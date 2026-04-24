package com.example.fileagent.controller;

import com.example.fileagent.service.KnowledgeBaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/kb")
@CrossOrigin(origins = "*")
public class KnowledgeBaseController {

    @Autowired
    private KnowledgeBaseService kbService;

    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("sessionId") String sessionId) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "文件为空"));
        }
        if (sessionId == null || sessionId.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "sessionId 不能为空"));
        }
        return ResponseEntity.ok(kbService.uploadFile(sessionId, file));
    }

    @GetMapping("/files")
    public ResponseEntity<?> getUploadedFiles(@RequestParam("sessionId") String sessionId) {
        return ResponseEntity.ok(kbService.getUploadedFiles(sessionId));
    }

    @GetMapping("/stats")
    public ResponseEntity<?> getStats(@RequestParam("sessionId") String sessionId) {
        return ResponseEntity.ok(Map.of(
                "hasKB", kbService.hasKnowledgeBase(sessionId),
                "chunkCount", kbService.getChunkCount(sessionId)
        ));
    }

    @DeleteMapping("/session/{sessionId}")
    public ResponseEntity<?> clearSession(@PathVariable String sessionId) {
        kbService.clearSession(sessionId);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @DeleteMapping("/all")
    public ResponseEntity<?> clearAll() {
        kbService.clearAll();
        return ResponseEntity.ok(Map.of("success", true));
    }
}
