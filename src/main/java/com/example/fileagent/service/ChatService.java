package com.example.fileagent.service;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.example.fileagent.skill.FileTools;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.Media;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import reactor.core.publisher.Flux;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    @Autowired
    private ChatModel chatModel;

    @Autowired
    private FileTools fileTools;

    @Autowired
    private ChatHistoryService historyService;

    @Autowired
    private KnowledgeBaseService kbService;

    @Autowired
    private FileAttachmentService fileAttachmentService;

    @Value("${spring.ai.dashscope.chat.options.model:qwen-plus}")
    private String model;

    @Value("${spring.ai.dashscope.chat.options.vl-model:qwen-vl-plus}")
    private String vlModel;

    private final Map<String, List<Message>> sessionHistory = new ConcurrentHashMap<>();

    private static final Pattern TOOL_CALL_PATTERN = Pattern.compile("\\[TOOL_CALL:([^\\]]+)\\]");

    private static final String SYSTEM_PROMPT = """
            你是一个智能文件助手，可以帮助用户管理本地文件和回答问题。

            你拥有以下文件操作工具，当用户需要时会自动调用：
            1. listDisk - 查看所有磁盘驱动器及容量信息（无参数）
            2. listFiles - 列出目录内容（参数：目录路径）
            3. getFileSize - 获取文件/目录大小（参数：文件路径）
            4. readFile - 读取文件内容（参数：文件路径）
            5. createFile - 创建新文件（参数：文件路径|文件内容）
            6. editFile - 编辑文件内容（参数：文件路径|新内容）
            7. deleteFile - 删除文件/目录（参数：文件路径）

            使用规则：
            1. 当需要调用工具时，在回复中嵌入工具调用标记，格式：[TOOL_CALL:工具名|参数]
            2. 调用工具后，用自然语言向用户解释结果
            3. 路径格式使用正斜杠，如 D:/workspace/test.txt
            4. 如果问题不涉及文件操作，直接回答
            5. 如果用户发送了图片或文档，请仔细理解其中的内容并回答
            """;

    private DashScopeChatOptions buildOptions() {
        return DashScopeChatOptions.builder()
                .withModel(model)
                .build();
    }

    private DashScopeChatOptions buildVLOptions() {
        return DashScopeChatOptions.builder()
                .withModel(vlModel)
                .build();
    }

    public String chat(String message, String sessionId) {
        log.info("=== [chat] 用户输入: {} ===", message);
        List<Message> messages = getOrCreateSession(sessionId);
        messages.add(new UserMessage(message));
        String result = processWithTool(messages);
        saveSession(sessionId);
        return result;
    }

    public String chatWithFiles(String message, List<String> filePaths, List<String> fileNames, String sessionId) {
        log.info("=== [chatWithFiles] 用户输入: {}, 文件数: {} ===", message, filePaths != null ? filePaths.size() : 0);
        List<Message> messages = getOrCreateSession(sessionId);
        UserMessage userMsg = buildMultimodalMessage(message, filePaths, fileNames);
        messages.add(userMsg);
        DashScopeChatOptions options = buildVLOptions();
        Prompt prompt = new Prompt(messages, options);
        var response = chatModel.call(prompt);
        String reply = response.getResult().getOutput().getText();
        log.info("[chatWithFiles] AI回复: {}", reply);
        String toolResult = tryHandleToolCall(reply);
        if (toolResult != null) {
            messages.add(new AssistantMessage(reply));
            messages.add(new UserMessage("工具执行结果：" + toolResult +
                    "\n请根据上述结果，用自然语言回答用户的问题。"));
            Prompt secondPrompt = new Prompt(messages, buildOptions());
            var secondResponse = chatModel.call(secondPrompt);
            reply = secondResponse.getResult().getOutput().getText();
        }
        messages.add(new AssistantMessage(reply));
        saveSession(sessionId);
        return removeToolCallMarkers(reply);
    }

    public Flux<String> streamChat(String message, String sessionId) {
        return streamChatInternal(message, List.of(), List.of(), sessionId);
    }

    public Flux<String> streamChatWithFiles(String message, List<String> filePaths, List<String> fileNames, String sessionId) {
        return streamChatInternal(message, filePaths, fileNames, sessionId);
    }

    private Flux<String> streamChatInternal(String message, List<String> filePaths, List<String> fileNames, String sessionId) {
        log.info("=== [stream] 用户输入: {}, 文件数: {} ===", message, filePaths != null ? filePaths.size() : 0);
        List<Message> messages = getOrCreateSession(sessionId);
        String augmentedMessage = augmentWithKB(message, sessionId);

        UserMessage userMsg;
        DashScopeChatOptions options;

        if (filePaths != null && !filePaths.isEmpty()) {
            userMsg = buildMultimodalMessage(augmentedMessage, filePaths, fileNames);
            options = buildVLOptions();
        } else {
            userMsg = new UserMessage(augmentedMessage);
            options = buildOptions();
        }

        messages.add(userMsg);

        Prompt prompt = new Prompt(messages, options);
        var response = chatModel.call(prompt);
        String firstReply = response.getResult().getOutput().getText();
        log.info("[stream] AI首轮回复: {}", firstReply);

        String toolResult = tryHandleToolCall(firstReply);

        if (toolResult != null) {
            log.info("[stream] 检测到工具调用，执行结果长度: {}", toolResult.length());
            messages.add(new AssistantMessage(firstReply));
            messages.add(new UserMessage("工具执行结果：" + toolResult +
                    "\n请根据上述结果，用自然语言回答用户的问题。不要输出任何标记。"));
            Prompt secondPrompt = new Prompt(messages, buildOptions());
            return chatModel.stream(secondPrompt)
                    .filter(r -> r != null && r.getResult() != null && r.getResult().getOutput() != null)
                    .map(r -> r.getResult().getOutput().getText())
                    .filter(t -> t != null && !t.isEmpty())
                    .doOnComplete(() -> saveSession(sessionId));
        } else {
            String cleanReply = removeToolCallMarkers(firstReply);
            log.info("[stream] 无需工具调用，直接流式输出");
            messages.add(new AssistantMessage(cleanReply));
            saveSession(sessionId);
            Prompt streamPrompt = new Prompt(messages, options);
            return chatModel.stream(streamPrompt)
                    .filter(r -> r != null && r.getResult() != null && r.getResult().getOutput() != null)
                    .map(r -> r.getResult().getOutput().getText())
                    .filter(t -> t != null && !t.isEmpty());
        }
    }

    /**
     * Build a multimodal UserMessage from text + Data URLs or local file paths.
     * filePaths can be either:
     * - Data URLs from the frontend (e.g. "data:application/pdf;base64,...")
     * - Local file paths (e.g. "D:/workspace/test.txt")
     * fileNames are the original filenames for determining file type.
     */
    private UserMessage buildMultimodalMessage(String text, List<String> filePaths, List<String> fileNames) {
        StringBuilder textBuilder = new StringBuilder();
        if (text != null && !text.isEmpty()) {
            textBuilder.append(text);
        }

        List<Media> mediaList = new ArrayList<>();
        int count = filePaths != null ? filePaths.size() : 0;

        for (int i = 0; i < count; i++) {
            String filePath = filePaths.get(i);
            String fileName = (fileNames != null && i < fileNames.size()) ? fileNames.get(i) : "unknown_file";

            FileAttachmentService.FileContent fc;
            // Check if it's a local file path or Data URL
            if (filePath.startsWith("data:")) {
                // Data URL format
                fc = fileAttachmentService.processDataUrl(filePath, fileName);
            } else {
                // Local file path
                fc = processLocalFile(filePath, fileName);
            }

            if (fc.hasImage()) {
                // Image: send as Media for vision model
                try {
                    MimeType mimeType = MimeType.valueOf(fc.mimeType());
                    Media media = new Media(mimeType, new InputStreamResource(new ByteArrayInputStream(fc.bytes())));
                    mediaList.add(media);
                    log.info("[Multimodal] 添加图片: {}, MIME: {}, {} bytes", fc.fileName(), fc.mimeType(), fc.bytes().length);
                } catch (Exception e) {
                    log.error("[Multimodal] 图片处理失败: {}", fileName, e);
                    textBuilder.append("\n（图片加载失败：").append(e.getMessage()).append("）");
                }
            } else if (fc.hasText()) {
                // Document/text: embed extracted text
                textBuilder.append("\n\n【文件内容: ").append(fc.fileName()).append("】\n");
                textBuilder.append(fc.extractedText());
                log.info("[Multimodal] 添加文档文本: {}, {} 字符", fc.fileName(), fc.extractedText().length());
            } else {
                // Could not read
                textBuilder.append("\n（文件读取失败: ").append(fc.extractedText()).append("）");
            }
        }

        if (mediaList.isEmpty()) {
            return new UserMessage(textBuilder.toString());
        } else {
            return new UserMessage(textBuilder.toString(), mediaList.toArray(new Media[0]));
        }
    }

    /**
     * Process a local file path and extract content
     */
    private FileAttachmentService.FileContent processLocalFile(String filePath, String fileName) {
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                return new FileAttachmentService.FileContent(fileName, null, null, "文件不存在: " + filePath);
            }

            String ext = getFileExtension(fileName).toLowerCase();
            
            // Text files: read directly
            if (FileAttachmentService.TEXT_EXTENSIONS.contains(ext) || ext.equals(".txt")) {
                String content = new String(java.nio.file.Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8);
                log.info("[LocalFile] 读取文本文件: {}, 长度: {}", fileName, content.length());
                return new FileAttachmentService.FileContent(fileName, "text/plain", null, content);
            }
            
            // Image files: read as bytes
            if (FileAttachmentService.IMAGE_EXTENSIONS.contains(ext)) {
                byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
                String mimeType = getMimeType(ext);
                log.info("[LocalFile] 读取图片文件: {}, MIME: {}, 大小: {} bytes", fileName, mimeType, bytes.length);
                return new FileAttachmentService.FileContent(fileName, mimeType, bytes, null);
            }
            
            // Document files: extract text
            if (FileAttachmentService.DOCUMENT_EXTENSIONS.contains(ext)) {
                try (InputStream is = new FileInputStream(file)) {
                    String text = extractDocumentFromBytes(java.nio.file.Files.readAllBytes(file.toPath()), ext);
                    log.info("[LocalFile] 提取文档文本: {}, 长度: {}", fileName, text.length());
                    return new FileAttachmentService.FileContent(fileName, "application/octet-stream", null, text);
                }
            }
            
            // Fallback: try as text
            try {
                String content = new String(java.nio.file.Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8);
                return new FileAttachmentService.FileContent(fileName, "text/plain", null, content);
            } catch (Exception e) {
                return new FileAttachmentService.FileContent(fileName, null, null, "不支持的文件格式: " + ext);
            }
            
        } catch (Exception e) {
            log.error("[LocalFile] 处理本地文件失败: {}", filePath, e);
            return new FileAttachmentService.FileContent(fileName, null, null, "处理文件失败: " + e.getMessage());
        }
    }

    private String getFileExtension(String fileName) {
        if (fileName == null) return "";
        int dotIdx = fileName.lastIndexOf('.');
        if (dotIdx < 0) return "";
        return fileName.substring(dotIdx).toLowerCase();
    }

    private String getMimeType(String ext) {
        return switch (ext) {
            case ".jpg", ".jpeg" -> "image/jpeg";
            case ".png" -> "image/png";
            case ".gif" -> "image/gif";
            case ".bmp" -> "image/bmp";
            case ".webp" -> "image/webp";
            default -> "application/octet-stream";
        };
    }

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

    private String processWithTool(List<Message> messages) {
        Prompt prompt = new Prompt(messages, buildOptions());
        var response = chatModel.call(prompt);
        String reply = response.getResult().getOutput().getText();
        log.info("[processWithTool] AI回复: {}", reply);
        String toolResult = tryHandleToolCall(reply);
        if (toolResult != null) {
            log.info("[processWithTool] 工具执行结果(前100字): {}",
                    toolResult.substring(0, Math.min(100, toolResult.length())));
            messages.add(new AssistantMessage(reply));
            messages.add(new UserMessage("工具执行结果：" + toolResult +
                    "\n请根据上述结果，用自然语言回答用户的问题。"));
            Prompt secondPrompt = new Prompt(messages, buildOptions());
            var secondResponse = chatModel.call(secondPrompt);
            reply = secondResponse.getResult().getOutput().getText();
        }
        messages.add(new AssistantMessage(reply));
        return removeToolCallMarkers(reply);
    }

    private String removeToolCallMarkers(String text) {
        if (text == null) return "";
        String cleaned = TOOL_CALL_PATTERN.matcher(text).replaceAll("").trim();
        return cleaned.replaceAll("\n{3,}", "\n\n");
    }

    private String tryHandleToolCall(String reply) {
        Matcher matcher = TOOL_CALL_PATTERN.matcher(reply);
        if (!matcher.find()) {
            log.debug("[ToolCall] 未检测到工具调用标记");
            return null;
        }
        String inner = matcher.group(1);
        String[] parts = inner.split("\\|", 2);
        String toolName = parts[0].trim();
        String args = parts.length > 1 ? parts[1].trim() : "";
        log.info("[ToolCall] 执行: name={}, args=[{}]", toolName, args);
        return switch (toolName) {
            case "listDisk" -> fileTools.listDisk(args);
            case "listFiles" -> { log.info("[ToolCall] -> listFiles({})", args); yield fileTools.listFiles(args); }
            case "getFileSize" -> { log.info("[ToolCall] -> getFileSize({})", args); yield fileTools.getFileSize(args); }
            case "readFile" -> {
                String[] rParts = args.split("\\|", 2);
                String path = rParts[0].trim();
                log.info("[ToolCall] -> readFile({})", path);
                if (rParts.length > 1 && !rParts[1].trim().isEmpty()) {
                    yield fileTools.readFile(path, Integer.parseInt(rParts[1].trim()));
                }
                yield fileTools.readFile(path, null);
            }
            case "createFile" -> {
                String[] cParts = args.split("\\|", 2);
                log.info("[ToolCall] -> createFile({})", cParts[0]);
                yield fileTools.createFile(cParts[0].trim(), cParts.length > 1 ? cParts[1] : "");
            }
            case "editFile" -> {
                String[] eParts = args.split("\\|", 2);
                log.info("[ToolCall] -> editFile({})", eParts[0]);
                yield fileTools.editFile(eParts[0].trim(), eParts.length > 1 ? eParts[1] : "");
            }
            case "deleteFile" -> { log.info("[ToolCall] -> deleteFile({})", args); yield fileTools.deleteFile(args); }
            default -> { log.warn("[ToolCall] 未知工具: {}", toolName); yield "未知工具: " + toolName; }
        };
    }

    private List<Message> getOrCreateSession(String sessionId) {
        return sessionHistory.computeIfAbsent(sessionId, k -> {
            List<Message> history = new ArrayList<>();
            history.add(new SystemMessage(SYSTEM_PROMPT));
            List<Message> saved = historyService.loadSession(sessionId);
            if (!saved.isEmpty()) {
                history.addAll(saved);
                log.info("[Session] 从文件加载 {} 条历史消息: {}", saved.size(), sessionId);
            }
            return history;
        });
    }

    private void saveSession(String sessionId) {
        List<Message> messages = sessionHistory.get(sessionId);
        if (messages != null) {
            historyService.saveSession(sessionId, messages);
        }
    }

    public void clearSession(String sessionId) {
        sessionHistory.remove(sessionId);
    }

    private String augmentWithKB(String message, String sessionId) {
        if (!kbService.hasKnowledgeBase(sessionId)) {
            return message;
        }
        List<String> relevantChunks = kbService.searchChunks(sessionId, message);
        if (relevantChunks.isEmpty()) {
            return message;
        }
        StringBuilder context = new StringBuilder();
        context.append("【参考文档内容】\n");
        for (int i = 0; i < relevantChunks.size(); i++) {
            context.append("--- 片段 ").append(i + 1).append(" ---\n");
            context.append(relevantChunks.get(i)).append("\n");
        }
        context.append("\n【用户问题】\n").append(message);
        context.append("\n\n请优先基于上述参考文档内容回答问题。如果文档中没有相关信息，再根据你的知识回答，并说明文档中未提及。");
        log.info("[RAG] 检索到 {} 个相关片段, sessionId={}", relevantChunks.size(), sessionId);
        return context.toString();
    }
}
