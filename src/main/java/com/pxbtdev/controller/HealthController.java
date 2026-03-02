package com.pxbtdev.controller;

import com.pxbtdev.service.OllamaService;
import com.pxbtdev.service.PlaywrightRunnerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class HealthController {

    private final OllamaService ollamaService;
    private final PlaywrightRunnerService playwrightRunnerService;

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Runtime rt = Runtime.getRuntime();
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "AutomationScriptCreator",
                "version", "2.0.0",
                "timestamp", System.currentTimeMillis(),
                "memory", Map.of(
                        "used", (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024 + "MB",
                        "total", rt.totalMemory() / 1024 / 1024 + "MB",
                        "max", rt.maxMemory() / 1024 / 1024 + "MB"),
                "ai", Map.of(
                        "available", ollamaService.isAvailable(),
                        "model", ollamaService.getModel()),
                "playwright", playwrightRunnerService.getStatus()));
    }

    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> info() {
        return ResponseEntity.ok(Map.of(
                "name", "AutomationScriptCreator",
                "description", "One-stop AI-powered testing platform",
                "version", "2.0.0",
                "features",
                new String[] { "Swagger/API Testing", "Website Testing", "Playwright Automation", "AI Test Agents" },
                "javaVersion", System.getProperty("java.version"),
                "author", "pxbt-dev"));
    }
}
