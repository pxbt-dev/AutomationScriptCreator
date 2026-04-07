package com.pxbtdev.service;

import com.pxbtdev.model.entity.InteractiveElement;
import com.pxbtdev.model.entity.TestCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates comprehensive test case suites from discovered page elements.
 * Uses Ollama AI to enhance test cases when available.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TestCaseGeneratorService {

    private final ElementDiscoveryService elementDiscoveryService;
    private final OllamaService ollamaService;

    /**
     * Generate test cases from a URL.
     */
    public List<TestCase> generateFromUrl(String url, String html, boolean useAI) {
        log.info("Starting test case generation from URL: [{}]", url);
        List<InteractiveElement> elements = elementDiscoveryService.discoverElements(html, url);
        return generateFromElements(elements, url, html, useAI);
    }

    /**
     * Generate test cases from discovered elements.
     */
    public List<TestCase> generateFromElements(List<InteractiveElement> elements, String url, String html,
            boolean useAI) {
        log.info("Generating test suite for {} elements on {}", elements.size(), url);
        long start = System.currentTimeMillis();
        List<TestCase> testCases = new ArrayList<>();

        // 1. Smoke test (always)
        log.info("Phase 1: Generating standard smoke tests");
        testCases.add(generateSmokeTest(url));

        // 2. Accessibility baseline
        log.info("Phase 2: Generating accessibility verification tests");
        testCases.add(generateAccessibilityTest(url));

        // 3. Per-element tests
        log.info("Phase 3: Generating individual element interaction tests");
        for (InteractiveElement el : elements) {
            testCases.add(generateElementTest(el, url));
        }
        log.info("Created {} element-specific test cases", elements.size());

        // 4. Form tests (grouped)
        log.info("Phase 4: Identifying form-related elements for bulk testing");
        List<InteractiveElement> formInputs = elements.stream()
                .filter(e -> e.getElementType().equals("input") || e.getElementType().equals("select")
                        || e.getElementType().equals("textarea"))
                .collect(Collectors.toList());
        if (!formInputs.isEmpty()) {
            log.info("Found {} form inputs, generating happy-path and validation tests", formInputs.size());
            testCases.add(generateFormTest(formInputs, url));
            testCases.add(generateFormValidationTest(formInputs, url));
        } else {
            log.info("No form inputs discovered, skipping form-specific test generation");
        }

        // 5. Navigation test (if links found)
        log.info("Phase 5: Generating cross-page navigation tests");
        List<InteractiveElement> links = elements.stream()
                .filter(e -> e.getElementType().equals("link"))
                .limit(5)
                .collect(Collectors.toList());
        if (!links.isEmpty()) {
            log.info("Found {} links for navigation testing", links.size());
            testCases.add(generateNavigationTest(links, url));
        }

        // 6. AI enhancement (if available)
        if (useAI) {
            log.info("Phase 6: AI-enhanced test generation requested");
            if (ollamaService.isAvailable()) {
                log.info("Ollama AI is online - initiating deep context analysis");
                List<TestCase> aiTests = generateAITestCases(elements, url, html);
                testCases.addAll(aiTests);
                log.info("AI successfully added {} complex test scenarios", aiTests.size());
            } else {
                log.warn("AI enhancement requested but Ollama service is unavailable/offline");
            }
        }

        log.info("Test suite generation completed in {}ms. Total test cases: {}", 
                (System.currentTimeMillis() - start), testCases.size());
        return testCases;
    }

    private TestCase generateSmokeTest(String url) {
        return TestCase.builder()
                .id("SMOKE-" + shortId())
                .title("Page Load Smoke Test")
                .description("Verifies the page loads successfully and core elements are present")
                .precondition("Browser is open and network is available")
                .steps(List.of(
                        "Navigate to " + url,
                        "Wait for page to fully load",
                        "Verify page title is not empty",
                        "Verify no 404 or 500 error is displayed",
                        "Verify at least one interactive element is visible"))
                .expectedResults(List.of(
                        "Page loads within 5 seconds",
                        "HTTP status 200 received",
                        "Page title is visible in browser tab",
                        "No JavaScript console errors on load",
                        "Core page content is visible"))
                .priority("High")
                .tags(List.of("smoke", "regression", "page-load"))
                .targetUrl(url)
                .build();
    }

    private TestCase generateAccessibilityTest(String url) {
        return TestCase.builder()
                .id("A11Y-" + shortId())
                .title("Basic Accessibility Check")
                .description("Verifies basic WCAG 2.1 accessibility requirements")
                .precondition("Page is loaded at " + url)
                .steps(List.of(
                        "Navigate to " + url,
                        "Check that all images have alt text",
                        "Check that all form inputs have associated labels",
                        "Check that page has a single <h1> heading",
                        "Verify colour contrast ratio meets WCAG AA (4.5:1)",
                        "Verify all interactive elements are keyboard-focusable"))
                .expectedResults(List.of(
                        "All images have descriptive alt attributes",
                        "All inputs are labelled",
                        "One H1 heading exists",
                        "Text contrast ratio >= 4.5:1",
                        "Tab order is logical"))
                .priority("Medium")
                .tags(List.of("accessibility", "a11y", "wcag"))
                .targetUrl(url)
                .build();
    }

    private TestCase generateElementTest(InteractiveElement el, String url) {
        String type = el.getElementType();
        String label = el.getLabel() != null && !el.getLabel().isEmpty() ? el.getLabel() : el.getSelector();
        List<String> steps;
        List<String> expectedResults;

        switch (type) {
            case "button" -> {
                steps = List.of(
                        "Navigate to " + url,
                        "Locate the '" + label + "' button using selector: " + el.getSelector(),
                        "Verify button is visible and enabled",
                        "Click the '" + label + "' button");
                expectedResults = List.of(
                        "Button is visible and not disabled",
                        "Button responds to click event",
                        "Expected action or navigation occurs after click");
            }
            case "input" -> {
                String testValue = getTestValue(el);
                steps = List.of(
                        "Navigate to " + url,
                        "Locate the '" + label + "' input field: " + el.getSelector(),
                        "Verify input field is visible and enabled",
                        "Enter test value: '" + testValue + "'",
                        el.isRequired() ? "Attempt to submit without value and verify error" : "Clear the field");
                expectedResults = List.of(
                        "Input accepts the test value",
                        "Value is retained in the field",
                        el.isRequired() ? "Validation error shown when empty" : "Field can be cleared");
            }
            case "link" -> {
                steps = List.of(
                        "Navigate to " + url,
                        "Locate the '" + label + "' link: " + el.getSelector(),
                        "Verify link is visible",
                        "Click the link",
                        "Verify navigation destination");
                expectedResults = List.of(
                        "Link is visible",
                        "Link navigates to: " + (el.getHref() != null ? el.getHref() : "expected URL"),
                        "Destination page loads without errors");
            }
            case "select" -> {
                steps = List.of(
                        "Navigate to " + url,
                        "Locate the '" + label + "' dropdown: " + el.getSelector(),
                        "Click to open the dropdown",
                        "Select first available option",
                        "Select last available option",
                        "Verify selection is saved");
                expectedResults = List.of(
                        "Dropdown opens correctly",
                        "Options are listed",
                        "Selected value is reflected in the field");
            }
            case "form" -> {
                steps = List.of(
                        "Navigate to " + url,
                        "Locate the form: " + el.getSelector(),
                        "Fill all required fields with valid test data",
                        "Submit the form");
                expectedResults = List.of(
                        "Form accepts test data",
                        "No validation errors with valid data",
                        "Form submits successfully or shows confirmation");
            }
            default -> {
                steps = List.of("Navigate to " + url, "Interact with element: " + el.getSelector());
                expectedResults = List.of("Element responds correctly to interaction");
            }
        }

        return TestCase.builder()
                .id(type.toUpperCase() + "-" + shortId())
                .title(toTitleCase(type) + " Test: " + label)
                .description("Tests the '" + label + "' " + type + " element")
                .precondition("User is on " + url)
                .steps(steps)
                .expectedResults(expectedResults)
                .priority(el.getPriority() >= 80 ? "High" : el.getPriority() >= 50 ? "Medium" : "Low")
                .tags(List.of(type, "element-test", el.getActionType()))
                .elementSelector(el.getSelector())
                .elementType(el.getElementType())
                .targetUrl(url)
                .build();
    }

    private TestCase generateFormTest(List<InteractiveElement> formInputs, String url) {
        List<String> steps = new ArrayList<>();
        steps.add("Navigate to " + url);
        steps.add("Wait for all form elements to be visible");
        for (InteractiveElement input : formInputs) {
            String label = input.getLabel() != null && !input.getLabel().isEmpty() ? input.getLabel()
                    : input.getSelector();
            steps.add("Fill '" + label + "' with: " + getTestValue(input));
        }
        steps.add("Submit the form");
        steps.add("Verify success state");

        return TestCase.builder()
                .id("FORM-" + shortId())
                .title("Happy Path Form Submission")
                .description("Tests complete form submission with valid data")
                .precondition("User is on " + url + " with all form fields visible")
                .steps(steps)
                .expectedResults(List.of(
                        "All fields accept valid test data",
                        "No validation errors with valid data",
                        "Form submits successfully",
                        "Success message or redirect occurs"))
                .priority("High")
                .tags(List.of("form", "happy-path", "regression"))
                .targetUrl(url)
                .build();
    }

    private TestCase generateFormValidationTest(List<InteractiveElement> formInputs, String url) {
        List<String> steps = new ArrayList<>();
        steps.add("Navigate to " + url);
        steps.add("Leave all fields empty");
        steps.add("Attempt to submit the form");
        steps.add("Verify validation errors appear");

        List<InteractiveElement> required = formInputs.stream()
                .filter(InteractiveElement::isRequired)
                .collect(Collectors.toList());
        if (!required.isEmpty()) {
            for (InteractiveElement r : required) {
                String label = r.getLabel() != null && !r.getLabel().isEmpty() ? r.getLabel() : r.getSelector();
                steps.add("Verify error shown for: " + label);
            }
        }

        return TestCase.builder()
                .id("FORM-VAL-" + shortId())
                .title("Form Validation - Required Fields")
                .description("Tests that form validation correctly rejects empty required fields")
                .precondition("User is on " + url)
                .steps(steps)
                .expectedResults(List.of(
                        "Form does not submit with empty required fields",
                        "Validation error messages are shown",
                        "Error messages are descriptive and accessible",
                        "Fields are highlighted to indicate the error"))
                .priority("High")
                .tags(List.of("form", "validation", "negative-test"))
                .targetUrl(url)
                .build();
    }

    private TestCase generateNavigationTest(List<InteractiveElement> links, String url) {
        List<String> steps = new ArrayList<>();
        steps.add("Navigate to " + url);
        for (InteractiveElement link : links) {
            steps.add("Click navigation item: '" + link.getLabel() + "'");
            steps.add("Verify page loads at: " + (link.getHref() != null ? link.getHref() : "expected url"));
            steps.add("Press Back to return");
        }

        return TestCase.builder()
                .id("NAV-" + shortId())
                .title("Navigation Link Verification")
                .description("Verifies all main navigation links load correctly")
                .precondition("User is on " + url)
                .steps(steps)
                .expectedResults(List.of(
                        "All navigation links are clickable",
                        "Each link navigates to the correct destination",
                        "No broken links (no 404 errors)",
                        "Browser back navigation works correctly"))
                .priority("High")
                .tags(List.of("navigation", "smoke", "regression"))
                .targetUrl(url)
                .build();
    }

    private List<TestCase> generateAITestCases(List<InteractiveElement> elements, String url, String html) {
        List<TestCase> aiTests = new ArrayList<>();

        // Summarise elements for the prompt
        String elementSummary = elements.stream()
                .limit(20)
                .map(e -> "- " + e.getElementType() + ": '" + e.getLabel() + "' [" + e.getSelector() + "]")
                .collect(Collectors.joining("\n"));

        String systemPrompt = """
                You are an expert QA engineer specialising in web application testing.
                Generate comprehensive, specific test cases for edge cases and security scenarios.
                Output ONLY valid JSON as a JSON array of test case objects with fields:
                title, description, precondition, steps (array), expectedResults (array), priority (High/Medium/Low), tags (array).
                Do not include any text before or after the JSON array.
                """;

        String userPrompt = """
                Page URL: %s

                Interactive elements found:
                %s

                Generate 3-5 additional test cases covering:
                1. Security edge cases (e.g., XSS in inputs, SQL injection attempts in search)
                2. Boundary conditions (e.g., max-length inputs, special characters)
                3. Accessibility keyboard navigation
                4. Mobile/responsive behaviour
                5. Error state handling

                Output as JSON array only.
                """.formatted(url, elementSummary);

        try {
            String response = ollamaService.chat(systemPrompt, userPrompt);
            if (response != null && !response.isBlank()) {
                aiTests = parseAITestCases(response, url);
                log.info("AI generated {} additional test cases", aiTests.size());
            }
        } catch (Exception e) {
            log.warn("AI test case generation failed: {}", e.getMessage());
        }

        return aiTests;
    }

    @SuppressWarnings("unchecked")
    private List<TestCase> parseAITestCases(String jsonResponse, String url) {
        List<TestCase> result = new ArrayList<>();
        try {
            // Extract JSON array from response
            int start = jsonResponse.indexOf('[');
            int end = jsonResponse.lastIndexOf(']');
            if (start < 0 || end < 0)
                return result;

            String jsonArray = jsonResponse.substring(start, end + 1);
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            List<Map<String, Object>> cases = mapper.readValue(jsonArray,
                    mapper.getTypeFactory().constructCollectionType(List.class, Map.class));

            for (Map<String, Object> tc : cases) {
                List<String> steps = toStringList(tc.get("steps"));
                List<String> expected = toStringList(tc.get("expectedResults"));
                List<String> tags = toStringList(tc.get("tags"));
                if (!tags.contains("ai-enhanced")) {
                    tags = new ArrayList<>(tags);
                    tags.add("ai-enhanced");
                }

                result.add(TestCase.builder()
                        .id("AI-" + shortId())
                        .title(getString(tc, "title", "AI Test Case"))
                        .description(getString(tc, "description", ""))
                        .precondition(getString(tc, "precondition", "User is on " + url))
                        .steps(steps)
                        .expectedResults(expected)
                        .priority(getString(tc, "priority", "Medium"))
                        .tags(tags)
                        .aiEnhanced(true)
                        .targetUrl(url)
                        .build());
            }
        } catch (Exception e) {
            log.warn("Failed to parse AI test cases JSON: {}", e.getMessage());
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<String> toStringList(Object obj) {
        if (obj instanceof List<?> list) {
            return list.stream().map(Object::toString).collect(Collectors.toList());
        }
        return new ArrayList<>();
    }

    private String getString(Map<String, Object> map, String key, String defaultValue) {
        Object val = map.get(key);
        return val != null ? val.toString() : defaultValue;
    }

    private String getTestValue(InteractiveElement el) {
        if (el.getInputType() == null)
            return "Test Value";
        return switch (el.getInputType()) {
            case "email" -> "test@example.com";
            case "password" -> "TestPass123!";
            case "tel" -> "+44 7700 900000";
            case "number" -> "42";
            case "date" -> "2024-01-15";
            case "url" -> "https://example.com";
            case "search" -> "test search query";
            default -> "Test " + (el.getLabel() != null && !el.getLabel().isEmpty() ? el.getLabel() : "Value");
        };
    }

    private String toTitleCase(String input) {
        if (input == null || input.isEmpty())
            return input;
        return input.substring(0, 1).toUpperCase() + input.substring(1).toLowerCase();
    }

    private String shortId() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}