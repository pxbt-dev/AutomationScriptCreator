package com.pxbtdev.utils;

import com.pxbtdev.model.dto.RecordingRequest;
import com.pxbtdev.model.dto.ScriptRequest;
import org.springframework.stereotype.Component;

@Component
public class Validator {

    public void validateRecordingRequest(RecordingRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Recording request cannot be null");
        }

        if (request.getUrl() == null || request.getUrl().trim().isEmpty()) {
            throw new IllegalArgumentException("URL is required");
        }

        if (request.getSessionName() == null || request.getSessionName().trim().isEmpty()) {
            throw new IllegalArgumentException("Session name is required");
        }

        // Validate URL format
        if (!isValidUrl(request.getUrl())) {
            throw new IllegalArgumentException("Invalid URL format: " + request.getUrl());
        }
    }

    public void validateScriptRequest(ScriptRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Script request cannot be null");
        }

        if (request.getSessionId() == null || request.getSessionId().trim().isEmpty()) {
            throw new IllegalArgumentException("Session ID is required");
        }
    }

    private boolean isValidUrl(String url) {
        try {
            new java.net.URL(url);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isValidSessionId(String sessionId) {
        return sessionId != null && sessionId.matches("[a-fA-F0-9\\-]{36}");
    }
}