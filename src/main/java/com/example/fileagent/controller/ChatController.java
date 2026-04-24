package com.example.fileagent.controller;

import com.example.fileagent.model.ChatRequest;
import com.example.fileagent.service.ChatHistoryService;
import com.example.fileagent.service.ChatHistoryService.ChatSessionInfo;
import com.example.fileagent.service.ChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")
public class ChatController {

    @Autowired
    private ChatService chatService;

    @Autowired
    private ChatHistoryService historyService;

    /**
     * Non-streaming chat with optional file attachments
     */
    @PostMapping
    public String chat(@RequestBody ChatRequest request) {
        String sessionId = request.getSessionId();
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = historyService.generateSessionId();
        }
        List<String> filePaths = request.getFilePaths();
        List<String> fileNames = request.getFileNames();
        if (filePaths != null && !filePaths.isEmpty()) {
            return chatService.chatWithFiles(request.getMessage(), filePaths, fileNames, sessionId);
        }
        return chatService.chat(request.getMessage(), sessionId);
    }

    /**
     * Streaming chat with optional file attachments
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(@RequestBody ChatRequest request) {
        String sessionId = request.getSessionId();
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = historyService.generateSessionId();
        }
        List<String> filePaths = request.getFilePaths();
        List<String> fileNames = request.getFileNames();
        if (filePaths != null && !filePaths.isEmpty()) {
            return chatService.streamChatWithFiles(request.getMessage(), filePaths, fileNames, sessionId);
        }
        return chatService.streamChat(request.getMessage(), sessionId);
    }

    // === 历史记录管理 API ===

    @GetMapping("/sessions")
    public List<ChatSessionInfo> listSessions() {
        return historyService.listSessions();
    }

    @GetMapping("/session/{sessionId}")
    public List<Map<String, String>> getSession(@PathVariable String sessionId) {
        return historyService.loadSessionRaw(sessionId);
    }

    @DeleteMapping("/session/{sessionId}")
    public String deleteSession(@PathVariable String sessionId) {
        boolean success = historyService.deleteSession(sessionId);
        chatService.clearSession(sessionId);
        return success ? "删除成功" : "删除失败";
    }

    @DeleteMapping("/sessions")
    public String clearAllSessions() {
        historyService.clearAll();
        return "所有历史记录已清除";
    }
}
