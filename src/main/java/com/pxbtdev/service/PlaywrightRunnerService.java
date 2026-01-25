// ./src/main/java/com/pxbtdev/service/PlaywrightRunnerService.java
package com.pxbtdev.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class PlaywrightRunnerService {

    private static final String PLAYWRIGHT_TEST_DIR = "generated-playwright-tests";
    private static final String PLAYWRIGHT_REPORT_DIR = "playwright-report";
    private static final String PLAYWRIGHT_RESULTS_DIR = "test-results";

    public Map<String, Object> runPlaywrightTest(String scriptContent, String testName) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("testName", testName);
        result.put("timestamp", System.currentTimeMillis());

        try {
            // 1. Save the script to a file
            String filename = saveScriptToFile(scriptContent, testName);
            if (filename == null) {
                result.put("error", "Failed to save script file");
                return result;
            }

            // 2. Check if Playwright is installed
            boolean playwrightInstalled = isPlaywrightInstalled();
            if (!playwrightInstalled) {
                result.put("warning", "Playwright not installed. Attempting to install...");
                installPlaywright();
            }

            // 3. Run the test
            TestExecutionResult executionResult = executePlaywrightTest(filename);

            // 4. Collect results
            result.put("success", executionResult.exitCode == 0);
            result.put("exitCode", executionResult.exitCode);
            result.put("output", executionResult.output);
            result.put("errorOutput", executionResult.errorOutput);
            result.put("executionTime", executionResult.executionTime);
            result.put("filename", filename);

            // 5. Get test results if available
            if (executionResult.exitCode == 0 || executionResult.exitCode == 1) {
                Map<String, Object> testResults = collectTestResults();
                result.put("testResults", testResults);

                // Check if there's an HTML report
                String htmlReportPath = findHtmlReport();
                if (htmlReportPath != null) {
                    result.put("htmlReportPath", htmlReportPath);
                    result.put("htmlReportUrl", "/playwright-report/index.html");
                }
            }

            log.info("Playwright test execution completed for: {}", testName);

        } catch (Exception e) {
            log.error("Failed to run Playwright test", e);
            result.put("error", "Execution failed: " + e.getMessage());
        }

        return result;
    }

    private String saveScriptToFile(String scriptContent, String testName) {
        try {
            // Create directory if it doesn't exist
            Path outputDir = Paths.get(PLAYWRIGHT_TEST_DIR);
            if (!Files.exists(outputDir)) {
                Files.createDirectories(outputDir);
            }

            // Sanitize filename
            String safeName = testName
                    .replaceAll("[^a-zA-Z0-9\\-_]", "_")
                    .toLowerCase();

            String filename = safeName + "_" + System.currentTimeMillis() + ".spec.js";
            Path filePath = outputDir.resolve(filename);

            // Write the script
            Files.writeString(filePath, scriptContent);

            log.info("Script saved to: {}", filePath.toAbsolutePath());
            return filename;

        } catch (Exception e) {
            log.error("Failed to save script file", e);
            return null;
        }
    }

    private boolean isPlaywrightInstalled() {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("npx", "playwright", "--version");
            processBuilder.directory(new File("."));
            Process process = processBuilder.start();

            // First, wait for the process to complete with timeout
            boolean completed = process.waitFor(5, TimeUnit.SECONDS);

            if (completed) {
                // If completed, get the exit code
                int exitCode = process.exitValue();
                return exitCode == 0;
            } else {
                // Process timed out
                process.destroy();
                return false;
            }

        } catch (Exception e) {
            log.debug("Playwright check failed: {}", e.getMessage());
            return false;
        }
    }

    private void installPlaywright() throws IOException, InterruptedException {
        log.info("Installing Playwright...");

        ProcessBuilder processBuilder = new ProcessBuilder("npm", "init", "playwright@latest", "-y");
        processBuilder.directory(new File("."));
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();

        // Read output
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                log.info("Playwright install: {}", line);
            }
        }

        // Wait for installation to complete (2 minute timeout)
        boolean completed = process.waitFor(120, TimeUnit.SECONDS);

        if (completed) {
            int exitCode = process.exitValue();
            log.info("Playwright installation completed with exit code: {}", exitCode);

            if (exitCode != 0) {
                throw new RuntimeException("Playwright installation failed with exit code: " + exitCode);
            }
        } else {
            process.destroy();
            throw new RuntimeException("Playwright installation timed out after 2 minutes");
        }
    }

    private TestExecutionResult executePlaywrightTest(String filename) {
        TestExecutionResult result = new TestExecutionResult();
        long startTime = System.currentTimeMillis();

        try {
            // Build the command
            List<String> command = new ArrayList<>();
            command.add("npx");
            command.add("playwright");
            command.add("test");
            command.add(PLAYWRIGHT_TEST_DIR + "/" + filename);
            command.add("--reporter=html");
            command.add("--output=" + PLAYWRIGHT_RESULTS_DIR);

            log.info("Executing Playwright command: {}", String.join(" ", command));

            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.directory(new File("."));
            processBuilder.redirectErrorStream(true); // Merge stdout and stderr

            Process process = processBuilder.start();

            // Read output
            StringBuilder outputBuilder = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    outputBuilder.append(line).append("\n");
                    log.debug("Playwright output: {}", line);
                }
            }

            // Wait for process to complete with timeout (5 minutes)
            boolean completed = process.waitFor(300, TimeUnit.SECONDS);

            if (completed) {
                result.exitCode = process.exitValue();
                result.output = outputBuilder.toString();

                // Parse for error messages
                if (result.exitCode != 0) {
                    result.errorOutput = extractErrorMessage(result.output);
                }
            } else {
                process.destroy();
                result.errorOutput = "Test execution timed out after 5 minutes";
                result.exitCode = -1;
                result.output = outputBuilder.toString();
            }

            result.executionTime = System.currentTimeMillis() - startTime;
            log.info("Test execution completed in {}ms with exit code {}", result.executionTime, result.exitCode);

        } catch (Exception e) {
            log.error("Failed to execute Playwright test", e);
            result.errorOutput = "Execution error: " + e.getMessage();
            result.exitCode = -1;
            result.executionTime = System.currentTimeMillis() - startTime;
        }

        return result;
    }

    private String extractErrorMessage(String output) {
        // Extract meaningful error from output
        if (output.contains("Error:")) {
            int errorIndex = output.indexOf("Error:");
            int endIndex = output.indexOf("\n", errorIndex);
            if (endIndex > errorIndex) {
                return output.substring(errorIndex, endIndex).trim();
            }
            return output.substring(errorIndex).trim();
        }

        // If no "Error:" found, return first few lines
        String[] lines = output.split("\n");
        StringBuilder error = new StringBuilder();
        for (String line : lines) {
            if (line.toLowerCase().contains("error") || line.toLowerCase().contains("fail") ||
                    line.toLowerCase().contains("exception")) {
                error.append(line).append("\n");
            }
        }

        if (error.length() > 0) {
            return error.toString().trim();
        }

        // Return truncated output if nothing specific found
        return output.length() > 200 ? output.substring(0, 200) + "..." : output;
    }

    private Map<String, Object> collectTestResults() {
        Map<String, Object> results = new HashMap<>();

        try {
            // Check for test results JSON file
            Path resultsJson = Paths.get(PLAYWRIGHT_RESULTS_DIR, "test-results.json");
            if (Files.exists(resultsJson)) {
                String jsonContent = Files.readString(resultsJson);
                results.put("jsonResults", jsonContent);
            }

            // Check for JUnit XML results
            Path junitXml = Paths.get(PLAYWRIGHT_RESULTS_DIR, "results.xml");
            if (Files.exists(junitXml)) {
                results.put("hasJUnitResults", true);
            }

            // Count screenshots if any
            Path screenshotsDir = Paths.get(PLAYWRIGHT_RESULTS_DIR, "screenshots");
            if (Files.exists(screenshotsDir)) {
                long screenshotCount = Files.list(screenshotsDir)
                        .filter(path -> path.toString().endsWith(".png"))
                        .count();
                results.put("screenshotCount", screenshotCount);
            }

        } catch (Exception e) {
            log.warn("Failed to collect test results", e);
        }

        return results;
    }

    private String findHtmlReport() {
        try {
            Path reportIndex = Paths.get(PLAYWRIGHT_REPORT_DIR, "index.html");
            if (Files.exists(reportIndex)) {
                return reportIndex.toAbsolutePath().toString();
            }

            // Also check in playwright-report directory
            Path playwrightReport = Paths.get("playwright-report", "index.html");
            if (Files.exists(playwrightReport)) {
                return playwrightReport.toAbsolutePath().toString();
            }

        } catch (Exception e) {
            log.warn("Failed to find HTML report", e);
        }

        return null;
    }

    public Map<String, Object> getPlaywrightStatus() {
        Map<String, Object> status = new HashMap<>();

        status.put("installed", isPlaywrightInstalled());
        status.put("testDir", PLAYWRIGHT_TEST_DIR);
        status.put("reportDir", PLAYWRIGHT_REPORT_DIR);
        status.put("resultsDir", PLAYWRIGHT_RESULTS_DIR);

        // Check if directories exist
        status.put("testDirExists", Files.exists(Paths.get(PLAYWRIGHT_TEST_DIR)));
        status.put("reportDirExists", Files.exists(Paths.get(PLAYWRIGHT_REPORT_DIR)));

        // Count existing test files
        try {
            Path testDir = Paths.get(PLAYWRIGHT_TEST_DIR);
            if (Files.exists(testDir)) {
                long testCount = Files.list(testDir)
                        .filter(path -> path.toString().endsWith(".spec.js"))
                        .count();
                status.put("testCount", testCount);
            }
        } catch (Exception e) {
            log.warn("Failed to count test files", e);
        }

        return status;
    }

    // Helper class for execution results
    private static class TestExecutionResult {
        int exitCode;
        String output;
        String errorOutput;
        long executionTime;
    }
}