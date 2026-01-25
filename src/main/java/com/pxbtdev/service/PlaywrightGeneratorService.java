package com.pxbtdev.service;

import com.pxbtdev.model.entity.TestCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
public class PlaywrightGeneratorService {

    private static final String OUTPUT_DIR = "generated-playwright-tests";

    public String generateTestSuite(List<TestCase> testCases, String url) {
        StringBuilder suite = new StringBuilder();

        // Header
        suite.append("// ============================================\n");
        suite.append("// Playwright Test Suite\n");
        suite.append("// Generated: ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\n");
        suite.append("// URL: ").append(url).append("\n");
        suite.append("// Test Cases: ").append(testCases.size()).append("\n");
        suite.append("// ============================================\n\n");

        // Imports
        suite.append("const { test, expect } = require('@playwright/test');\n\n");

        // Configuration
        suite.append("// Test Configuration\n");
        suite.append("test.describe.configure({ mode: 'serial' }); // Run tests sequentially\n");
        suite.append("test.setTimeout(60000); // 60 second timeout\n\n");

        // Global setup if needed
        suite.append("// Global setup if needed\n");
        suite.append("// test.beforeAll(async ({ browser }) => {\n");
        suite.append("//   // Setup code here\n");
        suite.append("// });\n\n");

        // Generate each test
        for (int i = 0; i < testCases.size(); i++) {
            TestCase testCase = testCases.get(i);
            suite.append(generateTestCase(testCase, i + 1));

            // Add spacing between tests
            if (i < testCases.size() - 1) {
                suite.append("\n");
            }
        }

        // Footer with helper functions
        suite.append("\n// ============================================\n");
        suite.append("// Helper Functions\n");
        suite.append("// ============================================\n\n");

        suite.append("""
            /**
             * Takes a screenshot on test failure
             */
            async function takeScreenshotOnFailure(page, testInfo) {
                if (testInfo.status !== testInfo.expectedStatus) {
                    const screenshotPath = `test-results/screenshots/${testInfo.title}-${Date.now()}.png`;
                    await page.screenshot({ path: screenshotPath, fullPage: true });
                    console.log(`Screenshot saved: ${screenshotPath}`);
                }
            }
            
            /**
             * Logs browser console messages during test
             */
            async function captureConsoleLogs(page) {
                const logs = [];
                page.on('console', msg => {
                    logs.push({
                        type: msg.type(),
                        text: msg.text(),
                        url: msg.location().url,
                        line: msg.location().lineNumber
                    });
                });
                return logs;
            }
            
            /**
             * Waits for network to be idle
             */
            async function waitForNetworkIdle(page) {
                await page.waitForLoadState('networkidle');
            }
            
            /**
             * Generic function to click an element with retry
             */
            async function clickWithRetry(page, selector, maxRetries = 3) {
                for (let i = 0; i < maxRetries; i++) {
                    try {
                        await page.click(selector);
                        return;
                    } catch (error) {
                        if (i === maxRetries - 1) throw error;
                        await page.waitForTimeout(1000);
                    }
                }
            }
            """);

        return suite.toString();
    }

    private String generateTestCase(TestCase testCase, int testNumber) {
        StringBuilder testCode = new StringBuilder();

        // Test function header
        String testName = sanitizeTestName(testCase.getTitle());
        testCode.append("test('").append(testNumber).append(". ").append(testName).append("', async ({ page }) => {\n");

        // Add test metadata as comments
        testCode.append("  // Test Case: ").append(testCase.getTitle()).append("\n");
        testCode.append("  // ID: ").append(testCase.getId()).append("\n");
        testCode.append("  // Priority: ").append(testCase.getPriority()).append("\n");
        if (testCase.getDescription() != null && !testCase.getDescription().isEmpty()) {
            testCode.append("  // Description: ").append(testCase.getDescription()).append("\n");
        }

        // Add tags as comments
        if (testCase.getTags() != null && !testCase.getTags().isEmpty()) {
            testCode.append("  // Tags: ").append(String.join(", ", testCase.getTags())).append("\n");
        }

        testCode.append("\n");

        // Precondition - usually navigation
        if (testCase.getPrecondition() != null && !testCase.getPrecondition().isEmpty()) {
            testCode.append("  // Precondition: ").append(testCase.getPrecondition()).append("\n");

            // Try to extract URL from precondition
            String url = extractUrlFromPrecondition(testCase.getPrecondition());
            if (url != null) {
                testCode.append("  await page.goto('").append(url).append("');\n");
                testCode.append("  await page.waitForLoadState('domcontentloaded');\n");
                testCode.append("  await expect(page).toHaveTitle(/.*/); // Verify page loaded\n\n");
            }
        }

        // Steps
        if (testCase.getSteps() != null && !testCase.getSteps().isEmpty()) {
            testCode.append("  // Test Steps\n");
            for (int i = 0; i < testCase.getSteps().size(); i++) {
                String step = testCase.getSteps().get(i);
                testCode.append("  // Step ").append(i + 1).append(": ").append(step).append("\n");

                // Convert step to Playwright code
                String playwrightStep = convertHumanStepToPlaywright(step);
                testCode.append("  ").append(playwrightStep).append("\n\n");
            }
        }

        // Expected Results as assertions
        if (testCase.getExpectedResults() != null && !testCase.getExpectedResults().isEmpty()) {
            testCode.append("  // Expected Results\n");
            for (String result : testCase.getExpectedResults()) {
                String assertion = convertExpectedResultToAssertion(result);
                testCode.append("  ").append(assertion).append("\n");
            }
        }

        // Add AI-specific enhancements if present
        if (testCase.getTags() != null && testCase.getTags().contains("ai-enhanced")) {
            testCode.append("\n  // AI-Enhanced Test - Additional validations\n");
            testCode.append("  await expect(page.locator('body')).toBeVisible();\n");
            testCode.append("  // Add more AI-suggested validations here\n");
        }

        testCode.append("});\n");

        return testCode.toString();
    }

    private String sanitizeTestName(String title) {
        if (title == null) return "unnamed_test";
        return title.replaceAll("[^a-zA-Z0-9\\s]", "")
                .replaceAll("\\s+", "_")
                .toLowerCase()
                .substring(0, Math.min(50, title.length()));
    }

    private String extractUrlFromPrecondition(String precondition) {
        // Simple URL extraction - you might want to improve this
        if (precondition.contains("http")) {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("https?://[^\\s]+");
            java.util.regex.Matcher matcher = pattern.matcher(precondition);
            if (matcher.find()) {
                return matcher.group();
            }
        }
        return null;
    }

    private String convertHumanStepToPlaywright(String step) {
        // This is a simplified conversion - in production you'd want more sophisticated logic

        step = step.toLowerCase();

        if (step.contains("click") || step.contains("tap") || step.contains("press")) {
            return generateClickCode(step);
        } else if (step.contains("type") || step.contains("enter") || step.contains("input")) {
            return generateTypeCode(step);
        } else if (step.contains("navigate") || step.contains("go to") || step.contains("visit")) {
            return generateNavigationCode(step);
        } else if (step.contains("hover") || step.contains("mouse over")) {
            return generateHoverCode(step);
        } else if (step.contains("select") || step.contains("choose")) {
            return generateSelectCode(step);
        } else if (step.contains("verify") || step.contains("check")) {
            return generateVerificationCode(step);
        } else if (step.contains("wait") || step.contains("pause")) {
            return "await page.waitForTimeout(2000);";
        } else if (step.contains("scroll")) {
            return "await page.evaluate(() => window.scrollBy(0, 500));";
        } else {
            // Generic interaction
            return "// TODO: Implement: " + step;
        }
    }

    private String generateClickCode(String step) {
        String selector = inferSelectorFromStep(step);
        if (selector != null) {
            return "await page.click('" + selector + "');";
        }
        return "// Click action needed - specify selector";
    }

    private String generateTypeCode(String step) {
        String selector = inferSelectorFromStep(step);
        String value = inferTestValueFromStep(step);

        if (selector != null) {
            return "await page.fill('" + selector + "', '" + value + "');";
        }
        return "// Type action needed - specify selector";
    }

    private String generateNavigationCode(String step) {
        // Try to extract URL
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("https?://[^\\s]+");
        java.util.regex.Matcher matcher = pattern.matcher(step);
        if (matcher.find()) {
            return "await page.goto('" + matcher.group() + "');";
        }
        return "// Navigation needed - specify URL";
    }

    private String generateHoverCode(String step) {
        String selector = inferSelectorFromStep(step);
        if (selector != null) {
            return "await page.hover('" + selector + "');";
        }
        return "// Hover action needed - specify selector";
    }

    private String generateSelectCode(String step) {
        String selector = inferSelectorFromStep(step);
        if (selector != null) {
            return "await page.selectOption('" + selector + "', { label: 'Option 1' });";
        }
        return "// Select action needed - specify selector";
    }

    private String generateVerificationCode(String step) {
        if (step.contains("visible") || step.contains("displayed")) {
            String selector = inferSelectorFromStep(step);
            if (selector != null) {
                return "await expect(page.locator('" + selector + "')).toBeVisible();";
            }
        }
        return "// Verification needed: " + step;
    }

    private String inferSelectorFromStep(String step) {
        // Very basic selector inference - in real app you'd have better logic
        if (step.contains("button")) {
            if (step.contains("submit")) return "button[type='submit']";
            return "button";
        } else if (step.contains("input") || step.contains("field")) {
            if (step.contains("email")) return "input[type='email']";
            if (step.contains("password")) return "input[type='password']";
            if (step.contains("text")) return "input[type='text']";
            return "input";
        } else if (step.contains("link") || step.contains("a href")) {
            return "a";
        } else if (step.contains("form")) {
            return "form";
        } else if (step.contains("menu") || step.contains("navigation")) {
            return "nav";
        }
        return null;
    }

    private String inferTestValueFromStep(String step) {
        if (step.contains("email")) return "test@example.com";
        if (step.contains("password")) return "TestPassword123!";
        if (step.contains("name")) return "Test User";
        if (step.contains("username")) return "testuser";
        if (step.contains("phone")) return "123-456-7890";
        return "Test Value";
    }

    private String convertExpectedResultToAssertion(String result) {
        result = result.toLowerCase();

        if (result.contains("load") || result.contains("loaded")) {
            return "await expect(page).toHaveTitle(/.*/);";
        } else if (result.contains("visible") || result.contains("displayed")) {
            return "// Verify visibility of key elements";
        } else if (result.contains("error") || result.contains("fail")) {
            return "// Check for console errors\n  const consoleErrors = [];\n  page.on('console', msg => {\n    if (msg.type() === 'error') consoleErrors.push(msg.text());\n  });\n  // Note: You might want to assert on consoleErrors array";
        } else if (result.contains("respond") || result.contains("time")) {
            return "// Performance assertion - page should be responsive";
        } else if (result.contains("success") || result.contains("work") || result.contains("correct")) {
            return "// Verify successful operation";
        }

        return "// Assert: " + result;
    }

    public String saveTestSuiteToFile(List<TestCase> testCases, String url) throws IOException {
        String suiteContent = generateTestSuite(testCases, url);
        String filename = generateFilename(url);
        Path outputDir = Paths.get(OUTPUT_DIR);

        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
        }

        Path filePath = outputDir.resolve(filename);
        Files.writeString(filePath, suiteContent);

        log.info("Playwright test suite saved to: {}", filePath.toAbsolutePath());

        return filename;
    }

    private String generateFilename(String url) {
        String domain = url.replaceAll("https?://", "").replaceAll("[^a-zA-Z0-9]", "_");
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        return domain + "_" + timestamp + ".spec.js";
    }
}