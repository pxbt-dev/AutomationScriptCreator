package com.pxbtdev.controller;

import com.pxbtdev.config.AIProperties;
import com.pxbtdev.model.entity.InteractiveElement;
import com.pxbtdev.model.entity.TestCase;
import com.pxbtdev.service.ElementDiscoveryOrchestrator;
import com.pxbtdev.service.TestCaseGeneratorService;
import com.pxbtdev.service.WebPageAnalyzerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/elements")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:8080")
public class ElementDiscoveryController {

    private final WebPageAnalyzerService webPageAnalyzerService;
    private final ElementDiscoveryOrchestrator elementDiscoveryOrchestrator;
    private final TestCaseGeneratorService testCaseGeneratorService;
    private final AIProperties aiProperties;

    @PostMapping("/discover")
    public ResponseEntity<Map<String, Object>> discoverElements(@RequestBody Map<String, String> request) {
        String url = request.get("url");
        boolean useML = Boolean.parseBoolean(request.getOrDefault("useML", "true"));
        boolean useAI = Boolean.parseBoolean(request.getOrDefault("useAI", "true"));

        if (url == null || url.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "URL is required"));
        }

        try {
            log.info("Discovering elements for URL: {}, ML: {}, AI: {}", url, useML, useAI);

            // Fetch page HTML
            String html = webPageAnalyzerService.fetchPageHtml(url);
            if (html == null || html.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Failed to fetch page content"));
            }

            // Discover elements
            List<InteractiveElement> elements;
            if (useML) {
                elements = elementDiscoveryOrchestrator.discoverInteractiveElementsWithML(html, url);
            } else {
                elements = elementDiscoveryOrchestrator.discoverInteractiveElements(html, url);
            }

            // Generate test cases if requested
            List<TestCase> testCases = null;
            if (useAI) {
                String sessionId = "session_" + UUID.randomUUID().toString().substring(0, 8);
                testCases = testCaseGeneratorService.generateTestCases(html, url, true, aiProperties.getLimitForMode("all"), sessionId);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("url", url);
            response.put("elementCount", elements.size());
            response.put("elements", elements);
            response.put("testCases", testCases);
            response.put("htmlPreview", html.substring(0, Math.min(500, html.length())) + "...");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Element discovery failed for URL: {}", url, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Discovery failed: " + e.getMessage()));
        }
    }

    @GetMapping("/analyze")
    public ResponseEntity<Map<String, Object>> analyzeUrl(@RequestParam String url) {
        try {
            log.info("Analyzing URL: {}", url);

            String html = webPageAnalyzerService.fetchPageHtml(url);
            if (html == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Failed to fetch page"));
            }

            // Basic analysis
            List<InteractiveElement> elements = elementDiscoveryOrchestrator.discoverInteractiveElements(html, url);

            Map<String, Object> analysis = new HashMap<>();
            analysis.put("url", url);
            analysis.put("totalElements", elements.size());
            analysis.put("htmlLength", html.length());

            // Count by type
            Map<String, Long> elementTypes = elements.stream()
                    .collect(Collectors.groupingBy(InteractiveElement::getElementType, Collectors.counting()));
            analysis.put("elementTypes", elementTypes);

            // Count by action
            Map<String, Long> actionTypes = elements.stream()
                    .collect(Collectors.groupingBy(InteractiveElement::getActionType, Collectors.counting()));
            analysis.put("actionTypes", actionTypes);

            // Priority distribution
            Map<String, Long> priorityDistribution = elements.stream()
                    .collect(Collectors.groupingBy(
                            e -> {
                                int priority = e.getPriority();
                                if (priority >= 70) return "High";
                                if (priority >= 40) return "Medium";
                                return "Low";
                            },
                            Collectors.counting()
                    ));
            analysis.put("priorityDistribution", priorityDistribution);

            return ResponseEntity.ok(analysis);

        } catch (Exception e) {
            log.error("Analysis failed for URL: {}", url, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Analysis failed: " + e.getMessage()));
        }
    }

    @PostMapping("/generate-test-cases")
    public ResponseEntity<Map<String, Object>> generateTestCases(@RequestBody Map<String, Object> request) {
        String url = (String) request.get("url");
        String mode = (String) request.getOrDefault("mode", "all");
        Boolean enhanceWithAI = (Boolean) request.getOrDefault("enhanceWithAI", false);
        Integer customLimit = (Integer) request.getOrDefault("limitAITests", null);
        Boolean fastMode = (Boolean) request.getOrDefault("fastMode", false);

        if (url == null || url.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "URL is required"
            ));
        }

        try {
            long startTime = System.currentTimeMillis();

            // Determine AI limit from properties or custom value
            int aiLimit;
            if (customLimit != null) {
                aiLimit = customLimit;
                log.info("Using custom AI limit: {}", aiLimit);
            } else {
                aiLimit = aiProperties.getLimitForMode(mode);
                log.info("Using AI limit for mode '{}': {}", mode, aiLimit);
            }

            log.info("Generating test cases for URL: {}, Mode: {}, AI Limit: {}, Fast: {}",
                    url, mode, aiLimit, fastMode);

            String html = webPageAnalyzerService.fetchPageHtml(url);
            if (html == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "Failed to fetch page"
                ));
            }

            // In fast mode, use a simplified HTML (first 5000 chars)
            if (fastMode && html.length() > 5000) {
                html = html.substring(0, 5000) + "... [TRUNCATED FOR SPEED]";
                log.info("Using truncated HTML for fast mode: {} chars", html.length());
            }

            List<TestCase> testCases;

            if (fastMode) {
                // Fast mode: generate basic test cases only
                testCases = generateBasicTestCasesFast(url, html);
            } else {
                // Normal mode: use the service with AI limit from properties
                String sessionId = "session_" + UUID.randomUUID().toString().substring(0, 8);
                testCases = testCaseGeneratorService.generateTestCases(html, url, enhanceWithAI, aiLimit, sessionId);
            }

            long processingTime = System.currentTimeMillis() - startTime;

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("url", url);
            response.put("testCaseCount", testCases.size());
            response.put("testCases", testCases);
            response.put("mode", mode);
            response.put("aiLimit", aiLimit);
            response.put("enhancedWithAI", enhanceWithAI);
            response.put("processingTimeMs", processingTime);
            response.put("fastMode", fastMode);
            response.put("aiUnlimited", aiProperties.isUnlimited(aiLimit));
            response.put("timestamp", System.currentTimeMillis());

            log.info("Generated {} test cases for URL: {} in {}ms (AI limit: {})",
                    testCases.size(), url, processingTime, aiLimit);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Test case generation failed for URL: {}", url, e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", "Generation failed: " + e.getMessage(),
                    "timestamp", System.currentTimeMillis()
            ));
        }
    }

    @PostMapping("/estimate-test-count")
    public ResponseEntity<Map<String, Object>> estimateTestCount(@RequestBody Map<String, String> request) {
        String url = request.get("url");
        String mode = request.getOrDefault("mode", "all");

        if (url == null || url.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "URL is required"
            ));
        }

        try {
            log.info("Estimating test count for URL: {}, Mode: {}", url, mode);

            String html = webPageAnalyzerService.fetchPageHtml(url);
            if (html == null) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "estimatedTests", 10, // Default estimate
                        "message", "Could not fetch page, using default estimate"
                ));
            }

            // Discover elements to estimate count
            List<InteractiveElement> elements = elementDiscoveryOrchestrator.discoverInteractiveElements(html, url);
            int elementCount = elements.size();

            // Estimate based on element count and mode
            int estimatedTests = estimateTestCases(elementCount, mode);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("url", url);
            response.put("elementCount", elementCount);
            response.put("estimatedTests", estimatedTests);
            response.put("mode", mode);
            response.put("timestamp", System.currentTimeMillis());

            log.info("Estimated {} test cases for {} elements (mode: {})",
                    estimatedTests, elementCount, mode);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Test count estimation failed for URL: {}", url, e);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "estimatedTests", 15, // Safe default
                    "error", "Estimation failed, using default",
                    "timestamp", System.currentTimeMillis()
            ));
        }
    }

    private int estimateTestCases(int elementCount, String mode) {
        if (elementCount <= 0) {
            return 10; // Minimum estimate
        }

        // Base estimate: 1 test per element + additional comprehensive tests
        int baseEstimate = Math.min(elementCount, 100); // Cap at 100 elements

        // Add comprehensive tests
        int comprehensiveTests = 5; // Smoke, navigation, form, edge cases, performance

        // AI factor based on mode
        double aiFactor = 1.0;
        switch (mode.toLowerCase()) {
            case "single":
                aiFactor = 1.1; // Slight overhead for single AI test
                break;
            case "all":
                aiFactor = 1.3; // More overhead for full AI enhancement
                break;
            case "playwright":
                aiFactor = 1.2;
                break;
            default: // fast
                aiFactor = 1.0;
        }

        int estimated = (int) ((baseEstimate + comprehensiveTests) * aiFactor);

        // Cap the estimate
        return Math.min(estimated, 500); // Maximum 500 test cases
    }

    @GetMapping("/generation-progress/{sessionId}")
    public ResponseEntity<Map<String, Object>> getGenerationProgress(@PathVariable String sessionId) {
        try {
            Map<String, Object> progress = testCaseGeneratorService.getGenerationProgress(sessionId);
            return ResponseEntity.ok(progress);
        } catch (Exception e) {
            log.error("Failed to get generation progress for session: {}", sessionId, e);
            return ResponseEntity.ok(Map.of(
                    "error", "Progress not available",
                    "sessionId", sessionId
            ));
        }
    }

    @PostMapping("/generate-playwright")
    public ResponseEntity<Map<String, Object>> generatePlaywrightScript(@RequestBody Map<String, Object> request) {
        String url = (String) request.get("url");
        Boolean includeAI = (Boolean) request.getOrDefault("includeAI", false);
        Integer customLimit = (Integer) request.getOrDefault("aiLimit", null);

        if (url == null || url.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "URL is required"
            ));
        }

        try {
            long startTime = System.currentTimeMillis();

            // Use custom limit or default from properties
            int aiLimit = (customLimit != null) ? customLimit : aiProperties.getLimitForMode("playwright");

            log.info("Generating Playwright tests for URL: {}, AI: {}, Limit: {}",
                    url, includeAI, aiLimit);

            String html = webPageAnalyzerService.fetchPageHtml(url);
            if (html == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "Failed to fetch page"
                ));
            }

            // Generate test cases with AI limit from properties
            String sessionId = "session_" + UUID.randomUUID().toString().substring(0, 8);
            List<TestCase> testCases = testCaseGeneratorService.generateTestCases(
                    html,
                    url,
                    includeAI,
                    aiLimit,
                    sessionId
            );

            // Generate Playwright script
            String playwrightScript = "";
            String filename = "";

            if (!testCases.isEmpty()) {
                playwrightScript = testCases.get(0).toPlaywrightScript();
            }

            long processingTime = System.currentTimeMillis() - startTime;

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("url", url);
            response.put("testCaseCount", testCases.size());
            response.put("playwrightScript", playwrightScript);
            response.put("filename", filename);
            response.put("aiLimit", aiLimit);
            response.put("aiUnlimited", aiProperties.isUnlimited(aiLimit));
            response.put("processingTimeMs", processingTime);
            response.put("timestamp", System.currentTimeMillis());

            log.info("Generated Playwright script for {} test cases in {}ms (AI limit: {})",
                    testCases.size(), processingTime, aiLimit);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Playwright generation failed for URL: {}", url, e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", "Generation failed: " + e.getMessage(),
                    "timestamp", System.currentTimeMillis()
            ));
        }
    }

    @GetMapping("/test-simple")
    public ResponseEntity<Map<String, Object>> testSimple() {
        try {
            log.info("Test endpoint called");

            // Create a test case using the builder
            TestCase testCase = TestCase.builder()
                    .id("TEST-" + UUID.randomUUID().toString().substring(0, 8))
                    .title("Simple Test Case")
                    .description("This is a test case to verify the API is working")
                    .precondition("User is on the homepage")
                    .steps(Arrays.asList(
                            "Navigate to the homepage",
                            "Verify page loads within 3 seconds",
                            "Check for visible header elements"
                    ))
                    .expectedResults(Arrays.asList(
                            "Page loads successfully",
                            "Header is visible",
                            "Navigation menu is accessible"
                    ))
                    .priority("High")
                    .tags(Arrays.asList("test", "smoke", "api-test"))
                    .build();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "API is working correctly");
            response.put("testCase", testCase);
            response.put("testCases", Arrays.asList(testCase));
            response.put("testCaseCount", 1);
            response.put("timestamp", System.currentTimeMillis());
            response.put("service", "AutomationScriptCreator");
            response.put("version", "1.0.0");

            log.info("Test endpoint response prepared");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error in test endpoint", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", e.getMessage(),
                    "timestamp", System.currentTimeMillis()
            ));
        }
    }

    @PostMapping("/debug-response")
    public ResponseEntity<Map<String, Object>> debugResponse(@RequestBody Map<String, Object> request) {
        try {
            String url = (String) request.get("url");
            log.info("Debug endpoint called for URL: {}", url);

            // Fetch the page
            String html = webPageAnalyzerService.fetchPageHtml(url);
            if (html == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "Failed to fetch page",
                        "url", url
                ));
            }

            // Discover elements
            List<InteractiveElement> elements = elementDiscoveryOrchestrator.discoverInteractiveElements(html, url);

            // Generate test cases with default AI limit
            String sessionId = "session_" + UUID.randomUUID().toString().substring(0, 8);
            List<TestCase> testCases = testCaseGeneratorService.generateTestCases(html, url, true, aiProperties.getLimitForMode("all"), sessionId);

            // Debug info
            Map<String, Object> debugInfo = new HashMap<>();
            debugInfo.put("url", url);
            debugInfo.put("htmlLength", html.length());
            debugInfo.put("elementCount", elements.size());
            debugInfo.put("testCaseCount", testCases.size());
            debugInfo.put("aiLimitUsed", aiProperties.getLimitForMode("all"));
            debugInfo.put("aiUnlimited", aiProperties.isUnlimited(aiProperties.getLimitForMode("all")));

            // Check AI service status
            try {
                Field ollamaServiceField = TestCaseGeneratorService.class.getDeclaredField("ollamaService");
                ollamaServiceField.setAccessible(true);
                Object ollamaService = ollamaServiceField.get(testCaseGeneratorService);

                if (ollamaService != null) {
                    Method isEnabledMethod = ollamaService.getClass().getMethod("isEnabled");
                    boolean aiEnabled = (boolean) isEnabledMethod.invoke(ollamaService);
                    debugInfo.put("aiEnabled", aiEnabled);
                }
            } catch (Exception e) {
                debugInfo.put("aiEnabled", "Unknown (error checking)");
            }

            // Sample first few elements and test cases
            if (!elements.isEmpty()) {
                debugInfo.put("sampleElements", elements.stream()
                        .limit(3)
                        .map(e -> Map.of(
                                "selector", e.getSelector(),
                                "type", e.getElementType(),
                                "priority", e.getPriority()
                        ))
                        .collect(Collectors.toList()));
            }

            if (!testCases.isEmpty()) {
                debugInfo.put("sampleTestCases", testCases.stream()
                        .limit(3)
                        .map(tc -> Map.of(
                                "id", tc.getId(),
                                "title", tc.getTitle(),
                                "priority", tc.getPriority(),
                                "stepsCount", tc.getSteps() != null ? tc.getSteps().size() : 0,
                                "tags", tc.getTags()
                        ))
                        .collect(Collectors.toList()));
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("debug", debugInfo);
            response.put("testCases", testCases);
            response.put("testCaseCount", testCases.size());
            response.put("timestamp", System.currentTimeMillis());

            log.info("Debug response prepared: {} elements, {} test cases",
                    elements.size(), testCases.size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Debug endpoint error", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", e.getMessage(),
                    "stackTrace", Arrays.toString(e.getStackTrace()),
                    "timestamp", System.currentTimeMillis()
            ));
        }
    }

    private List<TestCase> generateBasicTestCasesFast(String url, String html) {
        List<TestCase> testCases = new ArrayList<>();

        // Quick smoke test
        testCases.add(TestCase.builder()
                .id("FAST-SMOKE-" + UUID.randomUUID().toString().substring(0, 8))
                .title("Quick Smoke Test")
                .description("Basic functionality test generated in fast mode")
                .precondition("User navigates to " + url)
                .steps(Arrays.asList(
                        "Load the page and wait for it to be ready",
                        "Verify page title is not empty",
                        "Check that at least one interactive element is present"
                ))
                .expectedResults(Arrays.asList(
                        "Page loads within 3 seconds",
                        "No JavaScript errors in console",
                        "Page is responsive to user interaction"
                ))
                .priority("High")
                .tags(Arrays.asList("fast", "smoke", "quick-test"))
                .build());

        // Quick navigation test
        testCases.add(TestCase.builder()
                .id("FAST-NAV-" + UUID.randomUUID().toString().substring(0, 8))
                .title("Quick Navigation Test")
                .description("Test basic navigation functionality")
                .precondition("User is on the page")
                .steps(Arrays.asList(
                        "Check for navigation elements (menus, links)",
                        "Verify navigation elements are visible",
                        "Test at least one navigation element"
                ))
                .expectedResults(Arrays.asList(
                        "Navigation elements are accessible",
                        "Navigation works without errors",
                        "Page transitions are smooth"
                ))
                .priority("Medium")
                .tags(Arrays.asList("fast", "navigation", "quick-test"))
                .build());

        // Quick form test (if likely present)
        if (html.toLowerCase().contains("form") || html.toLowerCase().contains("input")) {
            testCases.add(TestCase.builder()
                    .id("FAST-FORM-" + UUID.randomUUID().toString().substring(0, 8))
                    .title("Quick Form Test")
                    .description("Test basic form functionality if forms are present")
                    .precondition("Form elements are present on the page")
                    .steps(Arrays.asList(
                            "Locate form elements",
                            "Enter test data into form fields",
                            "Submit form if submit button is present"
                    ))
                    .expectedResults(Arrays.asList(
                            "Form accepts input",
                            "Form validation works if present",
                            "Submission works without errors"
                    ))
                    .priority("Medium")
                    .tags(Arrays.asList("fast", "form", "quick-test"))
                    .build());
        }

        return testCases;
    }
}