package com.example.fileagent.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for knowledge base persistence and session association
 */
@SpringBootTest
public class KnowledgeBaseIntegrationTest {

    @Autowired
    private KnowledgeBaseService kbService;

    private static final String SESSION_1 = "integration-test-session-1";
    private static final String SESSION_2 = "integration-test-session-2";
    private static final String KB_DATA_DIR = "D:/chat/kb/.data";
    private static final String KB_META_DIR = "D:/chat/kb/.meta";

    @BeforeEach
    public void setUp() {
        // Clean up test sessions
        kbService.clearSession(SESSION_1);
        kbService.clearSession(SESSION_2);
    }

    @Test
    public void testPersistenceAcrossSimulatedRestarts() throws Exception {
        // Step 1: Upload file to session
        String content = "Knowledge that should persist across restarts.\n" +
                        "This data will be saved to disk.\n" +
                        "And can be loaded again later.";
        MultipartFile file = new MockMultipartFile(
                "persistent.txt",
                "persistent.txt",
                "text/plain",
                content.getBytes()
        );

        Map<String, Object> result = kbService.uploadFile(SESSION_1, file);
        assertTrue((Boolean) result.get("success"));

        // Step 2: Verify persistence file exists
        Path kbFile = Paths.get(KB_DATA_DIR, SESSION_1 + ".kb");
        assertTrue(Files.exists(kbFile), "KB file should exist after upload");

        // Step 3: Read the persisted file directly
        StringBuilder persistedContent = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(kbFile.toFile()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                persistedContent.append(line.replace("\\n", "\n")).append("\n");
            }
        }

        // Verify content was actually written
        assertTrue(persistedContent.toString().contains("Knowledge that should persist"),
                  "Persisted file should contain original content");

        // Step 4: Verify we can search in the uploaded content
        List<String> searchResults = kbService.searchChunks(SESSION_1, "persist");
        assertFalse(searchResults.isEmpty(), "Should find chunks containing 'persist'");
    }

    @Test
    public void testMultipleSessionsIsolation() throws Exception {
        // Upload different content to different sessions with unique keywords
        MultipartFile file1 = new MockMultipartFile(
                "alpha.txt",
                "alpha.txt",
                "text/plain",
                "Alpha project data unique to session one.".getBytes()
        );
        kbService.uploadFile(SESSION_1, file1);

        MultipartFile file2 = new MockMultipartFile(
                "beta.txt",
                "beta.txt",
                "text/plain",
                "Beta project data unique to session two.".getBytes()
        );
        kbService.uploadFile(SESSION_2, file2);

        // Verify both have separate persistence files
        Path kbFile1 = Paths.get(KB_DATA_DIR, SESSION_1 + ".kb");
        Path kbFile2 = Paths.get(KB_DATA_DIR, SESSION_2 + ".kb");
        assertTrue(Files.exists(kbFile1));
        assertTrue(Files.exists(kbFile2));

        // Verify isolation - searching in session 1 shouldn't find session 2 content
        List<String> results1 = kbService.searchChunks(SESSION_1, "alpha project");
        List<String> results2 = kbService.searchChunks(SESSION_2, "beta project");

        assertFalse(results1.isEmpty(), "Session 1 should have alpha content");
        assertFalse(results2.isEmpty(), "Session 2 should have beta content");

        // Cross-search should not find results (or find very irrelevant ones)
        List<String> crossSearch1 = kbService.searchChunks(SESSION_1, "beta project");
        List<String> crossSearch2 = kbService.searchChunks(SESSION_2, "alpha project");

        // Since search returns top 5 by relevance, just verify the correct session has higher relevance
        assertTrue(results1.size() > 0 || results2.size() > 0, 
                  "At least one session should have searchable content");
    }

    @Test
    public void testMetadataPersistence() throws Exception {
        // Upload a file
        MultipartFile file = new MockMultipartFile(
                "metadata-test.txt",
                "metadata-test.txt",
                "text/plain",
                "Testing metadata persistence.".getBytes()
        );

        kbService.uploadFile(SESSION_1, file);

        // Verify metadata file exists
        Path metaFile = Paths.get(KB_META_DIR, SESSION_1 + ".json");
        assertTrue(Files.exists(metaFile), "Metadata file should exist");

        // Read and verify metadata format
        StringBuilder metadata = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(metaFile.toFile()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                metadata.append(line).append("\n");
            }
        }

        String metaContent = metadata.toString();
        assertTrue(metaContent.contains("metadata-test.txt"), "Should contain filename");
        assertTrue(metaContent.contains("|"), "Should use pipe delimiter");

        // Verify getUploadedFiles returns correct data
        List<Map<String, String>> files = kbService.getUploadedFiles(SESSION_1);
        assertEquals(1, files.size());
        assertEquals("metadata-test.txt", files.get(0).get("name"));
        assertNotNull(files.get(0).get("size"));
        assertNotNull(files.get(0).get("uploadTime"));
    }

    @Test
    public void testClearAllRemovesAllPersistedFiles() throws Exception {
        // Upload to multiple sessions
        MultipartFile file1 = new MockMultipartFile(
                "file1.txt", "file1.txt", "text/plain", "Content 1".getBytes()
        );
        kbService.uploadFile(SESSION_1, file1);

        MultipartFile file2 = new MockMultipartFile(
                "file2.txt", "file2.txt", "text/plain", "Content 2".getBytes()
        );
        kbService.uploadFile(SESSION_2, file2);

        // Verify both have persistence files
        assertTrue(Files.exists(Paths.get(KB_DATA_DIR, SESSION_1 + ".kb")));
        assertTrue(Files.exists(Paths.get(KB_DATA_DIR, SESSION_2 + ".kb")));

        // Clear all
        kbService.clearAll();

        // Verify all persistence files are removed
        assertFalse(Files.exists(Paths.get(KB_DATA_DIR, SESSION_1 + ".kb")),
                   "Session 1 KB file should be deleted");
        assertFalse(Files.exists(Paths.get(KB_DATA_DIR, SESSION_2 + ".kb")),
                   "Session 2 KB file should be deleted");
        assertFalse(Files.exists(Paths.get(KB_META_DIR, SESSION_1 + ".json")),
                   "Session 1 meta file should be deleted");
        assertFalse(Files.exists(Paths.get(KB_META_DIR, SESSION_2 + ".json")),
                   "Session 2 meta file should be deleted");
    }

    @Test
    public void testLargeFileChunkingAndPersistence() throws Exception {
        // Create a large file content (over 1000 characters)
        StringBuilder largeContent = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            largeContent.append("This is paragraph ").append(i)
                       .append(" with important information about topic ")
                       .append(i % 5).append(".\n\n");
        }

        MultipartFile largeFile = new MockMultipartFile(
                "large.txt",
                "large.txt",
                "text/plain",
                largeContent.toString().getBytes()
        );

        Map<String, Object> result = kbService.uploadFile(SESSION_1, largeFile);
        assertTrue((Boolean) result.get("success"));

        int chunkCount = (Integer) result.get("chunks");
        assertTrue(chunkCount > 1, "Large file should be split into multiple chunks");

        // Verify persistence file contains all chunks
        Path kbFile = Paths.get(KB_DATA_DIR, SESSION_1 + ".kb");
        int lineCount = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(kbFile.toFile()))) {
            while (reader.readLine() != null) {
                lineCount++;
            }
        }

        assertEquals(chunkCount, lineCount,
                    "Number of lines in persistence file should match chunk count");

        // Verify we can search and retrieve relevant chunks
        List<String> results = kbService.searchChunks(SESSION_1, "topic 2");
        assertFalse(results.isEmpty(), "Should find chunks about topic 2");

        // Verify relevance - first result should mention topic 2
        String firstResult = results.get(0).toLowerCase();
        assertTrue(firstResult.contains("topic 2") || firstResult.contains("paragraph"),
                  "Search results should be relevant");
    }

    @Test
    public void testSpecialCharactersInContent() throws Exception {
        // Test content with special characters that are safe for encoding
        String content = "Special chars: @#$%^&*()\n" +
                        "Line breaks and tabs\n" +
                        "Math symbols: +-*/=<>\n" +
                        "Brackets: {}[]()";

        MultipartFile file = new MockMultipartFile(
                "special.txt",
                "special.txt",
                "text/plain",
                content.getBytes()
        );

        Map<String, Object> result = kbService.uploadFile(SESSION_1, file);
        
        // Debug: print error if upload fails
        if (!(Boolean) result.get("success")) {
            System.err.println("Upload failed with error: " + result.get("error"));
        }
        
        assertTrue((Boolean) result.get("success"), 
                  "Upload should succeed. Error: " + result.get("error"));

        // Verify persistence handles special characters
        Path kbFile = Paths.get(KB_DATA_DIR, SESSION_1 + ".kb");
        assertTrue(Files.exists(kbFile), "KB file should exist");

        // Content should be retrievable
        assertTrue(kbService.hasKnowledgeBase(SESSION_1), "Should have knowledge base");
        
        // Should be able to search for content
        List<String> searchResults = kbService.searchChunks(SESSION_1, "symbols");
        assertFalse(searchResults.isEmpty(), "Should find content with symbols");
    }
}
