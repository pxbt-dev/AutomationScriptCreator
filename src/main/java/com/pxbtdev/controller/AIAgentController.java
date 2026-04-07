package com.pxbtdev.controller;

import com.pxbtdev.service.AIAgentService;
import com.pxbtdev.service.OllamaService;
import com.pxbtdev.model.entity.TestCase;
import com.pxbtdev.util.UrlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/agent")
@RequiredArgsConstructor
public class AIAgentController {

    private final AIAgentService agentService;
    private final OllamaService ollamaService;

    /** Get AI agent and Ollama status */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(agentService.getStatus());
    }

    /** Get list of available Ollama models */
    @GetMapping("/models")
    public ResponseEntity<Map<String, Object>> models() {
        return ResponseEntity.ok(Map.of(
                "available", ollamaService.isAvailable(),
                "currentModel", ollamaService.getModel(),
                "models", ollamaService.getAvailableModels()));
    }

    /** Enhance a list of test cases with AI */
    @PostMapping("/enhance")
    public ResponseEntity<Map<String, Object>> enhance(@RequestBody Map<String, Object> req) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> testMaps = (List<Map<String, Object>>) req.get("testCases");
        String context = (String) req.getOrDefault("context", "");

        if (testMaps == null || testMaps.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "testCases required"));
        }

        List<TestCase> tests = fromMaps(testMaps);
        log.info("AI Agent enhancing {} test cases", tests.size());

        List<TestCase> enhanced = agentService.enhanceTestCases(tests, context);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "testCases", enhanced,
                "testCaseCount", enhanced.size(),
                "aiEnhancedCount", enhanced.stream().filter(TestCase::isAiEnhanced).count()));
    }

    /** Generate security test cases for a URL */
    @GetMapping("/security-tests")
    public ResponseEntity<List<TestCase>> securityTests(@RequestParam String url) {
        log.info("Received request for AI-driven security tests for URL: [{}]", url);
        long startTime = System.currentTimeMillis();

        String normalizedUrl = UrlUtils.normalizeUrl(url);

        try {
            if (!ollamaService.isAvailable()) {
                log.warn("Security test generation aborted: Ollama AI service is offline");
                return ResponseEntity.status(503).build();
            }

            log.info("Initiating AI security analysis for [{}]", normalizedUrl);
            List<TestCase> tests = agentService.generateSecurityTests(normalizedUrl, "");
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("AI security analysis for [{}] completed in {}ms. Generated {} special security scenarios.", 
                    normalizedUrl, duration, tests.size());
            return ResponseEntity.ok(tests);
        } catch (Exception e) {
            log.error("FATAL: AI security test generation failed for [{}] after {}ms: {}", 
                    normalizedUrl, (System.currentTimeMillis() - startTime), e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/accessibility-tests")
    public ResponseEntity<List<TestCase>> accessibilityTests(@RequestParam String url) {
        log.info("Received request for AI-driven accessibility tests for URL: [{}]", url);
        long startTime = System.currentTimeMillis();

        String normalizedUrl = UrlUtils.normalizeUrl(url);

        try {
            if (!ollamaService.isAvailable()) {
                log.warn("Accessibility test generation aborted: Ollama AI service is offline");
                return ResponseEntity.status(503).build();
            }

            log.info("Initiating AI accessibility analysis for [{}]", normalizedUrl);
            List<TestCase> tests = agentService.generateAccessibilityTests(normalizedUrl, "");
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("AI accessibility analysis for [{}] completed in {}ms. Generated {} special accessibility scenarios.", 
                    normalizedUrl, duration, tests.size());
            return ResponseEntity.ok(tests);
        } catch (Exception e) {
            log.error("FATAL: AI accessibility test generation failed for [{}] after {}ms: {}", 
                    normalizedUrl, (System.currentTimeMillis() - startTime), e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
/** Quick prompt to Ollama (for the chat interface) */
    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody Map<String, String> req) {
        String message = req.get("message");
        String system = req.getOrDefault("system",
                "You are an expert QA engineer assistant. Help with test automation, test case design, and quality assurance.");

        if (message == null || message.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "message required"));
        }

        if (!ollamaService.isAvailable()) {
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "response",
                    "AI is offline. Please install Ollama from https://ollama.com and run: ollama pull qwen2.5-coder:7b"));
        }

        String response = ollamaService.chat(system, message);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "response", response,
                "model", ollamaService.getModel()));
    }

    @SuppressWarnings("unchecked")
    private List<TestCase> fromMaps(List<Map<String, Object>> maps) {
        return maps.stream().map(m -> TestCase.builder()
                .id(str(m, "id"))
                .title(str(m, "title"))
                .description(str(m, "description"))
                .precondition(str(m, "precondition"))
                .steps(toList(m.get("steps")))
                .expectedResults(toList(m.get("expectedResults")))
                .priority(str(m, "priority"))
                .tags(toList(m.get("tags")))
                .targetUrl(str(m, "targetUrl"))
                .build()).collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private List<String> toList(Object obj) {
        if (obj instanceof List<?> l)
            return l.stream().map(Object::toString).collect(Collectors.toList());
        return new ArrayList<>();
    }

    private String str(Map<String, Object> m, String k) {
        return m.getOrDefault(k, "").toString();
    }
}
