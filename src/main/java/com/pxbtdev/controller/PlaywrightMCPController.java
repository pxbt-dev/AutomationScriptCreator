package com.pxbtdev.controller;

import com.pxbtdev.component.BrowserDriver;
import com.pxbtdev.service.PlaywrightMCPService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/mcp")
public class PlaywrightMCPController {

    private final PlaywrightMCPService playwrightService;
    private final BrowserDriver browserDriver;

    public PlaywrightMCPController(PlaywrightMCPService playwrightService, BrowserDriver browserDriver) {
        this.playwrightService = playwrightService;
        this.browserDriver = browserDriver;
    }

    @PostMapping("/session/{sessionId}/screenshot")
    public ResponseEntity<Map<String, String>> captureScreenshot(
            @PathVariable String sessionId,
            @RequestParam(required = false) String selector) {

        try {
            String filename = browserDriver.captureElementScreenshot(sessionId, selector);
            if (filename != null) {
                return ResponseEntity.ok(Map.of(
                        "filename", filename,
                        "sessionId", sessionId,
                        "url", "/screenshots/" + filename
                ));
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/session/{sessionId}/element")
    public ResponseEntity<Map<String, Object>> inspectElement(
            @PathVariable String sessionId,
            @RequestParam String selector) {

        try {
            Map<String, Object> elementInfo = browserDriver.inspectElement(sessionId, selector);
            return ResponseEntity.ok(elementInfo);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/session/{sessionId}/interact")
    public ResponseEntity<Map<String, Object>> interactWithElement(
            @PathVariable String sessionId,
            @RequestBody Map<String, Object> interaction) {

        try {
            // This would call MCP to perform interactions
            // For now, return mock response
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "sessionId", sessionId,
                    "interaction", interaction.get("type"),
                    "timestamp", System.currentTimeMillis()
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }
}