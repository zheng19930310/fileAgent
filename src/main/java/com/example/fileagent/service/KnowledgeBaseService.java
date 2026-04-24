package com.example.fileagent.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbookFactory;
import org.apache.poi.ooxml.POIXMLException;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.sl.usermodel.SlideShow;
import org.apache.poi.sl.usermodel.SlideShowFactory;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class KnowledgeBaseService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseService.class);

    private static final String KB_BASE_PATH = "D:/chat/kb";
    private static final String KB_DATA_DIR = KB_BASE_PATH + "/.data";
    private static final String KB_FILES_DIR = KB_BASE_PATH + "/.meta";

    /**
     * Each sessionId -> its knowledge base chunks.
     * A chunk is a text segment from the uploaded document.
     */
    private final Map<String, List<String>> sessionKB = new ConcurrentHashMap<>();

    /**
     * Metadata about uploaded files per session
     * sessionId -> list of file info maps
     */
    private final Map<String, List<Map<String, String>>> sessionFiles = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        File kbDir = new File(KB_BASE_PATH);
        if (!kbDir.exists()) {
            kbDir.mkdirs();
        }
        File dataDir = new File(KB_DATA_DIR);
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
        File metaDir = new File(KB_FILES_DIR);
        if (!metaDir.exists()) {
            metaDir.mkdirs();
        }
        log.info("知识库目录初始化完成: {}", KB_BASE_PATH);
        // 加载持久化的知识库数据
        loadAllSessions();
    }

    /**
     * Load all persisted session KB data from disk
     */
    private void loadAllSessions() {
        File dataDir = new File(KB_DATA_DIR);
        if (!dataDir.exists()) return;

        File[] sessionFiles = dataDir.listFiles((dir, name) -> name.endsWith(".kb"));
        if (sessionFiles == null) return;

        for (File f : sessionFiles) {
            try {
                String sessionId = f.getName().replace(".kb", "");
                List<String> chunks = new ArrayList<>();
                try (BufferedReader reader = new BufferedReader(new FileReader(f))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        chunks.add(line.replace("\\n", "\n").replace("\\r", "\r"));
                    }
                }
                sessionKB.put(sessionId, chunks);
                
                // Also load file metadata for this session
                loadSessionFiles(sessionId);
                
                log.info("加载知识库: sessionId={}, chunks={}", sessionId, chunks.size());
            } catch (IOException e) {
                log.error("加载知识库失败: {}", f.getName(), e);
            }
        }
        log.info("共加载 {} 个会话的知识库", sessionKB.size());
    }

    /**
     * Persist session KB chunks to disk
     */
    private void saveSessionKB(String sessionId) {
        try {
            Path filePath = Paths.get(KB_DATA_DIR, sessionId + ".kb");
            List<String> chunks = sessionKB.get(sessionId);
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath.toFile()))) {
                if (chunks != null) {
                    for (String chunk : chunks) {
                        writer.write(chunk.replace("\n", "\\n").replace("\r", "\\r"));
                        writer.newLine();
                    }
                }
            }
            log.debug("持久化知识库: sessionId={}, chunks={}", sessionId,
                    chunks != null ? chunks.size() : 0);
        } catch (IOException e) {
            log.error("持久化知识库失败: {}", sessionId, e);
        }
    }

    /**
     * Persist session files metadata to disk
     */
    private void saveSessionFiles(String sessionId) {
        try {
            Path filePath = Paths.get(KB_FILES_DIR, sessionId + ".json");
            List<Map<String, String>> files = sessionFiles.get(sessionId);
            // Simple JSON-like format: name|size|chunks|time per line
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath.toFile()))) {
                if (files != null) {
                    for (Map<String, String> f : files) {
                        writer.write(f.getOrDefault("name", ""));
                        writer.write("|");
                        writer.write(f.getOrDefault("size", ""));
                        writer.write("|");
                        writer.write(f.getOrDefault("chunks", ""));
                        writer.write("|");
                        writer.write(f.getOrDefault("uploadTime", ""));
                        writer.newLine();
                    }
                }
            }
        } catch (IOException e) {
            log.error("持久化文件元数据失败: {}", sessionId, e);
        }
    }

    /**
     * Load session files metadata from disk
     */
    private void loadSessionFiles(String sessionId) {
        try {
            Path filePath = Paths.get(KB_FILES_DIR, sessionId + ".json");
            if (!Files.exists(filePath)) return;

            List<Map<String, String>> files = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new FileReader(filePath.toFile()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split("\\|", 4);
                    if (parts.length == 4) {
                        Map<String, String> fileInfo = new HashMap<>();
                        fileInfo.put("name", parts[0]);
                        fileInfo.put("size", parts[1]);
                        fileInfo.put("chunks", parts[2]);
                        fileInfo.put("uploadTime", parts[3]);
                        files.add(fileInfo);
                    }
                }
            }
            sessionFiles.put(sessionId, files);
        } catch (IOException e) {
            log.error("加载文件元数据失败: {}", sessionId, e);
        }
    }

    /**
     * Upload and parse a file into knowledge base for a session
     */
    public Map<String, Object> uploadFile(String sessionId, MultipartFile file) {
        Map<String, Object> result = new HashMap<>();

        try {
            String originalName = file.getOriginalFilename();
            if (originalName == null || originalName.isEmpty()) {
                result.put("success", false);
                result.put("error", "文件名为空");
                return result;
            }

            String ext = originalName.substring(originalName.lastIndexOf('.')).toLowerCase();
            if (!isValidExt(ext)) {
                result.put("success", false);
                result.put("error", "不支持的文件格式: " + ext);
                return result;
            }

            // Save file to disk first
            String safeName = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS").format(new Date()) + "_" + originalName;
            Path filePath = Paths.get(KB_BASE_PATH, safeName);
            Files.createDirectories(filePath.getParent());
            try (var is = file.getInputStream()) {
                Files.copy(is, filePath);
            }

            // Parse file content from the saved file
            String content = extractTextFromFile(filePath, ext);
            if (content == null || content.trim().isEmpty()) {
                result.put("success", false);
                result.put("error", "文件内容为空或无法解析");
                return result;
            }

            // Split into chunks (500 chars each with overlap)
            List<String> chunks = splitIntoChunks(content, 500, 100);

            // Store chunks
            List<String> existingChunks = sessionKB.computeIfAbsent(sessionId, k -> new ArrayList<>());
            existingChunks.addAll(chunks);

            // Store file metadata
            List<Map<String, String>> files = sessionFiles.computeIfAbsent(sessionId, k -> new ArrayList<>());
            Map<String, String> fileInfo = new HashMap<>();
            fileInfo.put("name", originalName);
            fileInfo.put("size", String.valueOf(file.getSize()));
            fileInfo.put("chunks", String.valueOf(chunks.size()));
            fileInfo.put("uploadTime", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
            files.add(fileInfo);

            // Persist to disk
            saveSessionKB(sessionId);
            saveSessionFiles(sessionId);

            log.info("上传知识库文件: sessionId={}, fileName={}, chunks={}", sessionId, originalName, chunks.size());

            result.put("success", true);
            result.put("fileName", originalName);
            result.put("chunks", chunks.size());
            result.put("contentPreview", content.substring(0, Math.min(200, content.length())));
            return result;

        } catch (Exception e) {
            log.error("上传知识库文件失败", e);
            result.put("success", false);
            result.put("error", e.getMessage());
            return result;
        }
    }

    /**
     * Get knowledge base chunks for a session (with keyword search)
     */
    public List<String> searchChunks(String sessionId, String query) {
        List<String> chunks = sessionKB.get(sessionId);
        if (chunks == null || chunks.isEmpty()) {
            return new ArrayList<>();
        }

        // Simple keyword matching: return chunks that contain any word from the query
        String[] keywords = query.toLowerCase().split("\\s+");
        List<ScoredChunk> scored = new ArrayList<>();

        for (String chunk : chunks) {
            String lowerChunk = chunk.toLowerCase();
            int score = 0;
            for (String kw : keywords) {
                if (kw.length() > 1 && lowerChunk.contains(kw)) {
                    score++;
                }
            }
            if (score > 0) {
                scored.add(new ScoredChunk(chunk, score));
            }
        }

        // Sort by relevance score, return top chunks
        return scored.stream()
                .sorted(Comparator.comparingInt(ScoredChunk::score).reversed())
                .limit(5)
                .map(ScoredChunk::chunk)
                .collect(Collectors.toList());
    }

    /**
     * Get uploaded files for a session
     */
    public List<Map<String, String>> getUploadedFiles(String sessionId) {
        return sessionFiles.getOrDefault(sessionId, new ArrayList<>());
    }

    /**
     * Clear knowledge base for a session
     */
    public void clearSession(String sessionId) {
        sessionKB.remove(sessionId);
        sessionFiles.remove(sessionId);

        // Delete persisted files
        try {
            Path kbFile = Paths.get(KB_DATA_DIR, sessionId + ".kb");
            Path metaFile = Paths.get(KB_FILES_DIR, sessionId + ".json");
            if (Files.exists(kbFile)) Files.delete(kbFile);
            if (Files.exists(metaFile)) Files.delete(metaFile);
            log.debug("删除持久化文件: sessionId={}", sessionId);
        } catch (IOException e) {
            log.error("删除持久化文件失败: {}", sessionId, e);
        }

        log.info("清除知识库: {}", sessionId);
    }

    /**
     * Clear all knowledge bases
     */
    public void clearAll() {
        sessionKB.clear();
        sessionFiles.clear();
        
        // Delete all persisted files
        try {
            // Delete KB data files
            File dataDir = new File(KB_DATA_DIR);
            if (dataDir.exists()) {
                File[] kbFiles = dataDir.listFiles();
                if (kbFiles != null) {
                    for (File f : kbFiles) {
                        if (f.getName().endsWith(".kb")) f.delete();
                    }
                }
            }
            
            // Delete metadata files
            File metaDir = new File(KB_FILES_DIR);
            if (metaDir.exists()) {
                File[] metaFiles = metaDir.listFiles();
                if (metaFiles != null) {
                    for (File f : metaFiles) {
                        if (f.getName().endsWith(".json")) f.delete();
                    }
                }
            }
            
            log.debug("删除所有持久化文件");
        } catch (Exception e) {
            log.error("删除持久化文件失败", e);
        }
        
        log.info("清除所有知识库");
    }

    /**
     * Check if session has knowledge base
     */
    public boolean hasKnowledgeBase(String sessionId) {
        List<String> chunks = sessionKB.get(sessionId);
        return chunks != null && !chunks.isEmpty();
    }

    /**
     * Get total chunk count for a session
     */
    public int getChunkCount(String sessionId) {
        List<String> chunks = sessionKB.get(sessionId);
        return chunks == null ? 0 : chunks.size();
    }

    // === File Extraction ===

    private String extractTextFromFile(Path filePath, String ext) throws Exception {
        try (InputStream is = Files.newInputStream(filePath)) {
            return switch (ext) {
                case ".txt" -> Files.readString(filePath);
                case ".docx" -> extractDocx(is);
                case ".doc" -> extractDoc(is);
                case ".pdf" -> extractPdf(is);
                case ".pptx" -> extractPptx(is);
                case ".ppt" -> extractPpt(is);
                case ".xlsx" -> extractXlsx(is);
                case ".xls" -> extractXls(is);
                default -> throw new IllegalArgumentException("不支持的格式: " + ext);
            };
        }
    }

    private String extractDocx(InputStream is) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(is)) {
            StringBuilder sb = new StringBuilder();
            for (XWPFParagraph p : doc.getParagraphs()) {
                sb.append(p.getText()).append("\n");
            }
            return sb.toString();
        }
    }

    private String extractDoc(InputStream is) throws Exception {
        try (HWPFDocument doc = new HWPFDocument(is);
             WordExtractor extractor = new WordExtractor(doc)) {
            return extractor.getText();
        }
    }

    private String extractPdf(InputStream is) throws Exception {
        try (PDDocument doc = PDDocument.load(is)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(doc);
        }
    }

    private String extractPptx(InputStream is) throws Exception {
        try (SlideShow<?, ?> ppt = SlideShowFactory.create(is)) {
            StringBuilder sb = new StringBuilder();
            for (var slide : ppt.getSlides()) {
                for (var shape : slide.getShapes()) {
                    if (shape instanceof org.apache.poi.sl.usermodel.TextShape<?,?> ts) {
                        sb.append(ts.getText()).append("\n");
                    }
                }
            }
            return sb.toString();
        }
    }

    private String extractPpt(InputStream is) throws Exception {
        try (SlideShow<?, ?> ppt = SlideShowFactory.create(is)) {
            StringBuilder sb = new StringBuilder();
            for (var slide : ppt.getSlides()) {
                for (var shape : slide.getShapes()) {
                    if (shape instanceof org.apache.poi.sl.usermodel.TextShape<?,?> ts) {
                        sb.append(ts.getText()).append("\n");
                    }
                }
            }
            return sb.toString();
        }
    }

    private String extractXlsx(InputStream is) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(is)) {
            StringBuilder sb = new StringBuilder();
            for (var sheet : wb) {
                sb.append("Sheet: ").append(sheet.getSheetName()).append("\n");
                for (var row : sheet) {
                    for (var cell : row) {
                        sb.append(cell.toString()).append("\t");
                    }
                    sb.append("\n");
                }
            }
            return sb.toString();
        }
    }

    private String extractXls(InputStream is) throws Exception {
        try (HSSFWorkbook wb = new HSSFWorkbook(is)) {
            StringBuilder sb = new StringBuilder();
            for (var sheet : wb) {
                sb.append("Sheet: ").append(sheet.getSheetName()).append("\n");
                for (var row : sheet) {
                    for (var cell : row) {
                        sb.append(cell.toString()).append("\t");
                    }
                    sb.append("\n");
                }
            }
            return sb.toString();
        }
    }

    private boolean isValidExt(String ext) {
        return Set.of(".txt", ".doc", ".docx", ".pdf", ".ppt", ".pptx", ".xls", ".xlsx").contains(ext);
    }

    private List<String> splitIntoChunks(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isEmpty()) return chunks;

        // Split by paragraphs first
        String[] paragraphs = text.split("\n\n+");
        StringBuilder currentChunk = new StringBuilder();

        for (String para : paragraphs) {
            String trimmed = para.trim();
            if (trimmed.isEmpty()) continue;

            if (currentChunk.length() + trimmed.length() > chunkSize) {
                // Save current chunk
                if (currentChunk.length() > 0) {
                    chunks.add(currentChunk.toString().trim());
                    // Keep overlap
                    String overlapText = currentChunk.toString();
                    int overlapStart = Math.max(0, overlapText.length() - overlap);
                    currentChunk = new StringBuilder(overlapText.substring(overlapStart));
                }
            }
            currentChunk.append(trimmed).append("\n");
        }

        // Add remaining
        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString().trim());
        }

        // If no paragraphs, split by characters
        if (chunks.isEmpty() && text.length() > chunkSize) {
            int start = 0;
            while (start < text.length()) {
                int end = Math.min(start + chunkSize, text.length());
                chunks.add(text.substring(start, end).trim());
                start = end - overlap;
            }
        } else if (chunks.isEmpty()) {
            chunks.add(text.trim());
        }

        log.debug("分块完成: {} 块", chunks.size());
        return chunks;
    }

    private record ScoredChunk(String chunk, int score) {}
}
