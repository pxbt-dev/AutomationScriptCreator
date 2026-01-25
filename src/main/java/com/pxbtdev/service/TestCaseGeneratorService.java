package com.pxbtdev.service;

import com.pxbtdev.config.AIProperties;
import com.pxbtdev.model.entity.InteractiveElement;
import com.pxbtdev.model.entity.TestCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TestCaseGeneratorService {

    private final ElementDiscoveryOrchestrator elementDiscoveryOrchestrator;
    private final SimpleOllamaService ollamaService;
    private final ElementDiscoveryService elementDiscoveryService;
    private final AIProperties aiProperties;

    // Large thread pool for massive concurrent AI calls
    private final ExecutorService aiExecutor = Executors.newFixedThreadPool(20);

    // Cache for common test cases
    private final Map<String, List<TestCase>> testCaseCache = new ConcurrentHashMap<>();

    // Active sessions for progress tracking
    private final Map<String, GenerationSession> activeSessions = new ConcurrentHashMap<>();

    // Inner class for session tracking
    private static class GenerationSession {
        String sessionId;
        String url;
        int totalElements;
        int totalTestCases;
        int aiEnhancedCount;
        int completedAITests;
        long startTime;
        volatile boolean cancelled;
        List<String> statusMessages = new ArrayList<>();
        Map<String, Integer> elementTypeCounts = new HashMap<>();

        GenerationSession(String sessionId, String url) {
            this.sessionId = sessionId;
            this.url = url;
            this.startTime = System.currentTimeMillis();
        }

        void addStatus(String status) {
            statusMessages.add(String.format("[%tT] %s", new Date(), status));
            if (statusMessages.size() > 100) {
                statusMessages.remove(0);
            }
        }

        void trackElementType(String type) {
            elementTypeCounts.put(type, elementTypeCounts.getOrDefault(type, 0) + 1);
        }

        Map<String, Object> getProgress() {
            long elapsed = System.currentTimeMillis() - startTime;
            int percentComplete = 0;
            if (aiEnhancedCount > 0) {
                percentComplete = (int) ((completedAITests * 100.0) / aiEnhancedCount);
            }

            Map<String, Object> progress = new HashMap<>();
            progress.put("sessionId", sessionId);
            progress.put("url", url);
            progress.put("totalElements", totalElements);
            progress.put("totalTestCases", totalTestCases);
            progress.put("aiEnhancedCount", aiEnhancedCount);
            progress.put("completedAITests", completedAITests);
            progress.put("percentComplete", percentComplete);
            progress.put("elapsedMs", elapsed);
            progress.put("cancelled", cancelled);
            progress.put("elementTypeCounts", new HashMap<>(elementTypeCounts));
            progress.put("statusMessages", new ArrayList<>(statusMessages.subList(
                    Math.max(0, statusMessages.size() - 10), statusMessages.size())));

            // Estimate remaining time (10 seconds per remaining AI test)
            int remainingAITests = Math.max(0, aiEnhancedCount - completedAITests);
            long estimatedRemaining = remainingAITests * 10000L;
            progress.put("estimatedRemainingMs", estimatedRemaining);
            progress.put("eta", new Date(startTime + elapsed + estimatedRemaining));

            // Calculate speed
            if (elapsed > 0) {
                progress.put("testsPerSecond", String.format("%.2f",
                        (completedAITests * 1000.0) / elapsed));
            }

            return progress;
        }
    }

    public List<TestCase> generateTestCases(String html, String url) {
        return generateTestCases(html, url, true, 0, null); // 0 = NO LIMIT
    }

    public List<TestCase> generateTestCases(String html, String url, boolean enableAI, int maxAITests, String sessionId) {
        log.info("🚀 GENERATING TEST CASES - URL: {}, AI: {}, Max AI tests: {}, Session: {}",
                url, enableAI, maxAITests, sessionId);

        if (sessionId == null) {
            sessionId = "session_" + UUID.randomUUID().toString().substring(0, 12);
        }

        // Create session
        GenerationSession session = new GenerationSession(sessionId, url);
        activeSessions.put(sessionId, session);
        session.addStatus("🚀 Starting comprehensive test case generation for: " + url);

        try {
            // Check cache first
            String cacheKey = url + "|" + enableAI + "|" + maxAITests;
            if (testCaseCache.containsKey(cacheKey)) {
                session.addStatus("📦 Returning cached test cases");
                log.info("Returning cached test cases for: {}", url);
                List<TestCase> cached = testCaseCache.get(cacheKey);
                session.totalTestCases = cached.size();
                session.addStatus("✅ Retrieved " + cached.size() + " cached test cases");
                return new ArrayList<>(cached);
            }

            // Discover ALL interactive elements (NO FILTERING)
            session.addStatus("🔍 Discovering ALL interactive elements...");
            long discoverStart = System.currentTimeMillis();
            List<InteractiveElement> elements = discoverAllElementsComprehensive(html, url);
            long discoverTime = System.currentTimeMillis() - discoverStart;
            session.totalElements = elements.size();

            // Track element types
            elements.forEach(e -> session.trackElementType(e.getElementType()));

            session.addStatus(String.format("✅ Found %d interactive elements in %dms",
                    elements.size(), discoverTime));

            if (elements.isEmpty()) {
                session.addStatus("⚠️ No interactive elements found");
                log.warn("No interactive elements found for URL: {}", url);
                return Collections.emptyList();
            }

            // Generate comprehensive test cases - ONE PER ELEMENT
            session.addStatus("⚡ Generating comprehensive test cases...");
            long generateStart = System.currentTimeMillis();
            List<TestCase> testCases = generateMassiveTestSuite(elements, url, session);
            long generateTime = System.currentTimeMillis() - generateStart;
            session.totalTestCases = testCases.size();
            session.addStatus(String.format("✅ Generated %d test cases in %dms",
                    testCases.size(), generateTime));

            // Calculate how many AI tests to enhance (0 = NO LIMIT, enhance ALL)
            int aiTestsToEnhance = calculateAITestsToEnhance(testCases.size(), maxAITests, enableAI);
            session.aiEnhancedCount = aiTestsToEnhance;

            // Enhance with AI if enabled
            if (enableAI && ollamaService != null && ollamaService.isEnabled() && aiTestsToEnhance > 0) {
                session.addStatus("🤖 Enhancing " + aiTestsToEnhance + " test cases with AI...");
                long aiStart = System.currentTimeMillis();
                testCases = enhanceMassiveTestSuiteWithAI(testCases, html, aiTestsToEnhance, session);
                long aiTime = System.currentTimeMillis() - aiStart;
                session.addStatus(String.format("✅ AI enhancement completed in %dms", aiTime));
            } else {
                session.addStatus("⚡ AI enhancement skipped or disabled");
                log.info("AI enhancement disabled or not available");
            }

            // Cache results
            testCaseCache.put(cacheKey, new ArrayList<>(testCases));
            session.addStatus("🎉 Generation complete! Total time: " +
                    (System.currentTimeMillis() - session.startTime) + "ms");

            log.info("✅ Generated {} test cases for URL: {} ({} AI-enhanced)",
                    testCases.size(), url, aiTestsToEnhance);

            return testCases;

        } catch (Exception e) {
            session.addStatus("❌ Error: " + e.getMessage());
            log.error("Test case generation failed for URL: {}", url, e);
            throw new RuntimeException("Failed to generate test cases: " + e.getMessage(), e);
        } finally {
            // Keep session for a while for progress polling
            String finalSessionId = sessionId;
            CompletableFuture.delayedExecutor(60, TimeUnit.SECONDS).execute(() -> {
                activeSessions.remove(finalSessionId);
                log.debug("Cleaned up session: {}", finalSessionId);
            });
        }
    }

    private List<InteractiveElement> discoverAllElementsComprehensive(String html, String url) {
        log.info("Starting comprehensive element discovery for: {}", url);

        // Use ALL discovery methods to get maximum elements
        List<InteractiveElement> basicElements = elementDiscoveryService.discoverInteractiveElements(html, url);
        log.info("Basic discovery found {} elements", basicElements.size());

        List<InteractiveElement> mlElements = elementDiscoveryOrchestrator.discoverInteractiveElementsWithML(html, url);
        log.info("ML discovery found {} elements", mlElements.size());

        // Also look for additional elements using raw HTML parsing
        List<InteractiveElement> rawElements = discoverRawElements(html, url);
        log.info("Raw HTML parsing found {} elements", rawElements.size());

        // Combine ALL elements from ALL sources
        Map<String, InteractiveElement> allElements = new HashMap<>();

        // Add basic elements
        for (InteractiveElement element : basicElements) {
            if (element.getSelector() != null && !element.getSelector().trim().isEmpty()) {
                String key = element.getSelector() + "|" + element.getElementType();
                allElements.put(key, element);
            }
        }

        // Add ML elements (may have better priority scores)
        for (InteractiveElement element : mlElements) {
            if (element.getSelector() != null && !element.getSelector().trim().isEmpty()) {
                String key = element.getSelector() + "|" + element.getElementType();
                InteractiveElement existing = allElements.get(key);
                if (existing == null || element.getPriority() > existing.getPriority()) {
                    allElements.put(key, element);
                }
            }
        }

        // Add raw elements
        for (InteractiveElement element : rawElements) {
            if (element.getSelector() != null && !element.getSelector().trim().isEmpty()) {
                String key = element.getSelector() + "|" + element.getElementType();
                allElements.putIfAbsent(key, element);
            }
        }

        // Convert back to list and sort by priority
        List<InteractiveElement> result = new ArrayList<>(allElements.values());
        result.sort((e1, e2) -> Integer.compare(e2.getPriority(), e1.getPriority()));

        log.info("✅ Total unique interactive elements discovered: {}", result.size());
        return result;
    }

    private List<InteractiveElement> discoverRawElements(String html, String url) {
        List<InteractiveElement> elements = new ArrayList<>();
        try {
            org.jsoup.Jsoup.parse(html, url).select("*").forEach(element -> {
                String tagName = element.tagName().toLowerCase();

                // Check if it's an interactive element
                if (isInteractiveTag(tagName) || hasInteractiveAttributes(element)) {
                    InteractiveElement ie = InteractiveElement.builder()
                            .tagName(tagName)
                            .elementType(getElementTypeFromTag(tagName))
                            .actionType(getActionTypeFromTag(tagName))
                            .selector(generateSelector(element))
                            .text(element.text().substring(0, Math.min(100, element.text().length())))
                            .id(element.id())
                            .classes(element.className())
                            .priority(calculateRawPriority(element))
                            .build();
                    elements.add(ie);
                }
            });
        } catch (Exception e) {
            log.warn("Raw element discovery failed: {}", e.getMessage());
        }
        return elements;
    }

    private boolean isInteractiveTag(String tagName) {
        return tagName.matches("a|button|input|textarea|select|form|nav|menu|details|summary");
    }

    private boolean hasInteractiveAttributes(org.jsoup.nodes.Element element) {
        return element.hasAttr("onclick") || element.hasAttr("onchange") ||
                element.hasAttr("onsubmit") || element.hasAttr("role") ||
                element.attr("role").equals("button") || element.attr("role").equals("link");
    }

    private String getElementTypeFromTag(String tagName) {
        switch (tagName) {
            case "a": return "Link";
            case "button": return "Button";
            case "input": return "Input";
            case "textarea": return "Text Area";
            case "select": return "Dropdown";
            case "form": return "Form";
            case "nav": return "Navigation";
            default: return "Element";
        }
    }

    private String getActionTypeFromTag(String tagName) {
        switch (tagName) {
            case "a": return "CLICK";
            case "button": return "CLICK";
            case "input": return "TYPE";
            case "textarea": return "TYPE";
            case "select": return "SELECT";
            default: return "INTERACT";
        }
    }

    private String generateSelector(org.jsoup.nodes.Element element) {
        if (!element.id().isEmpty()) {
            return "#" + element.id();
        }

        List<String> parts = new ArrayList<>();
        parts.add(element.tagName());

        if (!element.className().isEmpty()) {
            String[] classes = element.className().split("\\s+");
            if (classes.length > 0) {
                parts.add("." + classes[0]);
            }
        }

        // Add attributes for uniqueness
        if (element.hasAttr("name")) {
            parts.add("[name=\"" + element.attr("name") + "\"]");
        } else if (element.hasAttr("type")) {
            parts.add("[type=\"" + element.attr("type") + "\"]");
        } else if (element.hasAttr("href")) {
            parts.add("[href*=\"" + element.attr("href").substring(0, Math.min(20, element.attr("href").length())) + "\"]");
        }

        return String.join("", parts);
    }

    private int calculateRawPriority(org.jsoup.nodes.Element element) {
        int priority = 0;

        if (!element.id().isEmpty()) priority += 30;
        if (element.hasAttr("data-testid") || element.hasAttr("data-qa")) priority += 40;
        if (element.tagName().equals("button") || element.tagName().equals("a")) priority += 20;
        if (element.hasText() && element.text().length() > 2) priority += 10;
        if (element.hasAttr("role") && element.attr("role").equals("button")) priority += 25;
        if (element.hasAttr("aria-label") || element.hasAttr("aria-labelledby")) priority += 15;

        return Math.min(100, priority);
    }

    private List<TestCase> generateMassiveTestSuite(List<InteractiveElement> elements, String url, GenerationSession session) {
        List<TestCase> testCases = new ArrayList<>();

        // 1. Smoke test (always include)
        session.addStatus("Creating smoke test...");
        testCases.add(generateComprehensiveSmokeTest(elements, url));

        // 2. Individual element tests - ONE FOR EACH AND EVERY ELEMENT (NO LIMIT!)
        session.addStatus("Creating individual element tests...");
        int elementTestCount = 0;
        for (InteractiveElement element : elements) {
            if (session.cancelled) break;

            testCases.add(createDetailedElementTest(element, elementTestCount + 1));
            elementTestCount++;

            // Update progress every 50 elements
            if (elementTestCount % 50 == 0) {
                session.addStatus("Created " + elementTestCount + " element tests...");
            }
        }
        session.addStatus("✅ Created " + elementTestCount + " individual element tests");

        // 3. Comprehensive form tests
        session.addStatus("Creating form tests...");
        List<TestCase> formTests = generateComprehensiveFormTests(elements);
        testCases.addAll(formTests);
        session.addStatus("✅ Created " + formTests.size() + " form tests");

        // 4. Navigation flow tests
        session.addStatus("Creating navigation tests...");
        List<TestCase> navTests = generateNavigationFlowTests(elements);
        testCases.addAll(navTests);
        session.addStatus("✅ Created " + navTests.size() + " navigation tests");

        // 5. User journey tests (complex sequences)
        session.addStatus("Creating user journey tests...");
        List<TestCase> journeyTests = generateUserJourneyTests(elements, url);
        testCases.addAll(journeyTests);
        session.addStatus("✅ Created " + journeyTests.size() + " user journey tests");

        // 6. Edge case and error tests
        session.addStatus("Creating edge case tests...");
        List<TestCase> edgeTests = generateEdgeCaseTests(elements);
        testCases.addAll(edgeTests);
        session.addStatus("✅ Created " + edgeTests.size() + " edge case tests");

        // 7. Performance and load tests
        session.addStatus("Creating performance tests...");
        List<TestCase> perfTests = generatePerformanceTests(elements, url);
        testCases.addAll(perfTests);
        session.addStatus("✅ Created " + perfTests.size() + " performance tests");

        // 8. Accessibility tests
        session.addStatus("Creating accessibility tests...");
        List<TestCase> a11yTests = generateAccessibilityTests(elements);
        testCases.addAll(a11yTests);
        session.addStatus("✅ Created " + a11yTests.size() + " accessibility tests");

        log.info("Generated MASSIVE test suite with {} total test cases", testCases.size());
        return testCases;
    }

    private TestCase generateComprehensiveSmokeTest(List<InteractiveElement> elements, String url) {
        // Take top 10 highest priority elements for smoke test
        List<InteractiveElement> criticalElements = elements.stream()
                .sorted((e1, e2) -> Integer.compare(e2.getPriority(), e1.getPriority()))
                .limit(10)
                .collect(Collectors.toList());

        List<String> steps = new ArrayList<>();
        steps.add("Navigate to " + url);
        steps.add("Wait for page to load completely");

        for (InteractiveElement element : criticalElements) {
            steps.add(String.format("Verify %s '%s' is visible and accessible",
                    element.getElementType(),
                    element.getText() != null && !element.getText().isEmpty() ?
                            element.getText().substring(0, Math.min(50, element.getText().length())) :
                            element.getSelector()));
        }

        steps.add("Check console for errors");
        steps.add("Verify page responds within 3 seconds");

        return TestCase.builder()
                .id("SMOKE_" + UUID.randomUUID().toString().substring(0, 8))
                .title("🔥 Comprehensive Smoke Test - Critical Path")
                .description("Tests the most critical user interactions and page functionality. Includes " +
                        criticalElements.size() + " key elements.")
                .precondition("Fresh browser session")
                .steps(steps)
                .expectedResults(Arrays.asList(
                        "✅ Page loads successfully within 3 seconds",
                        "✅ All critical elements are visible and accessible",
                        "✅ No JavaScript errors in console",
                        "✅ Page is responsive and interactive",
                        "✅ User can complete basic navigation"
                ))
                .priority("Critical")
                .tags(Arrays.asList("smoke", "critical", "regression", "comprehensive"))
                .build();
    }

    private TestCase createDetailedElementTest(InteractiveElement element, int index) {
        String priority = determinePriority(element.getPriority());
        String elementDesc = element.getText() != null && !element.getText().isEmpty() ?
                "'" + element.getText().substring(0, Math.min(100, element.getText().length())) + "'" :
                element.getSelector();

        return TestCase.builder()
                .id("ELEM-" + index + "-" + UUID.randomUUID().toString().substring(0, 6))
                .title(String.format("%s. Test %s: %s",
                        index,
                        element.getElementType(),
                        elementDesc))
                .description(String.format("Detailed test for %s element. Selector: %s. Priority score: %d/100",
                        element.getElementType(), element.getSelector(), element.getPriority()))
                .precondition("Element is present on the page")
                .steps(createElementTestSteps(element))
                .expectedResults(createElementExpectedResults(element))
                .priority(priority)
                .tags(createElementTags(element))
                .build();
    }

    private List<String> createElementTestSteps(InteractiveElement element) {
        List<String> steps = new ArrayList<>();
        steps.add("Navigate to the page containing the element");
        steps.add("Wait for page to load completely");
        steps.add(String.format("Locate the %s element using selector: %s",
                element.getElementType(), element.getSelector()));
        steps.add("Verify element is visible in viewport");
        steps.add("Verify element is not disabled");

        switch (element.getActionType()) {
            case "CLICK":
                steps.add("Click on the element");
                steps.add("Verify click produces expected response");
                break;
            case "TYPE":
                String testValue = element.getTestValue() != null ?
                        element.getTestValue() : "test_" + element.getElementType().toLowerCase();
                steps.add("Click to focus on the element");
                steps.add(String.format("Type '%s' into the element", testValue));
                steps.add("Verify input is accepted");
                break;
            case "SELECT":
                steps.add("Click to open dropdown");
                steps.add("Select first available option");
                steps.add("Verify selection is applied");
                break;
            default:
                steps.add("Interact with the element");
                steps.add("Verify interaction works");
        }

        steps.add("Check console for any errors after interaction");
        return steps;
    }

    private List<String> createElementExpectedResults(InteractiveElement element) {
        List<String> results = new ArrayList<>();
        results.add("✅ Element is found using the selector");
        results.add("✅ Element is visible and not obscured");
        results.add("✅ Element is enabled and interactive");

        switch (element.getActionType()) {
            case "CLICK":
                results.add("✅ Click action registers successfully");
                results.add("✅ Appropriate response occurs (navigation, modal, etc.)");
                results.add("✅ No errors in console after click");
                break;
            case "TYPE":
                results.add("✅ Input field accepts text input");
                results.add("✅ Text is visible in the field");
                results.add("✅ No validation errors for test input");
                break;
            case "SELECT":
                results.add("✅ Dropdown opens when clicked");
                results.add("✅ Options are selectable");
                results.add("✅ Selected value is displayed");
                break;
        }

        results.add("✅ Page remains stable after interaction");
        results.add("✅ No JavaScript errors occur");
        return results;
    }

    private List<String> createElementTags(InteractiveElement element) {
        List<String> tags = new ArrayList<>();
        tags.add("element");
        tags.add(element.getElementType().toLowerCase());
        tags.add(element.getActionType().toLowerCase());

        if (element.getPriority() > 70) tags.add("high-priority");
        if (element.getId() != null && !element.getId().isEmpty()) tags.add("has-id");
        if (element.getClasses() != null && !element.getClasses().isEmpty()) tags.add("has-classes");

        return tags;
    }

    private String determinePriority(int score) {
        if (score >= 80) return "Critical";
        if (score >= 60) return "High";
        if (score >= 40) return "Medium";
        if (score >= 20) return "Low";
        return "Info";
    }

    private List<TestCase> generateComprehensiveFormTests(List<InteractiveElement> elements) {
        List<InteractiveElement> formElements = elements.stream()
                .filter(e -> e.getActionType().equals("TYPE") ||
                        e.getActionType().equals("SELECT") ||
                        e.getTagName().equals("form"))
                .collect(Collectors.toList());

        if (formElements.isEmpty()) {
            return Collections.emptyList();
        }

        List<TestCase> tests = new ArrayList<>();

        // Group form elements logically (by proximity in selector or similar attributes)
        Map<String, List<InteractiveElement>> formGroups = new HashMap<>();
        String currentGroup = "form_group_1";
        int count = 0;

        for (InteractiveElement element : formElements) {
            if (count % 5 == 0 && count > 0) {
                currentGroup = "form_group_" + (count / 5 + 1);
            }
            formGroups.computeIfAbsent(currentGroup, k -> new ArrayList<>()).add(element);
            count++;
        }

        // Create test for each form group
        for (Map.Entry<String, List<InteractiveElement>> entry : formGroups.entrySet()) {
            String groupId = entry.getKey();
            List<InteractiveElement> groupElements = entry.getValue();

            List<String> steps = new ArrayList<>();
            steps.add("Navigate to page with form elements");
            steps.add("Verify form section is visible");

            for (InteractiveElement field : groupElements) {
                if (field.getActionType().equals("TYPE")) {
                    String testValue = field.getTestValue() != null ?
                            field.getTestValue() : generateTestValueForField(field);
                    steps.add(String.format("Enter '%s' into %s field (%s)",
                            testValue, field.getElementType(), field.getSelector()));
                } else if (field.getActionType().equals("SELECT")) {
                    steps.add(String.format("Select option from %s dropdown (%s)",
                            field.getElementType(), field.getSelector()));
                }
            }

            steps.add("Submit the form if submit button is present");
            steps.add("Verify form submission behavior");

            TestCase test = TestCase.builder()
                    .id("FORM-" + UUID.randomUUID().toString().substring(0, 8))
                    .title("📝 Form Test: " + groupElements.size() + " fields")
                    .description("Comprehensive form test with " + groupElements.size() +
                            " input fields. Tests data entry, validation, and submission.")
                    .precondition("Form elements are present on page")
                    .steps(steps)
                    .expectedResults(Arrays.asList(
                            "✅ Form accepts valid input in all fields",
                            "✅ No validation errors for test data",
                            "✅ Submit button is enabled when required fields are filled",
                            "✅ Form submission produces expected response",
                            "✅ No console errors during form interaction"
                    ))
                    .priority("High")
                    .tags(Arrays.asList("form", "validation", "submission", "comprehensive"))
                    .build();

            tests.add(test);
        }

        return tests;
    }

    private String generateTestValueForField(InteractiveElement field) {
        if (field.getText() != null && field.getText().toLowerCase().contains("email")) {
            return "test.user@example.com";
        } else if (field.getText() != null && field.getText().toLowerCase().contains("password")) {
            return "SecurePass123!";
        } else if (field.getText() != null && field.getText().toLowerCase().contains("name")) {
            return "Test User";
        } else if (field.getText() != null && field.getText().toLowerCase().contains("phone")) {
            return "+1-234-567-8900";
        } else {
            return "Test value for " + field.getElementType();
        }
    }

    private List<TestCase> generateNavigationFlowTests(List<InteractiveElement> elements) {
        List<InteractiveElement> navElements = elements.stream()
                .filter(e -> e.getTagName().equals("a") ||
                        e.getElementType().toLowerCase().contains("link") ||
                        e.getElementType().toLowerCase().contains("nav"))
                .collect(Collectors.toList());

        if (navElements.isEmpty()) {
            return Collections.emptyList();
        }

        List<TestCase> tests = new ArrayList<>();

        // Create navigation flow tests in batches of 5 links
        int batchSize = 5;
        for (int i = 0; i < navElements.size(); i += batchSize) {
            int end = Math.min(i + batchSize, navElements.size());
            List<InteractiveElement> batch = navElements.subList(i, end);

            List<String> steps = new ArrayList<>();
            steps.add("Start on main page");

            for (InteractiveElement link : batch) {
                String linkText = link.getText() != null && !link.getText().isEmpty() ?
                        "'" + link.getText().substring(0, Math.min(50, link.getText().length())) + "'" :
                        "navigation link";
                steps.add(String.format("Click %s link (%s)", linkText, link.getSelector()));
                steps.add("Verify navigation to correct page");
                steps.add("Wait for page to load");
                steps.add("Use browser back button to return");
            }

            TestCase test = TestCase.builder()
                    .id("NAV-" + UUID.randomUUID().toString().substring(0, 8))
                    .title("🧭 Navigation Flow: " + batch.size() + " links")
                    .description("Tests navigation flow through " + batch.size() +
                            " links. Verifies correct destinations and browser history.")
                    .precondition("Starting on main page")
                    .steps(steps)
                    .expectedResults(Arrays.asList(
                            "✅ All navigation links work correctly",
                            "✅ Links navigate to expected destinations",
                            "✅ Pages load without errors",
                            "✅ Browser history functions properly",
                            "✅ No broken links or 404 errors"
                    ))
                    .priority("Medium")
                    .tags(Arrays.asList("navigation", "links", "flow", "browser"))
                    .build();

            tests.add(test);
        }

        return tests;
    }

    private List<TestCase> generateUserJourneyTests(List<InteractiveElement> elements, String url) {
        List<TestCase> tests = new ArrayList<>();

        // Create complex user journey tests
        String[] journeys = {
                "New user registration flow",
                "Search and result selection",
                "Shopping cart to checkout",
                "Content browsing and consumption",
                "User profile update flow"
        };

        for (String journey : journeys) {
            // Select relevant elements for this journey
            List<InteractiveElement> journeyElements = elements.stream()
                    .filter(e -> isRelevantForJourney(e, journey))
                    .limit(8)
                    .collect(Collectors.toList());

            if (journeyElements.size() >= 3) {
                List<String> steps = new ArrayList<>();
                steps.add("Start journey: " + journey);
                steps.add("Navigate to starting page");

                for (int i = 0; i < journeyElements.size(); i++) {
                    InteractiveElement element = journeyElements.get(i);
                    steps.add(String.format("Step %d: %s on %s (%s)",
                            i + 1,
                            element.getActionType().equals("CLICK") ? "Click" : "Interact with",
                            element.getElementType(),
                            element.getSelector()));
                }

                steps.add("Complete the journey");
                steps.add("Verify journey completion state");

                TestCase test = TestCase.builder()
                        .id("JOURNEY-" + UUID.randomUUID().toString().substring(0, 8))
                        .title("🚀 User Journey: " + journey)
                        .description("Comprehensive user journey test simulating " + journey +
                                " with " + journeyElements.size() + " steps.")
                        .precondition("User ready to begin " + journey)
                        .steps(steps)
                        .expectedResults(Arrays.asList(
                                "✅ Journey can be completed successfully",
                                "✅ Each step executes without errors",
                                "✅ User reaches intended destination",
                                "✅ System state is correct after journey",
                                "✅ No data loss or corruption"
                        ))
                        .priority("High")
                        .tags(Arrays.asList("journey", "user-flow", "e2e", "comprehensive"))
                        .build();

                tests.add(test);
            }
        }

        return tests;
    }

    private boolean isRelevantForJourney(InteractiveElement element, String journey) {
        String text = element.getText() != null ? element.getText().toLowerCase() : "";
        String elementType = element.getElementType().toLowerCase();

        switch (journey.toLowerCase()) {
            case "new user registration flow":
                return text.contains("sign up") || text.contains("register") ||
                        text.contains("create account") || elementType.contains("form");
            case "search and result selection":
                return text.contains("search") || elementType.contains("input") ||
                        elementType.contains("button");
            case "shopping cart to checkout":
                return text.contains("cart") || text.contains("checkout") ||
                        text.contains("buy") || text.contains("add to");
            case "content browsing and consumption":
                return elementType.contains("link") || elementType.contains("button") ||
                        text.contains("read") || text.contains("view");
            case "user profile update flow":
                return text.contains("profile") || text.contains("settings") ||
                        text.contains("account") || elementType.contains("form");
            default:
                return true;
        }
    }

    private List<TestCase> generateEdgeCaseTests(List<InteractiveElement> elements) {
        List<TestCase> tests = new ArrayList<>();

        // Invalid input tests for form elements
        List<InteractiveElement> formElements = elements.stream()
                .filter(e -> e.getActionType().equals("TYPE") || e.getActionType().equals("SELECT"))
                .limit(5)
                .collect(Collectors.toList());

        if (!formElements.isEmpty()) {
            TestCase invalidInputTest = TestCase.builder()
                    .id("EDGE-INVALID-" + UUID.randomUUID().toString().substring(0, 8))
                    .title("⚠️ Edge Case: Invalid Form Input")
                    .description("Tests form validation with deliberately invalid input")
                    .precondition("Form is present on page")
                    .steps(Arrays.asList(
                            "Navigate to form page",
                            "Enter invalid data (special characters, very long text, SQL injection attempts)",
                            "Attempt to submit form",
                            "Check for validation error messages",
                            "Verify form prevents submission"
                    ))
                    .expectedResults(Arrays.asList(
                            "✅ Form correctly rejects invalid input",
                            "✅ Clear error messages are displayed",
                            "✅ Submit button remains disabled or form doesn't submit",
                            "✅ No security vulnerabilities exposed",
                            "✅ User can correct errors and proceed"
                    ))
                    .priority("Medium")
                    .tags(Arrays.asList("edge-case", "validation", "security", "negative"))
                    .build();

            tests.add(invalidInputTest);
        }

        // Rapid interaction tests
        TestCase rapidTest = TestCase.builder()
                .id("EDGE-RAPID-" + UUID.randomUUID().toString().substring(0, 8))
                .title("⚡ Edge Case: Rapid Successive Clicks")
                .description("Tests UI resilience against rapid user interactions")
                .precondition("Page with interactive elements loaded")
                .steps(Arrays.asList(
                        "Select 3-5 interactive elements",
                        "Perform rapid successive clicks on each element",
                        "Observe system response",
                        "Check for UI freezes or crashes"
                ))
                .expectedResults(Arrays.asList(
                        "✅ System handles rapid interactions gracefully",
                        "✅ No UI freezes or unresponsiveness",
                        "✅ No duplicate actions triggered",
                        "✅ Console remains free of errors",
                        "✅ Page remains stable"
                ))
                .priority("Medium")
                .tags(Arrays.asList("edge-case", "performance", "stress", "ui"))
                .build();

        tests.add(rapidTest);

        return tests;
    }

    private List<TestCase> generatePerformanceTests(List<InteractiveElement> elements, String url) {
        List<TestCase> tests = new ArrayList<>();

        TestCase loadPerformance = TestCase.builder()
                .id("PERF-LOAD-" + UUID.randomUUID().toString().substring(0, 8))
                .title("⏱️ Performance: Page Load Time")
                .description("Tests page load performance and time to interactive")
                .precondition("Clean browser cache")
                .steps(Arrays.asList(
                        "Clear browser cache",
                        "Navigate to " + url,
                        "Start timer on navigation",
                        "Wait for page to be fully interactive",
                        "Stop timer and record load time"
                ))
                .expectedResults(Arrays.asList(
                        "✅ Page loads in under 3 seconds",
                        "✅ Time to interactive under 5 seconds",
                        "✅ No long-running tasks block interaction",
                        "✅ Resource loading optimized",
                        "✅ Performance metrics within acceptable range"
                ))
                .priority("High")
                .tags(Arrays.asList("performance", "load-time", "metrics", "optimization"))
                .build();

        tests.add(loadPerformance);

        TestCase interactionPerformance = TestCase.builder()
                .id("PERF-INTERACT-" + UUID.randomUUID().toString().substring(0, 8))
                .title("⚡ Performance: Interaction Response Time")
                .description("Tests response time for user interactions")
                .precondition("Page fully loaded")
                .steps(Arrays.asList(
                        "Select 5 key interactive elements",
                        "Measure click/tap response time for each",
                        "Record average response time",
                        "Check for jank or lag during interactions"
                ))
                .expectedResults(Arrays.asList(
                        "✅ Average response time under 100ms",
                        "✅ No perceptible lag during interactions",
                        "✅ Animations are smooth (60fps)",
                        "✅ Memory usage stable during interactions",
                        "✅ CPU usage within acceptable limits"
                ))
                .priority("Medium")
                .tags(Arrays.asList("performance", "responsiveness", "interaction", "metrics"))
                .build();

        tests.add(interactionPerformance);

        return tests;
    }

    private List<TestCase> generateAccessibilityTests(List<InteractiveElement> elements) {
        List<TestCase> tests = new ArrayList<>();

        TestCase keyboardAccessibility = TestCase.builder()
                .id("A11Y-KEYBOARD-" + UUID.randomUUID().toString().substring(0, 8))
                .title("♿ Accessibility: Keyboard Navigation")
                .description("Tests keyboard accessibility and tab order")
                .precondition("Page loaded, keyboard only")
                .steps(Arrays.asList(
                        "Use Tab key to navigate through interactive elements",
                        "Verify logical tab order",
                        "Use Enter/Space to activate elements",
                        "Check for keyboard traps",
                        "Verify focus indicators are visible"
                ))
                .expectedResults(Arrays.asList(
                        "✅ All interactive elements reachable via keyboard",
                        "✅ Logical tab order maintained",
                        "✅ Clear focus indicators visible",
                        "✅ No keyboard traps",
                        "✅ Screen reader announcements appropriate"
                ))
                .priority("High")
                .tags(Arrays.asList("accessibility", "keyboard", "a11y", "wcag"))
                .build();

        tests.add(keyboardAccessibility);

        TestCase screenReader = TestCase.builder()
                .id("A11Y-SCREENREADER-" + UUID.randomUUID().toString().substring(0, 8))
                .title("📢 Accessibility: Screen Reader Compatibility")
                .description("Tests screen reader compatibility and ARIA labels")
                .precondition("Screen reader enabled")
                .steps(Arrays.asList(
                        "Navigate page with screen reader",
                        "Verify meaningful element descriptions",
                        "Check ARIA labels and roles",
                        "Test form field announcements",
                        "Verify error message announcements"
                ))
                .expectedResults(Arrays.asList(
                        "✅ All elements have meaningful descriptions",
                        "✅ ARIA attributes correctly implemented",
                        "✅ Form fields properly labeled",
                        "✅ Error messages announced",
                        "✅ Screen reader navigation logical"
                ))
                .priority("Medium")
                .tags(Arrays.asList("accessibility", "screen-reader", "aria", "a11y"))
                .build();

        tests.add(screenReader);

        return tests;
    }

    private int calculateAITestsToEnhance(int totalTests, int maxAITests, boolean enableAI) {
        if (!enableAI) return 0;
        if (maxAITests <= 0) {
            // NO LIMIT requested - apply safety limit
            int safeLimit = Math.min(aiProperties.getMaxAITestsPerRequest(), totalTests);
            log.info("AI enhancement: UNLIMITED requested, using safety limit of {}", safeLimit);
            return safeLimit;
        }

        int result = Math.min(maxAITests, totalTests);
        result = Math.min(result, aiProperties.getMaxAITestsPerRequest()); // Apply safety cap
        log.info("AI enhancement: Enhancing {} of {} test cases (safety capped)", result, totalTests);
        return result;
    }

    private List<TestCase> enhanceMassiveTestSuiteWithAI(List<TestCase> testCases, String html,
                                                         int aiTestsToEnhance, GenerationSession session) {
        if (testCases.isEmpty() || aiTestsToEnhance <= 0) {
            return testCases;
        }

        log.info("🚀 Starting AI enhancement for {} test cases (session: {})",
                aiTestsToEnhance, session.sessionId);

        List<TestCase> enhancedTestCases = new ArrayList<>(testCases);

        // Select test cases to enhance (prioritize by importance)
        List<TestCase> toEnhance = selectTestCasesForAIEnhancement(testCases, aiTestsToEnhance);

        if (toEnhance.isEmpty()) {
            return enhancedTestCases;
        }

        // Process enhancements in massive parallel batches
        int batchSize = 10;
        List<List<TestCase>> batches = new ArrayList<>();

        for (int i = 0; i < toEnhance.size(); i += batchSize) {
            int end = Math.min(i + batchSize, toEnhance.size());
            batches.add(toEnhance.subList(i, end));
        }

        log.info("Processing AI enhancement in {} batches of up to {} tests each",
                batches.size(), batchSize);

        // Process each batch
        for (int batchNum = 0; batchNum < batches.size(); batchNum++) {
            if (session.cancelled) {
                log.info("Session {} cancelled, stopping AI enhancement", session.sessionId);
                session.addStatus("❌ AI enhancement cancelled by user");
                break;
            }

            List<TestCase> batch = batches.get(batchNum);
            session.addStatus(String.format("🤖 AI enhancing batch %d/%d (%d tests)...",
                    batchNum + 1, batches.size(), batch.size()));

            List<CompletableFuture<TestCase>> futures = new ArrayList<>();

            for (TestCase testCase : batch) {
                CompletableFuture<TestCase> future = CompletableFuture.supplyAsync(() -> {
                            try {
                                TestCase enhanced = enhanceTestCaseWithAI(testCase, html);
                                session.completedAITests++;

                                // Update progress every 5 completions
                                if (session.completedAITests % 5 == 0) {
                                    session.addStatus(String.format("✅ Enhanced %d/%d test cases",
                                            session.completedAITests, session.aiEnhancedCount));
                                }

                                return enhanced;
                            } catch (Exception e) {
                                log.warn("AI enhancement failed for {}: {}", testCase.getId(), e.getMessage());
                                session.completedAITests++;
                                return testCase;
                            }
                        }, aiExecutor)
                        .orTimeout(45, TimeUnit.SECONDS) // Longer timeout for complex enhancements
                        .exceptionally(ex -> {
                            log.warn("AI enhancement timeout/failed for {}: {}", testCase.getId(), ex.getMessage());
                            session.completedAITests++;
                            return testCase;
                        });

                futures.add(future);
            }

            // Wait for current batch to complete
            try {
                CompletableFuture<Void> batchFuture = CompletableFuture.allOf(
                        futures.toArray(new CompletableFuture[0])
                );

                // Calculate timeout for this batch
                int batchTimeout = Math.min(300, 60 + (batch.size() * 30));
                batchFuture.get(batchTimeout, TimeUnit.SECONDS);

                // Collect results
                List<TestCase> enhancedBatch = new ArrayList<>();
                for (CompletableFuture<TestCase> future : futures) {
                    try {
                        enhancedBatch.add(future.get());
                    } catch (Exception e) {
                        log.warn("Failed to get enhanced test case from batch", e);
                    }
                }

                // Update original test cases
                for (TestCase enhanced : enhancedBatch) {
                    for (int i = 0; i < enhancedTestCases.size(); i++) {
                        if (enhancedTestCases.get(i).getId().equals(enhanced.getId())) {
                            enhancedTestCases.set(i, enhanced);
                            break;
                        }
                    }
                }

                log.info("✅ Completed AI enhancement batch {}/{} ({} tests)",
                        batchNum + 1, batches.size(), enhancedBatch.size());

            } catch (TimeoutException e) {
                log.warn("Batch {} AI enhancement timeout, continuing with next batch", batchNum + 1);
                session.addStatus("⚠️ Batch " + (batchNum + 1) + " timeout, continuing...");
            } catch (Exception e) {
                log.error("Error during batch {} AI enhancement", batchNum + 1, e);
                session.addStatus("❌ Error in batch " + (batchNum + 1) + ": " + e.getMessage());
            }
        }

        int actuallyEnhanced = (int) enhancedTestCases.stream()
                .filter(tc -> tc.getTags() != null && tc.getTags().contains("ai-enhanced"))
                .count();

        session.addStatus(String.format("🎉 AI enhancement complete! Enhanced %d of %d requested tests",
                actuallyEnhanced, aiTestsToEnhance));

        log.info("✅ AI enhancement completed. Actually enhanced {} test cases", actuallyEnhanced);

        return enhancedTestCases;
    }

    // ========== EXISTING HELPER METHODS (keep these as they are) ==========

    private TestCase enhanceTestCaseWithAI(TestCase testCase, String html) {
        if (ollamaService == null || !ollamaService.isEnabled()) {
            return testCase;
        }

        log.info("Starting AI enhancement for test case: {}", testCase.getId());
        long startTime = System.currentTimeMillis();

        try {
            String prompt = buildEnhancementPrompt(testCase, html);
            log.debug("Prompt length: {} characters", prompt.length());

            // Use the enhanced method with timeout
            String aiResponse;
            try {
                aiResponse = ollamaService.generateWithTimeout(prompt, 30000); // 30 second timeout
            } catch (Exception e) {
                log.warn("AI enhancement timeout for test case {}: {}", testCase.getId(), e.getMessage());
                return testCase;
            }

            long duration = System.currentTimeMillis() - startTime;

            if (aiResponse == null || aiResponse.trim().isEmpty()) {
                log.warn("Empty AI response for test case {} after {}ms", testCase.getId(), duration);
                return testCase;
            }

            log.debug("AI response received in {}ms (length: {})", duration, aiResponse.length());

            TestCase enhanced = parseAIEnhancement(testCase, aiResponse);
            log.info("AI enhancement completed for {} in {}ms", testCase.getId(), duration);

            return enhanced;

        } catch (Exception e) {
            log.error("AI enhancement failed for test case {}: {}", testCase.getId(), e.getMessage(), e);
            return testCase;
        }
    }

    private String buildEnhancementPrompt(TestCase testCase, String html) {
        return String.format("""
            You are a senior QA automation expert. Enhance this test case to be production-ready.
            Focus on making it specific, actionable, and comprehensive.
            
            Respond ONLY in this exact format:
            
            ENHANCED_STEPS:
            1. [enhanced step 1 - make it specific and actionable with concrete values]
            2. [enhanced step 2]
            3. [enhanced step 3]
            4. [enhanced step 4 - include verification steps]
            5. [enhanced step 5 - include error handling if needed]
            
            ENHANCED_RESULTS:
            1. [specific, measurable, verifiable result 1]
            2. [specific, measurable, verifiable result 2]
            3. [specific, measurable, verifiable result 3]
            
            EDGE_CASES:
            - [relevant edge case 1 to consider]
            - [relevant edge case 2 to consider]
            - [relevant edge case 3 to consider]
            
            Original Test Case:
            Title: %s
            Description: %s
            Steps: %s
            Expected Results: %s
            Priority: %s
            
            Do not include any other text, explanations, or formatting.
            Be concise but thorough.
            """,
                testCase.getTitle(),
                testCase.getDescription() != null ? testCase.getDescription() : "",
                String.join("\n", testCase.getSteps()),
                String.join("\n", testCase.getExpectedResults()),
                testCase.getPriority()
        );
    }

    private TestCase parseAIEnhancement(TestCase original, String aiResponse) {
        List<String> enhancedSteps = new ArrayList<>(original.getSteps());
        List<String> enhancedResults = new ArrayList<>(original.getExpectedResults());
        List<String> edgeCases = new ArrayList<>();

        // Extract AI suggestions
        String[] sections = aiResponse.split("\n\n");

        for (String section : sections) {
            if (section.startsWith("ENHANCED_STEPS:")) {
                List<String> extractedSteps = extractNumberedList(section.substring("ENHANCED_STEPS:".length()));
                if (!extractedSteps.isEmpty()) {
                    // Replace or augment steps
                    enhancedSteps = extractedSteps;
                }
            } else if (section.startsWith("ENHANCED_RESULTS:")) {
                List<String> extractedResults = extractNumberedList(section.substring("ENHANCED_RESULTS:".length()));
                if (!extractedResults.isEmpty()) {
                    enhancedResults = extractedResults;
                }
            } else if (section.startsWith("EDGE_CASES:")) {
                edgeCases = extractBulletList(section.substring("EDGE_CASES:".length()));
            }
        }

        // Build enhanced test case
        StringBuilder enhancedDescription = new StringBuilder();
        if (original.getDescription() != null) {
            enhancedDescription.append(original.getDescription());
        }

        if (!edgeCases.isEmpty()) {
            enhancedDescription.append("\n\nAI-Suggested Considerations:\n");
            for (String edgeCase : edgeCases) {
                enhancedDescription.append("• ").append(edgeCase).append("\n");
            }
        }

        List<String> enhancedTags = new ArrayList<>(original.getTags() != null ? original.getTags() : new ArrayList<>());
        if (!enhancedTags.contains("ai-enhanced")) {
            enhancedTags.add("ai-enhanced");
        }

        return TestCase.builder()
                .id(original.getId())
                .title(original.getTitle() + " 🤖")
                .description(enhancedDescription.toString().trim())
                .precondition(original.getPrecondition())
                .steps(enhancedSteps)
                .expectedResults(enhancedResults)
                .priority(original.getPriority())
                .tags(enhancedTags)
                .build();
    }

    private List<String> extractNumberedList(String text) {
        return Arrays.stream(text.split("\n"))
                .map(String::trim)
                .filter(line -> line.matches("^\\d+[.)]\\s+.*"))
                .map(line -> line.replaceFirst("^\\d+[.)]\\s+", "").trim())
                .filter(item -> !item.isEmpty())
                .collect(Collectors.toList());
    }

    private List<String> extractBulletList(String text) {
        return Arrays.stream(text.split("\n"))
                .map(String::trim)
                .filter(line -> line.matches("^[-*•]\\s+.*"))
                .map(line -> line.replaceFirst("^[-*•]\\s+", "").trim())
                .filter(item -> !item.isEmpty())
                .collect(Collectors.toList());
    }

    private List<TestCase> selectTestCasesForAIEnhancement(List<TestCase> testCases, int max) {
        // Sort by priority and select top N
        return testCases.stream()
                .sorted((a, b) -> {
                    // Priority order: Critical > High > Medium > Low > Info
                    Map<String, Integer> priorityOrder = Map.of(
                            "Critical", 5,
                            "High", 4,
                            "Medium", 3,
                            "Low", 2,
                            "Info", 1
                    );
                    int priorityA = priorityOrder.getOrDefault(a.getPriority(), 0);
                    int priorityB = priorityOrder.getOrDefault(b.getPriority(), 0);
                    return Integer.compare(priorityB, priorityA);
                })
                .limit(max)
                .collect(Collectors.toList());
    }

    // ========== PROGRESS TRACKING METHODS ==========

    public Map<String, Object> getGenerationProgress(String sessionId) {
        GenerationSession session = activeSessions.get(sessionId);
        if (session != null) {
            return session.getProgress();
        }
        return Collections.emptyMap();
    }

    public boolean cancelGeneration(String sessionId) {
        GenerationSession session = activeSessions.get(sessionId);
        if (session != null) {
            session.cancelled = true;
            session.addStatus("⏹️ Generation cancelled by user");
            log.info("Cancelled generation session: {}", sessionId);
            return true;
        }
        return false;
    }

    public List<Map<String, Object>> getActiveSessions() {
        return activeSessions.values().stream()
                .map(GenerationSession::getProgress)
                .collect(Collectors.toList());
    }

    public void clearCache() {
        testCaseCache.clear();
        log.info("Test case cache cleared");
    }

    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("size", testCaseCache.size());
        stats.put("totalTestCases", testCaseCache.values().stream()
                .mapToInt(List::size)
                .sum());
        stats.put("averagePerUrl", testCaseCache.values().stream()
                .mapToInt(List::size)
                .average()
                .orElse(0.0));
        return stats;
    }
}