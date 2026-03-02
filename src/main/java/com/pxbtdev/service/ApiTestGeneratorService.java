package com.pxbtdev.service;

import com.pxbtdev.model.entity.TestCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates API test cases from a parsed Swagger/OpenAPI spec.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiTestGeneratorService {

    private final OllamaService ollamaService;

    @SuppressWarnings("unchecked")
    public List<TestCase> generateFromSpec(Map<String, Object> spec, boolean useAI) {
        List<TestCase> testCases = new ArrayList<>();
        List<Map<String, Object>> endpoints = (List<Map<String, Object>>) spec.getOrDefault("endpoints", List.of());
        String baseUrl = (String) spec.getOrDefault("baseUrl", "");
        String title = (String) spec.getOrDefault("title", "API");

        log.info("Generating API tests for {} endpoints", endpoints.size());

        // 1. Generate per-endpoint tests
        for (Map<String, Object> endpoint : endpoints) {
            testCases.addAll(generateEndpointTests(endpoint, baseUrl, title));
        }

        // 2. Auth tests
        testCases.add(generateAuthTest(baseUrl, title));

        // 3. Rate limit / error tests
        testCases.add(generateErrorHandlingTest(baseUrl, title));

        // 4. AI-enhanced tests if available
        if (useAI && ollamaService.isAvailable()) {
            List<TestCase> aiTests = generateAIApiTests(spec, endpoints);
            testCases.addAll(aiTests);
            log.info("AI added {} additional API tests", aiTests.size());
        }

        return testCases;
    }

    @SuppressWarnings("unchecked")
    private List<TestCase> generateEndpointTests(Map<String, Object> endpoint, String baseUrl, String apiTitle) {
        List<TestCase> tests = new ArrayList<>();
        String method = (String) endpoint.get("method");
        String path = (String) endpoint.get("path");
        String operationId = (String) endpoint.get("operationId");
        String summary = (String) endpoint.getOrDefault("summary", operationId);
        List<Map<String, Object>> params = (List<Map<String, Object>>) endpoint.getOrDefault("parameters", List.of());
        boolean deprecated = Boolean.TRUE.equals(endpoint.get("deprecated"));

        String fullUrl = baseUrl + path;

        // Happy path test
        List<String> happySteps = new ArrayList<>();
        happySteps.add("Send " + method + " request to: " + fullUrl);

        // Add required parameters
        List<Map<String, Object>> requiredParams = params.stream()
                .filter(p -> Boolean.TRUE.equals(p.get("required")))
                .collect(Collectors.toList());

        for (Map<String, Object> param : requiredParams) {
            String paramIn = (String) param.getOrDefault("in", "query");
            String paramName = (String) param.get("name");
            String paramType = (String) param.getOrDefault("type", "string");
            String testValue = getTestValue(paramName, paramType);

            if ("header".equals(paramIn)) {
                happySteps.add("Set header '" + paramName + "': '" + testValue + "'");
            } else if ("path".equals(paramIn)) {
                happySteps.add("Set path parameter '" + paramName + "': '" + testValue + "'");
            } else if ("query".equals(paramIn)) {
                happySteps.add("Set query parameter '" + paramName + "': '" + testValue + "'");
            } else if ("formData".equals(paramIn) || "body".equals(paramIn)) {
                happySteps.add("Set form/body field '" + paramName + "': '" + testValue + "'");
            }
        }
        happySteps.add("Verify response status code is 200 or 2xx");
        happySteps.add("Verify response body is valid JSON");

        List<String> happyExpected = List.of(
                "HTTP 2xx response returned",
                "Response body is valid JSON",
                "Response time < 2000ms",
                "No error fields in response body");

        List<String> tags = new ArrayList<>(List.of("api-test", method.toLowerCase(), "happy-path"));
        if (deprecated)
            tags.add("deprecated");

        tests.add(TestCase.builder()
                .id("API-" + method + "-" + shortId())
                .title("[" + method + "] " + (summary != null ? summary : path) + " - Happy Path")
                .description("Tests successful " + method + " call to " + path)
                .precondition("Valid authentication token available. API server running at " + baseUrl)
                .steps(happySteps)
                .expectedResults(happyExpected)
                .priority(getPriority(method))
                .tags(tags)
                .targetUrl(fullUrl)
                .build());

        // Missing required params test (negative)
        if (!requiredParams.isEmpty()) {
            tests.add(TestCase.builder()
                    .id("API-" + method + "-NEG-" + shortId())
                    .title("[" + method + "] " + (summary != null ? summary : path) + " - Missing Required Params")
                    .description("Tests that API returns 400 when required parameters are missing")
                    .precondition("API server running at " + baseUrl)
                    .steps(List.of(
                            "Send " + method + " request to: " + fullUrl,
                            "Omit all required parameters",
                            "Verify response"))
                    .expectedResults(List.of(
                            "HTTP 400 Bad Request returned",
                            "Error message explains which fields are missing",
                            "Response body contains error details"))
                    .priority("High")
                    .tags(List.of("api-test", method.toLowerCase(), "negative-test", "validation"))
                    .targetUrl(fullUrl)
                    .build());
        }

        // Auth test if Authorization header is a parameter
        boolean hasAuthParam = params.stream()
                .anyMatch(p -> "Authorization".equals(p.get("name")));
        if (hasAuthParam) {
            tests.add(TestCase.builder()
                    .id("API-AUTH-" + shortId())
                    .title("[" + method + "] " + (summary != null ? summary : path) + " - Unauthorised Access")
                    .description("Tests that API rejects requests without valid auth token")
                    .precondition("API server running at " + baseUrl)
                    .steps(List.of(
                            "Send " + method + " request to: " + fullUrl,
                            "Omit the Authorization header",
                            "Verify response"))
                    .expectedResults(List.of(
                            "HTTP 401 Unauthorized returned",
                            "Response body contains auth error message"))
                    .priority("High")
                    .tags(List.of("api-test", "security", "auth"))
                    .targetUrl(fullUrl)
                    .build());
        }

        return tests;
    }

    private TestCase generateAuthTest(String baseUrl, String apiTitle) {
        return TestCase.builder()
                .id("API-AUTH-GLOBAL-" + shortId())
                .title(apiTitle + " - Global Auth Verification")
                .description("Verifies authentication is enforced across all protected endpoints")
                .precondition("API server is running at " + baseUrl)
                .steps(List.of(
                        "Send request to a protected endpoint without any Authorization header",
                        "Verify 401 response",
                        "Send request with invalid/expired token",
                        "Verify 401 or 403 response",
                        "Send request with valid token",
                        "Verify 200 response"))
                .expectedResults(List.of(
                        "Unauthenticated requests return 401",
                        "Invalid token requests return 401 or 403",
                        "Valid token requests return 200",
                        "Token expiry is handled gracefully"))
                .priority("High")
                .tags(List.of("api-test", "security", "auth", "regression"))
                .build();
    }

    private TestCase generateErrorHandlingTest(String baseUrl, String apiTitle) {
        return TestCase.builder()
                .id("API-ERR-" + shortId())
                .title(apiTitle + " - Error Response Format")
                .description("Verifies API error responses are consistent and well-formed")
                .precondition("API server is running at " + baseUrl)
                .steps(List.of(
                        "Send request to a non-existent endpoint: " + baseUrl + "/api/non-existent",
                        "Verify 404 response",
                        "Send request with invalid Content-Type header",
                        "Send request with malformed JSON body to a POST endpoint",
                        "Verify error responses contain consistent structure"))
                .expectedResults(List.of(
                        "404 returned for unknown endpoints",
                        "415 or 400 returned for wrong Content-Type",
                        "400 returned for malformed JSON",
                        "All error responses have consistent JSON structure",
                        "Error messages are human-readable"))
                .priority("Medium")
                .tags(List.of("api-test", "error-handling", "regression"))
                .build();
    }

    @SuppressWarnings("unchecked")
    private List<TestCase> generateAIApiTests(Map<String, Object> spec, List<Map<String, Object>> endpoints) {
        List<TestCase> result = new ArrayList<>();
        try {
            String endpointSummary = endpoints.stream()
                    .limit(10)
                    .map(ep -> "- " + ep.get("method") + " " + ep.get("path") + ": " + ep.getOrDefault("summary", ""))
                    .collect(Collectors.joining("\n"));

            String systemPrompt = """
                    You are an expert API QA engineer. Generate additional API test cases for security, edge cases, and integration scenarios.
                    Output ONLY a valid JSON array. Each element has: title, description, precondition, steps (array), expectedResults (array), priority (High/Medium/Low), tags (array).
                    No text before or after the JSON.
                    """;

            String userPrompt = """
                    API: %s v%s
                    Base URL: %s

                    Endpoints:
                    %s

                    Generate 3-4 additional test cases covering:
                    1. Data injection attacks (SQL injection, XSS via API)
                    2. Large payload / stress testing
                    3. Concurrent request handling
                    4. API versioning consistency

                    Output as JSON array only.
                    """.formatted(
                    spec.getOrDefault("title", "API"),
                    spec.getOrDefault("version", "1.0"),
                    spec.getOrDefault("baseUrl", ""),
                    endpointSummary);

            String response = ollamaService.chat(systemPrompt, userPrompt);
            if (response != null && !response.isBlank()) {
                result = parseAITests(response);
            }
        } catch (Exception e) {
            log.warn("AI API test generation failed: {}", e.getMessage());
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<TestCase> parseAITests(String json) {
        List<TestCase> result = new ArrayList<>();
        try {
            int start = json.indexOf('[');
            int end = json.lastIndexOf(']');
            if (start < 0 || end < 0)
                return result;

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            List<Map<String, Object>> cases = mapper.readValue(json.substring(start, end + 1),
                    mapper.getTypeFactory().constructCollectionType(List.class, Map.class));

            for (Map<String, Object> tc : cases) {
                List<String> tags = toList(tc.get("tags"));
                tags.add("ai-enhanced");
                result.add(TestCase.builder()
                        .id("API-AI-" + shortId())
                        .title(str(tc, "title"))
                        .description(str(tc, "description"))
                        .precondition(str(tc, "precondition"))
                        .steps(toList(tc.get("steps")))
                        .expectedResults(toList(tc.get("expectedResults")))
                        .priority(str(tc, "priority"))
                        .tags(tags)
                        .aiEnhanced(true)
                        .build());
            }
        } catch (Exception e) {
            log.warn("Failed to parse AI API test cases: {}", e.getMessage());
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

    private String getPriority(String method) {
        return switch (method) {
            case "DELETE" -> "High";
            case "POST", "PUT", "PATCH" -> "High";
            case "GET" -> "Medium";
            default -> "Low";
        };
    }

    private String getTestValue(String name, String type) {
        String n = name.toLowerCase();
        if (n.contains("id") || n.contains("customer"))
            return "12345";
        if (n.contains("email"))
            return "test@example.com";
        if (n.contains("name"))
            return "Test User";
        if (n.contains("token") || n.contains("auth"))
            return "Bearer test-token-here";
        if ("integer".equals(type) || "number".equals(type))
            return "1";
        return "test-value";
    }

    private String shortId() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
