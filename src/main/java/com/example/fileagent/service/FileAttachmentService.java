package com.example.fileagent.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.Base64;
import java.util.Set;

/**
 * Service for handling file attachments sent as Data URLs from the frontend.
 * Supports images (via vision model) and documents (PDF/Word text extraction).
 */
@Service
public class FileAttachmentService {

    private static final Logger log = LoggerFactory.getLogger(FileAttachmentService.class);

    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
            ".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp"
    );
    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            ".txt", ".md", ".csv", ".json", ".xml", ".html", ".css", ".js",
            ".java", ".py", ".ts", ".tsx", ".go", ".rs", ".c", ".cpp", ".h"
    );
    private static final Set<String> DOCUMENT_EXTENSIONS = Set.of(
            ".pdf", ".doc", ".docx"
    );
    private static final Set<String> BINARY_EXTENSIONS = Set.of(
            ".xls", ".xlsx", ".ppt", ".pptx"
    );

    /**
     * Result of processing a file attachment from a Data URL.
     */
    public record FileContent(String fileName, String mimeType, byte[] bytes, String extractedText) {
        public boolean hasImage() { return bytes != null && mimeType != null && mimeType.startsWith("image/"); }
        public boolean hasText() { return extractedText != null && !extractedText.isEmpty(); }
    }

    /**
     * Parse a Data URL and process the content.
     * Input format: "data:image/jpeg;base64,/9j/4AAQ..." or "data:application/pdf;base64,JVBERi0xLjQK..."
     * For text files, the dataUrl is actually plain text (readAsText result).
     */
    public FileContent processDataUrl(String dataUrl, String fileName) {
        try {
            // Check if it's plain text (readAsText result, no data: prefix)
            if (!dataUrl.startsWith("data:")) {
                log.info("读取纯文本内容: {}, 长度: {}", fileName, dataUrl.length());
                return new FileContent(fileName, "text/plain", null, dataUrl);
            }

            // Parse Data URL: "data:mimeType;base64,base64Data"
            int mimeEnd = dataUrl.indexOf(';');
            if (mimeEnd < 0) {
                return new FileContent(fileName, null, null, "无效的Data URL格式");
            }

            String mimeType = dataUrl.substring(5, mimeEnd); // Remove "data:" prefix
            int base64Start = dataUrl.indexOf(',', mimeEnd);
            if (base64Start < 0) {
                return new FileContent(fileName, null, null, "无效的Data URL格式");
            }

            String base64Data = dataUrl.substring(base64Start + 1);
            byte[] bytes = Base64.getDecoder().decode(base64Data);

            log.info("解析Data URL: {}, MIME: {}, 大小: {} bytes", fileName, mimeType, bytes.length);

            String ext = getFileExtension(fileName);

            // Handle images: return raw bytes for vision model
            if (IMAGE_EXTENSIONS.contains(ext) || mimeType.startsWith("image/")) {
                log.info("图片附件: {}, MIME: {}, 大小: {} bytes", fileName, mimeType, bytes.length);
                return new FileContent(fileName, mimeType, bytes, null);
            }

            // Handle documents: extract text from binary data
            if (DOCUMENT_EXTENSIONS.contains(ext)) {
                String text = extractDocumentFromBytes(bytes, ext);
                log.info("文档附件提取文本: {}, 长度: {}", fileName, text.length());
                return new FileContent(fileName, mimeType, null, text);
            }

            // Handle binary office docs (Excel, PPT): extract text
            if (BINARY_EXTENSIONS.contains(ext)) {
                String text = extractOfficeFromBytes(bytes, ext);
                log.info("Office文档附件提取文本: {}, 长度: {}", fileName, text.length());
                return new FileContent(fileName, mimeType, null, text);
            }

            // Unknown binary: try as text
            try {
                String text = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                return new FileContent(fileName, "text/plain", null, text);
            } catch (Exception e) {
                return new FileContent(fileName, mimeType, bytes, null);
            }

        } catch (Exception e) {
            log.error("处理文件失败: {}", fileName, e);
            return new FileContent(fileName, null, null, "处理文件失败: " + e.getMessage());
        }
    }

    /**
     * Extract text from PDF/Word document bytes.
     */
    private String extractDocumentFromBytes(byte[] bytes, String ext) throws Exception {
        try (InputStream is = new ByteArrayInputStream(bytes)) {
            return switch (ext) {
                case ".pdf" -> {
                    try (PDDocument doc = PDDocument.load(is)) {
                        PDFTextStripper stripper = new PDFTextStripper();
                        yield stripper.getText(doc);
                    }
                }
                case ".docx" -> {
                    StringBuilder sb = new StringBuilder();
                    try (XWPFDocument doc = new XWPFDocument(is)) {
                        for (XWPFParagraph p : doc.getParagraphs()) {
                            sb.append(p.getText()).append("\n");
                        }
                    }
                    yield sb.toString();
                }
                case ".doc" -> {
                    try (HWPFDocument doc = new HWPFDocument(is);
                         WordExtractor extractor = new WordExtractor(doc)) {
                        yield extractor.getText();
                    }
                }
                default -> new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            };
        }
    }

    /**
     * Extract text from Excel/PPT bytes.
     */
    private String extractOfficeFromBytes(byte[] bytes, String ext) {
        try {
            // Try as text first for simplicity
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "（二进制文件，无法提取文本）";
        }
    }

    /**
     * Get file extension from filename.
     */
    private String getFileExtension(String fileName) {
        if (fileName == null) return "";
        int dotIdx = fileName.lastIndexOf('.');
        if (dotIdx < 0) return "";
        return fileName.substring(dotIdx).toLowerCase();
    }

    /**
     * Check if a filename is an image.
     */
    public boolean isImage(String fileName) {
        return IMAGE_EXTENSIONS.contains(getFileExtension(fileName));
    }

    /**
     * Check if a filename is supported.
     */
    public boolean isSupported(String fileName) {
        String ext = getFileExtension(fileName);
        return IMAGE_EXTENSIONS.contains(ext) || TEXT_EXTENSIONS.contains(ext)
                || DOCUMENT_EXTENSIONS.contains(ext) || BINARY_EXTENSIONS.contains(ext);
    }
}
