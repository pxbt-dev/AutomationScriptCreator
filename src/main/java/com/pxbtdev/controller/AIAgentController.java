package com.pxbtdev.controller;

import com.pxbtdev.service.AIAgentService;
import com.pxbtdev.service.OllamaService;
import com.pxbtdev.model.entity.TestCase;
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
    @PostMapping("/security-tests")
    public ResponseEntity<Map<String, Object>> securityTests(@RequestBody Map<String, Object> req) {
        String url = (String) req.get("url");
        String elementSummary = (String) req.getOrDefault("elementSummary", "");

        List<TestCase> tests = agentService.generateSecurityTests(url, elementSummary);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "testCases", tests,
                "testCaseCount", tests.size()));
    }

    /** Generate accessibility test cases */
    @PostMapping("/accessibility-tests")
    public ResponseEntity<Map<String, Object>> accessibilityTests(@RequestBody Map<String, Object> req) {
        String url = (String) req.get("url");
        String pageMetadata = (String) req.getOrDefault("pageMetadata", "");

        List<TestCase> tests = agentService.generateAccessibilityTests(url, pageMetadata);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "testCases", tests,
                "testCaseCount", tests.size()));
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
