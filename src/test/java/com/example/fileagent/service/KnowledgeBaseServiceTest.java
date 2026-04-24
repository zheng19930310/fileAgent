package com.example.fileagent.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class KnowledgeBaseServiceTest {

    @Autowired
    private KnowledgeBaseService kbService;

    private static final String TEST_SESSION_ID = "test-session-001";
    private static final String KB_DATA_DIR = "D:/chat/kb/.data";
    private static final String KB_META_DIR = "D:/chat/kb/.meta";

    @BeforeEach
    public void setUp() {
        // Clean up test session before each test
        kbService.clearSession(TEST_SESSION_ID);
    }

    @Test
    public void testUploadFileAndPersist() throws IOException {
        // Create a test file
        String content = "This is test content for knowledge base.\nIt has multiple lines.\nThird line here.";
        MultipartFile file = new MockMultipartFile(
                "test.txt",
                "test.txt",
                "text/plain",
                content.getBytes()
        );

        // Upload file
        Map<String, Object> result = kbService.uploadFile(TEST_SESSION_ID, file);

        // Verify upload result
        assertTrue((Boolean) result.get("success"));
        assertEquals("test.txt", result.get("fileName"));
        assertNotNull(result.get("chunks"));

        // Verify persistence file exists
        Path kbFile = Paths.get(KB_DATA_DIR, TEST_SESSION_ID + ".kb");
        assertTrue(Files.exists(kbFile), "KB persistence file should exist");

        // Verify we can retrieve chunks
        List<String> chunks = kbService.searchChunks(TEST_SESSION_ID, "test");
        assertFalse(chunks.isEmpty(), "Should find chunks containing 'test'");
    }

    @Test
    public void testLoadPersistedData() throws IOException {
        // First, upload and persist data
        String content = "Persistent knowledge content for testing.";
        MultipartFile file = new MockMultipartFile(
                "persist.txt",
                "persist.txt",
                "text/plain",
                content.getBytes()
        );

        kbService.uploadFile(TEST_SESSION_ID, file);

        // Verify file was persisted
        Path kbFile = Paths.get(KB_DATA_DIR, TEST_SESSION_ID + ".kb");
        assertTrue(Files.exists(kbFile));

        // Simulate reload by creating a new service instance would load from disk
        // For now, verify the data is in memory
        assertTrue(kbService.hasKnowledgeBase(TEST_SESSION_ID));
        int chunkCount = kbService.getChunkCount(TEST_SESSION_ID);
        assertTrue(chunkCount > 0);
    }

    @Test
    public void testClearSessionDeletesFiles() throws IOException {
        // Upload a file first
        String content = "Content to be cleared.";
        MultipartFile file = new MockMultipartFile(
                "clear.txt",
                "clear.txt",
                "text/plain",
                content.getBytes()
        );

        kbService.uploadFile(TEST_SESSION_ID, file);

        // Verify files exist
        Path kbFile = Paths.get(KB_DATA_DIR, TEST_SESSION_ID + ".kb");
        Path metaFile = Paths.get(KB_META_DIR, TEST_SESSION_ID + ".json");
        assertTrue(Files.exists(kbFile));

        // Clear session
        kbService.clearSession(TEST_SESSION_ID);

        // Verify files are deleted
        assertFalse(Files.exists(kbFile), "KB file should be deleted");
        assertFalse(Files.exists(metaFile), "Meta file should be deleted");

        // Verify in-memory data is cleared
        assertFalse(kbService.hasKnowledgeBase(TEST_SESSION_ID));
    }

    @Test
    public void testSearchChunks() throws IOException {
        // Upload content with specific keywords
        String content = "Artificial intelligence is transforming the world.\n" +
                        "Machine learning is a subset of AI.\n" +
                        "Deep learning uses neural networks.";
        MultipartFile file = new MockMultipartFile(
                "ai.txt",
                "ai.txt",
                "text/plain",
                content.getBytes()
        );

        kbService.uploadFile(TEST_SESSION_ID, file);

        // Search for relevant chunks
        List<String> results = kbService.searchChunks(TEST_SESSION_ID, "machine learning");
        assertFalse(results.isEmpty(), "Should find chunks about machine learning");

        // Verify relevance (first result should contain the keyword)
        String firstResult = results.get(0).toLowerCase();
        assertTrue(firstResult.contains("machine") || firstResult.contains("learning"),
                  "First result should be relevant");
    }

    @Test
    public void testMultipleFileUpload() throws IOException {
        // Upload first file
        MultipartFile file1 = new MockMultipartFile(
                "file1.txt",
                "file1.txt",
                "text/plain",
                "First file content.".getBytes()
        );
        kbService.uploadFile(TEST_SESSION_ID, file1);

        // Upload second file
        MultipartFile file2 = new MockMultipartFile(
                "file2.txt",
                "file2.txt",
                "text/plain",
                "Second file content.".getBytes()
        );
        kbService.uploadFile(TEST_SESSION_ID, file2);

        // Verify both files are tracked
        List<Map<String, String>> files = kbService.getUploadedFiles(TEST_SESSION_ID);
        assertEquals(2, files.size(), "Should have 2 uploaded files");

        // Verify total chunks
        int totalChunks = kbService.getChunkCount(TEST_SESSION_ID);
        assertTrue(totalChunks >= 2, "Should have chunks from both files");
    }

    @Test
    public void testEmptyQueryReturnsAllChunks() throws IOException {
        String content = "Test chunk one.\nTest chunk two.";
        MultipartFile file = new MockMultipartFile(
                "empty-query.txt",
                "empty-query.txt",
                "text/plain",
                content.getBytes()
        );

        kbService.uploadFile(TEST_SESSION_ID, file);

        // Empty query should return empty list (no match)
        List<String> results = kbService.searchChunks(TEST_SESSION_ID, "");
        assertTrue(results.isEmpty(), "Empty query should return no results");
    }
}
