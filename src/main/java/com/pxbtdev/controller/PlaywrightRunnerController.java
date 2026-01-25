// ./src/main/java/com/pxbtdev/controller/PlaywrightRunnerController.java
package com.pxbtdev.controller;

import com.pxbtdev.service.PlaywrightRunnerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/playwright")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:8080")
public class PlaywrightRunnerController {

    private final PlaywrightRunnerService playwrightRunnerService;

    @PostMapping("/run")
    public ResponseEntity<Map<String, Object>> runPlaywrightTest(@RequestBody Map<String, Object> request) {
        try {
            String scriptContent = (String) request.get("script");
            String testName = (String) request.get("testName");

            if (scriptContent == null || scriptContent.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "Script content is required"
                ));
            }

            if (testName == null || testName.trim().isEmpty()) {
                testName = "automated_test_" + System.currentTimeMillis();
            }

            log.info("Running Playwright test: {}", testName);
            Map<String, Object> result = playwrightRunnerService.runPlaywrightTest(scriptContent, testName);

            // Add additional info
            result.put("requestTime", System.currentTimeMillis());

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Failed to run Playwright test", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", "Failed to run test: " + e.getMessage(),
                    "timestamp", System.currentTimeMillis()
            ));
        }
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getPlaywrightStatus() {
        try {
            Map<String, Object> status = playwrightRunnerService.getPlaywrightStatus();
            status.put("success", true);
            status.put("timestamp", System.currentTimeMillis());
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            log.error("Failed to get Playwright status", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", "Failed to get status: " + e.getMessage(),
                    "timestamp", System.currentTimeMillis()
            ));
        }
    }

    @GetMapping("/report")
    public ResponseEntity<Map<String, Object>> getLatestReport() {
        try {
            Map<String, Object> status = playwrightRunnerService.getPlaywrightStatus();

            // Check if report exists
            boolean hasReport = (boolean) status.getOrDefault("reportDirExists", false);

            if (hasReport) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "hasReport", true,
                        "reportUrl", "/playwright-report/index.html",
                        "timestamp", System.currentTimeMillis()
                ));
            } else {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "hasReport", false,
                        "message", "No report available. Run a test first.",
                        "timestamp", System.currentTimeMillis()
                ));
            }

        } catch (Exception e) {
            log.error("Failed to get report", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", "Failed to get report: " + e.getMessage(),
                    "timestamp", System.currentTimeMillis()
            ));
        }
    }
}