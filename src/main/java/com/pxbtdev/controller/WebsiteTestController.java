package com.pxbtdev.controller;

import com.pxbtdev.service.WebPageAnalyzerService;
import com.pxbtdev.service.ElementDiscoveryService;
import com.pxbtdev.service.TestCaseGeneratorService;
import com.pxbtdev.service.PlaywrightRunnerService;
import com.pxbtdev.model.entity.InteractiveElement;
import com.pxbtdev.model.entity.TestCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/website")
@RequiredArgsConstructor
public class WebsiteTestController {

    private final WebPageAnalyzerService analyzerService;
    private final ElementDiscoveryService discoveryService;
    private final TestCaseGeneratorService testCaseGeneratorService;
    private final PlaywrightRunnerService playwrightRunnerService;

    /** Analyse a URL: fetch metadata and element inventory */
    @GetMapping("/analyze")
    public ResponseEntity<Map<String, Object>> analyze(@RequestParam String url) {
        try {
            log.info("Analysing URL: {}", url);
            Map<String, Object> pageInfo = analyzerService.analyzePage(url);

            String html = (String) pageInfo.get("_html");
            if (html == null)
                html = analyzerService.fetchPageHtml(url);

            List<InteractiveElement> elements = html != null
                    ? discoveryService.discoverElements(html, url)
                    : List.of();

            Map<String, Object> result = new LinkedHashMap<>(pageInfo);
            result.put("elements", elements);
            result.put("elementCount", elements.size());
            result.put("success", !pageInfo.containsKey("error"));
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Analysis failed for {}: {}", url, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /** Generate test cases from a URL */
    @PostMapping("/generate-tests")
    public ResponseEntity<Map<String, Object>> generateTests(@RequestBody Map<String, Object> req) {
        String url = (String) req.get("url");
        boolean useAI = Boolean.TRUE.equals(req.get("useAI"));

        if (url == null || url.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "URL is required"));
        }

        try {
            long start = System.currentTimeMillis();
            log.info("Generating tests for {} (AI={})", url, useAI);

            String html = analyzerService.fetchPageHtml(url);
            if (html == null)
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "error", "Could not fetch page"));

            List<TestCase> tests = testCaseGeneratorService.generateFromUrl(url, html, useAI);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("url", url);
            result.put("testCases", tests);
            result.put("testCaseCount", tests.size());
            result.put("aiEnhanced", useAI);
            result.put("aiEnhancedCount", tests.stream().filter(TestCase::isAiEnhanced).count());
            result.put("processingTimeMs", System.currentTimeMillis() - start);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Test generation failed for {}: {}", url, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /** Generate a Playwright script from a URL */
    @PostMapping("/generate-playwright")
    public ResponseEntity<Map<String, Object>> generatePlaywright(@RequestBody Map<String, Object> req) {
        String url = (String) req.get("url");
        boolean useAI = Boolean.TRUE.equals(req.get("useAI"));

        if (url == null || url.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "URL is required"));
        }

        try {
            log.info("Generating Playwright script for {}", url);
            String html = analyzerService.fetchPageHtml(url);
            if (html == null)
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "error", "Could not fetch page"));

            List<TestCase> tests = testCaseGeneratorService.generateFromUrl(url, html, useAI);
            String script = playwrightRunnerService.generatePlaywrightScript(tests, url);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "script", script,
                    "testCaseCount", tests.size(),
                    "url", url));
        } catch (Exception e) {
            log.error("Playwright generation failed: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /** Execute a Playwright script */
    @PostMapping("/run-playwright")
    public ResponseEntity<Map<String, Object>> runPlaywright(@RequestBody Map<String, Object> req) {
        String script = (String) req.get("script");
        String testName = (String) req.getOrDefault("testName", "test_" + System.currentTimeMillis());

        if (script == null || script.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Script is required"));
        }

        log.info("Running Playwright test: {}", testName);
        Map<String, Object> result = playwrightRunnerService.runScript(script, testName);
        return ResponseEntity.ok(result);
    }

    /** Playwright environment status */
    @GetMapping("/playwright-status")
    public ResponseEntity<Map<String, Object>> playwrightStatus() {
        return ResponseEntity.ok(playwrightRunnerService.getStatus());
    }
}