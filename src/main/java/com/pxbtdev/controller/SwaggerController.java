package com.pxbtdev.controller;

import com.pxbtdev.service.SwaggerParserService;
import com.pxbtdev.service.ApiTestGeneratorService;
import com.pxbtdev.service.ApiTestRunnerService;
import com.pxbtdev.model.entity.TestCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/swagger")
@RequiredArgsConstructor
public class SwaggerController {

    private final SwaggerParserService parserService;
    private final ApiTestGeneratorService testGeneratorService;
    private final ApiTestRunnerService testRunnerService;

    /** Parse a Swagger/OpenAPI spec from URL */
    @PostMapping("/parse")
    public ResponseEntity<Map<String, Object>> parse(@RequestBody Map<String, String> req) {
        String url = req.get("url");
        if (url == null || url.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Swagger URL is required"));
        }
        log.info("Parsing Swagger spec: {}", url);
        Map<String, Object> result = parserService.parseSpec(url);
        return ResponseEntity.ok(result);
    }

    /** Generate test cases from a previously parsed spec */
    @PostMapping("/generate-tests")
    public ResponseEntity<Map<String, Object>> generateTests(@RequestBody Map<String, Object> req) {
        @SuppressWarnings("unchecked")
        Map<String, Object> spec = (Map<String, Object>) req.get("spec");
        boolean useAI = Boolean.TRUE.equals(req.get("useAI"));

        if (spec == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Spec is required"));
        }

        log.info("Generating API tests from spec (AI={})", useAI);
        long start = System.currentTimeMillis();
        List<TestCase> tests = testGeneratorService.generateFromSpec(spec, useAI);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "testCases", tests,
                "testCaseCount", tests.size(),
                "aiEnhancedCount", tests.stream().filter(TestCase::isAiEnhanced).count(),
                "processingTimeMs", System.currentTimeMillis() - start));
    }

    /** Parse spec and generate tests in one shot */
    @PostMapping("/parse-and-generate")
    public ResponseEntity<Map<String, Object>> parseAndGenerate(@RequestBody Map<String, Object> req) {
        String url = (String) req.get("url");
        boolean useAI = Boolean.TRUE.equals(req.get("useAI"));

        if (url == null || url.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Swagger URL is required"));
        }

        log.info("Parse and generate for: {} (AI={})", url, useAI);

        Map<String, Object> spec = parserService.parseSpec(url);
        if (!Boolean.TRUE.equals(spec.get("success"))) {
            return ResponseEntity.ok(spec); // Return error from parser
        }

        long start = System.currentTimeMillis();
        List<TestCase> tests = testGeneratorService.generateFromSpec(spec, useAI);

        Map<String, Object> result = new LinkedHashMap<>(spec);
        result.put("testCases", tests);
        result.put("testCaseCount", tests.size());
        result.put("aiEnhancedCount", tests.stream().filter(TestCase::isAiEnhanced).count());
        result.put("processingTimeMs", System.currentTimeMillis() - start);
        return ResponseEntity.ok(result);
    }

    /** Execute API tests against a live endpoint */
    @PostMapping("/run-tests")
    public ResponseEntity<Map<String, Object>> runTests(@RequestBody Map<String, Object> req) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> testCaseMaps = (List<Map<String, Object>>) req.get("testCases");
        String authToken = (String) req.getOrDefault("authToken", "");

        if (testCaseMaps == null || testCaseMaps.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "testCases are required"));
        }

        // Convert maps back to TestCase objects (simple approach)
        List<TestCase> testCases = convertToTestCases(testCaseMaps);

        log.info("Running {} API tests", testCases.size());
        long start = System.currentTimeMillis();
        List<Map<String, Object>> results = testRunnerService.runTests(testCases, authToken);

        long passed = results.stream().filter(r -> Boolean.TRUE.equals(r.get("passed"))).count();
        long failed = results.size() - passed;

        return ResponseEntity.ok(Map.of(
                "success", true,
                "results", results,
                "summary", Map.of(
                        "total", results.size(),
                        "passed", passed,
                        "failed", failed,
                        "passRate", results.isEmpty() ? 0 : (int) Math.round(passed * 100.0 / results.size())),
                "executionTimeMs", System.currentTimeMillis() - start));
    }

    @SuppressWarnings("unchecked")
    private List<TestCase> convertToTestCases(List<Map<String, Object>> maps) {
        List<TestCase> list = new ArrayList<>();
        for (Map<String, Object> m : maps) {
            list.add(TestCase.builder()
                    .id(str(m, "id"))
                    .title(str(m, "title"))
                    .targetUrl(str(m, "targetUrl"))
                    .tags(toList(m.get("tags")))
                    .steps(toList(m.get("steps")))
                    .expectedResults(toList(m.get("expectedResults")))
                    .priority(str(m, "priority"))
                    .build());
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    private List<String> toList(Object obj) {
        if (obj instanceof List<?> l)
            return l.stream().map(Object::toString).collect(java.util.stream.Collectors.toList());
        return new ArrayList<>();
    }

    private String str(Map<String, Object> m, String k) {
        return m.getOrDefault(k, "").toString();
    }
}
