package com.example.fileagent.model;

import lombok.Data;
import java.util.List;

@Data
public class ChatRequest {
    private String message;
    private String sessionId;

    /**
     * List of attached files for multimodal requests.
     * Each item is a Data URL string, e.g.:
     * - "data:image/jpeg;base64,/9j/4AAQ..."
     * - "data:application/pdf;base64,JVBERi0xLjQK..."
     * - Plain text (for .txt/.md etc files read as text)
     */
    private List<String> filePaths;

    /**
     * Corresponding filenames for each item in filePaths.
     * Needed to determine file type for processing.
     */
    private List<String> fileNames;
}
