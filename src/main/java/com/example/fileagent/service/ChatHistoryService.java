package com.example.fileagent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class ChatHistoryService {

    private static final Logger log = LoggerFactory.getLogger(ChatHistoryService.class);

    @Value("${chat.history.path:D:/chat}")
    private String historyPath;

    @PostConstruct
    public void init() {
        File dir = new File(historyPath);
        if (!dir.exists()) {
            dir.mkdirs();
            log.info("创建聊天记录目录: {}", historyPath);
        }
    }

    public void saveSession(String sessionId, List<Message> messages) {
        try {
            String fileName = sessionId + ".txt";
            Path filePath = Paths.get(historyPath, fileName);
            try (FileWriter writer = new FileWriter(filePath.toFile())) {
                for (Message msg : messages) {
                    String role;
                    if (msg instanceof UserMessage) role = "USER";
                    else if (msg instanceof AssistantMessage) role = "ASSISTANT";
                    else continue;
                    String content = msg.getText();
                    if (content != null && !content.isEmpty()) {
                        writer.write(role + "\t" + content.replace("\n", "\\n") + "\n");
                    }
                }
            }
            log.info("保存聊天记录: {}", fileName);
        } catch (IOException e) {
            log.error("保存失败: {}", sessionId, e);
        }
    }

    public List<Message> loadSession(String sessionId) {
        try {
            String fileName = sessionId + ".txt";
            Path filePath = Paths.get(historyPath, fileName);
            if (!Files.exists(filePath)) return new ArrayList<>();
            List<Message> messages = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new FileReader(filePath.toFile()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    int tabIdx = line.indexOf('\t');
                    if (tabIdx < 0) continue;
                    String role = line.substring(0, tabIdx);
                    String content = line.substring(tabIdx + 1).replace("\\n", "\n");
                    if ("USER".equals(role)) messages.add(new UserMessage(content));
                    else if ("ASSISTANT".equals(role)) messages.add(new AssistantMessage(content));
                }
            }
            log.info("加载历史记录: {}, {} 条消息", fileName, messages.size());
            return messages;
        } catch (IOException e) {
            log.error("加载失败: {}", sessionId, e);
            return new ArrayList<>();
        }
    }

    public List<Map<String, String>> loadSessionRaw(String sessionId) {
        List<Message> messages = loadSession(sessionId);
        return messages.stream().map(msg -> {
            Map<String, String> entry = new LinkedHashMap<>();
            entry.put("role", (msg instanceof UserMessage) ? "user" : "assistant");
            entry.put("content", msg.getText());
            return entry;
        }).collect(Collectors.toList());
    }

    public List<ChatSessionInfo> listSessions() {
        File dir = new File(historyPath);
        if (!dir.exists()) return new ArrayList<>();
        try (Stream<Path> paths = Files.walk(dir.toPath())) {
            return paths.filter(p -> p.toString().endsWith(".txt")).map(p -> {
                String sid = p.getFileName().toString().replace(".txt", "");
                File file = p.toFile();
                String display = sid;
                try {
                    Date d = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").parse(sid);
                    display = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(d);
                } catch (Exception ignored) {}
                return new ChatSessionInfo(sid, display, file.lastModified(), countLines(file));
            }).sorted(Comparator.comparingLong(ChatSessionInfo::getLastModified).reversed())
              .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("列出历史记录失败", e);
            return new ArrayList<>();
        }
    }

    public boolean deleteSession(String sessionId) {
        try {
            return Files.deleteIfExists(Paths.get(historyPath, sessionId + ".txt"));
        } catch (IOException e) {
            log.error("删除失败: {}", sessionId, e);
            return false;
        }
    }

    public void clearAll() {
        File dir = new File(historyPath);
        if (!dir.exists()) return;
        File[] files = dir.listFiles((d, n) -> n.endsWith(".txt"));
        if (files != null) for (File f : files) f.delete();
        log.info("已清除所有历史记录");
    }

    public String generateSessionId() {
        return new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
    }

    private int countLines(File file) {
        int c = 0;
        try (BufferedReader r = new BufferedReader(new FileReader(file))) {
            while (r.readLine() != null) c++;
        } catch (IOException ignored) {}
        return c;
    }

    public static class ChatSessionInfo {
        private final String sessionId, displayName;
        private final long lastModified;
        private final int messageCount;
        public ChatSessionInfo(String sid, String name, long mod, int count) {
            this.sessionId = sid; this.displayName = name; this.lastModified = mod; this.messageCount = count;
        }
        public String getSessionId() { return sessionId; }
        public String getDisplayName() { return displayName; }
        public long getLastModified() { return lastModified; }
        public int getMessageCount() { return messageCount; }
    }
}
