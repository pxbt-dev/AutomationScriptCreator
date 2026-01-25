package com.pxbtdev.controller;

import com.pxbtdev.config.AIProperties;
import com.pxbtdev.model.entity.InteractiveElement;
import com.pxbtdev.model.entity.TestCase;
import com.pxbtdev.service.*;
import com.pxbtdev.utils.TestOllamaConnection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AITestController {

    private final WebPageAnalyzerService webPageAnalyzerService;
    private final TestOllamaConnection testOllamaConnection;
    private final SimpleOllamaService simpleOllamaService;
    private final AITestEnhancerService aiTestEnhancerService;
    private final ElementDiscoveryOrchestrator elementDiscoveryOrchestrator;
    private final TestCaseGeneratorService testCaseGeneratorService;
    private final AIProperties aiProperties;

    @GetMapping("/test")
    public ResponseEntity<Map<String, String>> testAIConnection() {
        Map<String, String> response = new HashMap<>();

        try {
            // Test basic connection with timeout
            CompletableFuture<String> ollamaTestFuture = CompletableFuture.supplyAsync(testOllamaConnection::testConnection
            );

            CompletableFuture<String> simpleTestFuture = CompletableFuture.supplyAsync(simpleOllamaService::testConnection
            );

            CompletableFuture<String> enhancerTestFuture = CompletableFuture.supplyAsync(aiTestEnhancerService::testAIConnection
            );

            // Wait for all with timeout
            CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                    ollamaTestFuture, simpleTestFuture, enhancerTestFuture
            );

            allFutures.get(30, TimeUnit.SECONDS); // Overall timeout 30 seconds

            response.put("ollamaConnection", ollamaTestFuture.get());
            response.put("simpleService", simpleTestFuture.get());
            response.put("enhancerService", enhancerTestFuture.get());

            return ResponseEntity.ok(response);

        } catch (TimeoutException e) {
            response.put("error", "AI tests timed out after 30 seconds. Check if Ollama is running.");
            response.put("hint", "Start Ollama: `ollama serve` or `ollama run llama2`");
            return ResponseEntity.status(504).body(response); // 504 Gateway Timeout
        } catch (Exception e) {
            response.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @GetMapping("/test-simple")
    public ResponseEntity<Map<String, String>> testSimplePrompt() {
        try {
            String prompt = "What is 2+2? Answer with just the number.";
            String response = simpleOllamaService.generate(prompt);

            Map<String, String> result = new HashMap<>();
            result.put("prompt", prompt);
            result.put("response", response);
            result.put("success", "true");

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    @GetMapping("/another-test-simple")
    public ResponseEntity<Map<String, Object>> testSimple() {
        // Return a simple hardcoded test case to verify the API works
        TestCase testCase = TestCase.builder()
                .id("TEST-123")
                .title("Simple Test Case")
                .description("This is a test case to verify the API is working")
                .precondition("User is on the homepage")
                .steps(Arrays.asList(
                        "Navigate to the homepage",
                        "Verify page loads",
                        "Check for visible elements"
                ))
                .expectedResults(Arrays.asList(
                        "Page loads within 3 seconds",
                        "Header is visible",
                        "Navigation works"
                ))
                .priority("High")
                .tags(Arrays.asList("test", "smoke", "ai-enhanced"))
                .build();

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "API is working");
        response.put("testCase", testCase);
        response.put("testCases", Collections.singletonList(testCase));
        response.put("testCaseCount", 1);
        response.put("timestamp", System.currentTimeMillis());

        return ResponseEntity.ok(response);
    }


    @PostMapping("/debug-response")
    public ResponseEntity<Map<String, Object>> debugResponse(@RequestBody Map<String, Object> request) {
        String url = (String) request.get("url");

        try {
            log.info("Debug test for URL: {}", url);
            String html = webPageAnalyzerService.fetchPageHtml(url);
            List<InteractiveElement> elements = elementDiscoveryOrchestrator.discoverInteractiveElements(html, url);
            List<TestCase> testCases = testCaseGeneratorService.generateTestCases(html, url);

            // Debug: Check what's in the test cases
            log.info("Generated {} test cases", testCases.size());
            for (int i = 0; i < testCases.size(); i++) {
                TestCase tc = testCases.get(i);
                log.info("Test Case {}: {}", i, tc.getTitle());
                log.info("  Steps: {}", tc.getSteps() != null ? tc.getSteps().size() : 0);
                log.info("  Expected Results: {}", tc.getExpectedResults() != null ? tc.getExpectedResults().size() : 0);
                log.info("  Tags: {}", tc.getTags());
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("testCases", testCases);
            response.put("testCasesCount", testCases.size());
            response.put("aiEnhancedCount", testCases.stream()
                    .filter(tc -> tc.getTags() != null && tc.getTags().contains("ai-enhanced"))
                    .count());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Debug failed", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", e.getMessage(),
                    "stackTrace", Arrays.toString(e.getStackTrace())
            ));
        }
    }

    // In AITestController.java - update the testOllamaDirect method:
    @GetMapping("/test-ollama-direct")
    public ResponseEntity<Map<String, Object>> testOllamaDirect() {
        Map<String, Object> result = new HashMap<>();

        // Create HttpClient inside try-with-resources
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:11434/api/generate"))
                    .timeout(Duration.ofSeconds(5))
                    .POST(HttpRequest.BodyPublishers.ofString(
                            "{\"model\":\"llama2\",\"prompt\":\"Hello\",\"stream\":false}"
                    ))
                    .header("Content-Type", "application/json")
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            result.put("statusCode", response.statusCode());
            result.put("status", response.statusCode() == 200 ? "Ollama is running" : "Ollama error");
            result.put("success", true);

        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("hint", "Make sure Ollama is installed and running: `ollama serve`");
        }

        return ResponseEntity.ok(result);
    }

    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getAIConfig() {
        try {
            Map<String, Object> config = new HashMap<>();

            // AI enhancement limits
            config.put("limitSingle", aiProperties.getLimitSingle());
            config.put("limitAll", aiProperties.getLimitAll());
            config.put("limitFast", aiProperties.getLimitFast());
            config.put("limitPlaywright", aiProperties.getLimitPlaywright());

            // Safety limits
            config.put("maxTotalTestCases", aiProperties.getMaxTotalTestCases());
            config.put("maxAITestsPerRequest", aiProperties.getMaxAITestsPerRequest());
            config.put("maxConcurrentAIRequests", aiProperties.getMaxConcurrentAIRequests());

            // Timeouts
            config.put("timeoutMs", aiProperties.getTimeoutMs());
            config.put("batchSize", aiProperties.getBatchSize());

            // Thresholds
            config.put("minElementsForAI", aiProperties.getMinElementsForAI());
            config.put("maxElementsForAI", aiProperties.getMaxElementsForAI());

            // Flags
            config.put("enableAdvancedPrompting", aiProperties.isEnableAdvancedPrompting());
            config.put("confidenceThreshold", aiProperties.getConfidenceThreshold());
            config.put("unlimitedFlag", 0); // For frontend reference

            return ResponseEntity.ok(config);

        } catch (Exception e) {
            log.error("Failed to get AI config", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to load AI configuration"));
        }
    }


}