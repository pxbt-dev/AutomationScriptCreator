package com.pxbtdev.service;

import com.pxbtdev.model.entity.TestCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Executes generated API tests by making real HTTP calls.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiTestRunnerService {

    private final RestTemplate restTemplate;

    /**
     * Run a list of API test cases and return results.
     */
    public List<Map<String, Object>> runTests(List<TestCase> testCases, String authToken) {
        List<Map<String, Object>> results = new ArrayList<>();
        for (TestCase tc : testCases) {
            results.add(runSingleTest(tc, authToken));
        }
        return results;
    }

    public Map<String, Object> runSingleTest(TestCase tc, String authToken) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("testId", tc.getId());
        result.put("title", tc.getTitle());

        if (tc.getTargetUrl() == null || tc.getTargetUrl().isBlank()) {
            result.put("status", "SKIPPED");
            result.put("message", "No URL defined for this test");
            return result;
        }

        long start = System.currentTimeMillis();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");
            headers.set("Accept", "application/json");
            if (authToken != null && !authToken.isBlank()) {
                headers.set("Authorization", authToken.startsWith("Bearer ") ? authToken : "Bearer " + authToken);
            }

            HttpEntity<String> entity = new HttpEntity<>(null, headers);

            // Determine method from tags
            String method = "GET";
            if (tc.getTags() != null) {
                if (tc.getTags().contains("post"))
                    method = "POST";
                else if (tc.getTags().contains("put"))
                    method = "PUT";
                else if (tc.getTags().contains("delete"))
                    method = "DELETE";
                else if (tc.getTags().contains("patch"))
                    method = "PATCH";
            }

            ResponseEntity<String> response = restTemplate.exchange(
                    tc.getTargetUrl(),
                    HttpMethod.valueOf(method),
                    entity,
                    String.class);

            long elapsed = System.currentTimeMillis() - start;
            int statusCode = response.getStatusCode().value();

            // Determine pass/fail
            boolean isNegativeTest = tc.getTags() != null &&
                    (tc.getTags().contains("negative-test") || tc.getTags().contains("auth"));

            boolean passed;
            if (isNegativeTest) {
                passed = statusCode == 400 || statusCode == 401 || statusCode == 403 || statusCode == 404;
            } else {
                passed = statusCode >= 200 && statusCode < 300;
            }

            result.put("status", passed ? "PASSED" : "FAILED");
            result.put("httpStatus", statusCode);
            result.put("responseTime", elapsed + "ms");
            result.put("passed", passed);
            result.put("responsePreview", truncate(response.getBody(), 300));

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            long elapsed = System.currentTimeMillis() - start;
            int statusCode = e.getStatusCode().value();
            boolean isNegativeTest = tc.getTags() != null &&
                    (tc.getTags().contains("negative-test") || tc.getTags().contains("auth"));
            boolean passed = isNegativeTest && (statusCode == 400 || statusCode == 401 || statusCode == 403);

            result.put("status", passed ? "PASSED" : "FAILED");
            result.put("httpStatus", statusCode);
            result.put("responseTime", elapsed + "ms");
            result.put("passed", passed);
            result.put("error", e.getMessage());
        } catch (Exception e) {
            result.put("status", "ERROR");
            result.put("passed", false);
            result.put("error", e.getMessage());
            result.put("responseTime", (System.currentTimeMillis() - start) + "ms");
        }

        return result;
    }

    private String truncate(String s, int max) {
        if (s == null)
            return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
