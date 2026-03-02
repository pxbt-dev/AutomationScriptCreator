package com.pxbtdev.service;

import com.pxbtdev.model.entity.TestCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * AI Agent Service - Orchestrates multiple AI-powered testing agents.
 *
 * Agents:
 * - Test Enhancer: Improves existing test cases with edge cases
 * - Security Agent: Finds security test scenarios
 * - Accessibility Agent: Generates WCAG test cases
 * - API Agent: Analyses API specs for comprehensive coverage
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AIAgentService {

    private final OllamaService ollamaService;

    /**
     * Run the Test Enhancement Agent on a list of test cases.
     */
    public List<TestCase> enhanceTestCases(List<TestCase> testCases, String pageContext) {
        if (!ollamaService.isAvailable()) {
            log.warn("AI Agent: Ollama not available. Returning original test cases.");
            return testCases;
        }

        List<TestCase> enhanced = new ArrayList<>();
        for (TestCase tc : testCases) {
            enhanced.add(enhanceSingle(tc, pageContext));
        }

        // Also generate new edge case tests
        enhanced.addAll(generateEdgeCases(testCases, pageContext));

        log.info("AI Agent enhanced {} test cases, generated {} total", testCases.size(), enhanced.size());
        return enhanced;
    }

    /**
     * Enhance a single test case with AI.
     */
    public TestCase enhanceSingle(TestCase tc, String context) {
        if (!ollamaService.isAvailable())
            return tc;

        String system = """
                You are a senior QA engineer. Your job is to improve a test case by:
                1. Making steps more specific and actionable
                2. Adding missing assertions
                3. Including edge cases
                Return ONLY a valid JSON object with fields: title, steps (array), expectedResults (array).
                No extra text.
                """;

        String user = """
                Page context: %s

                Test case to improve:
                Title: %s
                Steps: %s
                Expected Results: %s

                Return ONLY the JSON object.
                """.formatted(
                context,
                tc.getTitle(),
                String.join("; ", tc.getSteps() != null ? tc.getSteps() : List.of()),
                String.join("; ", tc.getExpectedResults() != null ? tc.getExpectedResults() : List.of()));

        try {
            String response = ollamaService.chat(system, user);
            if (response == null || response.isBlank())
                return tc;

            int start = response.indexOf('{');
            int end = response.lastIndexOf('}');
            if (start < 0 || end < 0)
                return tc;

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> improved = mapper.readValue(response.substring(start, end + 1), Map.class);

            List<String> steps = toList(improved.get("steps"));
            List<String> expected = toList(improved.get("expectedResults"));

            if (steps.isEmpty() && expected.isEmpty())
                return tc;

            List<String> tags = new ArrayList<>(tc.getTags() != null ? tc.getTags() : List.of());
            if (!tags.contains("ai-enhanced"))
                tags.add("ai-enhanced");

            return TestCase.builder()
                    .id(tc.getId())
                    .title(improved.getOrDefault("title", tc.getTitle()).toString())
                    .description(tc.getDescription())
                    .precondition(tc.getPrecondition())
                    .steps(steps.isEmpty() ? tc.getSteps() : steps)
                    .expectedResults(expected.isEmpty() ? tc.getExpectedResults() : expected)
                    .priority(tc.getPriority())
                    .tags(tags)
                    .aiEnhanced(true)
                    .targetUrl(tc.getTargetUrl())
                    .elementSelector(tc.getElementSelector())
                    .elementType(tc.getElementType())
                    .build();

        } catch (Exception e) {
            log.debug("Failed to enhance test case {}: {}", tc.getId(), e.getMessage());
            return tc;
        }
    }

    /**
     * Security Agent: Generate security-focused test cases.
     */
    public List<TestCase> generateSecurityTests(String url, String elementSummary) {
        if (!ollamaService.isAvailable())
            return List.of();

        String system = """
                You are a security-focused QA engineer. Generate test cases targeting common web vulnerabilities.
                Output ONLY a JSON array. Each element: title, description, precondition, steps (array), expectedResults (array), priority (High/Medium/Low), tags (array).
                No extra text.
                """;

        String user = """
                URL: %s
                Elements on page: %s

                Generate 4-5 security test cases covering:
                - XSS in input fields
                - SQL injection in search/form fields
                - CSRF protection
                - Clickjacking headers
                - Sensitive data exposure in URLs

                Output JSON array only.
                """.formatted(url, elementSummary);

        return callAndParse(user, system, url, "SEC");
    }

    /**
     * Accessibility Agent: Generate WCAG 2.1 compliance tests.
     */
    public List<TestCase> generateAccessibilityTests(String url, String pageMetadata) {
        if (!ollamaService.isAvailable())
            return List.of();

        String system = """
                You are a WCAG 2.1 accessibility expert QA engineer.
                Generate specific, actionable accessibility test cases.
                Output ONLY a JSON array. Each element: title, description, precondition, steps (array), expectedResults (array), priority (High/Medium/Low), tags (array).
                No extra text.
                """;

        String user = """
                URL: %s
                Page info: %s

                Generate 4-5 WCAG 2.1 AA compliance test cases covering:
                - Keyboard navigation (Tab order, focus management)
                - Screen reader compatibility (ARIA labels, roles)
                - Colour contrast ratios
                - Form error announcement
                - Skip navigation links

                Output JSON array only.
                """.formatted(url, pageMetadata);

        return callAndParse(user, system, url, "A11Y");
    }

    /**
     * Get AI agent status and model info.
     */
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        boolean available = ollamaService.isAvailable();
        status.put("available", available);
        status.put("model", ollamaService.getModel());
        status.put("enabled", ollamaService.isEnabled());

        if (available) {
            List<String> models = ollamaService.getAvailableModels();
            status.put("availableModels", models);
            status.put("status", "READY");
            status.put("message", "AI agents are ready. Model: " + ollamaService.getModel());
        } else {
            status.put("status", "OFFLINE");
            status.put("message",
                    "Ollama is not running. Install from https://ollama.com and run: ollama pull qwen2.5-coder:7b");
            status.put("availableModels", List.of());
        }

        return status;
    }

    private List<TestCase> generateEdgeCases(List<TestCase> existingTests, String context) {
        if (!ollamaService.isAvailable())
            return List.of();

        String titles = existingTests.stream()
                .limit(5)
                .map(TestCase::getTitle)
                .collect(Collectors.joining(", "));

        String system = """
                You are a QA expert. Generate edge case tests NOT covered by the existing tests.
                Output ONLY a JSON array. Each element: title, description, precondition, steps (array), expectedResults (array), priority, tags (array).
                """;

        String user = """
                Context: %s
                Existing tests: %s

                Generate 2-3 edge case tests not already covered. Focus on: timeouts, network errors, session expiry, concurrent users.
                Output JSON array only.
                """
                .formatted(context, titles);

        return callAndParse(user, system, null, "EDGE");
    }

    @SuppressWarnings("unchecked")
    private List<TestCase> callAndParse(String userPrompt, String systemPrompt, String url, String idPrefix) {
        List<TestCase> result = new ArrayList<>();
        try {
            String response = ollamaService.chat(systemPrompt, userPrompt);
            if (response == null || response.isBlank())
                return result;

            int start = response.indexOf('[');
            int end = response.lastIndexOf(']');
            if (start < 0 || end < 0)
                return result;

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            List<Map<String, Object>> cases = mapper.readValue(response.substring(start, end + 1),
                    mapper.getTypeFactory().constructCollectionType(List.class, Map.class));

            for (Map<String, Object> tc : cases) {
                List<String> tags = toList(tc.get("tags"));
                tags.add("ai-enhanced");
                tags.add("ai-agent");
                result.add(TestCase.builder()
                        .id(idPrefix + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                        .title(str(tc, "title"))
                        .description(str(tc, "description"))
                        .precondition(str(tc, "precondition"))
                        .steps(toList(tc.get("steps")))
                        .expectedResults(toList(tc.get("expectedResults")))
                        .priority(str(tc, "priority"))
                        .tags(tags)
                        .aiEnhanced(true)
                        .targetUrl(url)
                        .build());
            }
        } catch (Exception e) {
            log.warn("AI agent call failed: {}", e.getMessage());
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<String> toList(Object obj) {
        if (obj instanceof List<?>)
            return ((List<?>) obj).stream().map(Object::toString).collect(Collectors.toList());
        return new ArrayList<>();
    }

    private String str(Map<String, Object> m, String k) {
        return m.getOrDefault(k, "").toString();
    }
}
