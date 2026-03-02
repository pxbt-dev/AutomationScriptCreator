package com.pxbtdev.model.entity;

import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TestCase {

    private String id;
    private String title;
    private String description;
    private String precondition;
    private List<String> steps;
    private List<String> expectedResults;
    private String priority; // High, Medium, Low
    private List<String> tags;
    private boolean aiEnhanced;
    private String targetUrl;
    private String elementSelector;
    private String elementType;

    public String toHumanReadableFormat() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== ").append(title).append(" ===\n");
        sb.append("ID: ").append(id).append("\n");
        sb.append("Priority: ").append(priority).append("\n");
        if (description != null)
            sb.append("Description: ").append(description).append("\n");
        if (precondition != null)
            sb.append("Precondition: ").append(precondition).append("\n");

        if (steps != null && !steps.isEmpty()) {
            sb.append("\nSteps:\n");
            for (int i = 0; i < steps.size(); i++) {
                sb.append("  ").append(i + 1).append(". ").append(steps.get(i)).append("\n");
            }
        }

        if (expectedResults != null && !expectedResults.isEmpty()) {
            sb.append("\nExpected Results:\n");
            for (String result : expectedResults) {
                sb.append("  ✓ ").append(result).append("\n");
            }
        }

        if (tags != null && !tags.isEmpty()) {
            sb.append("Tags: ").append(String.join(", ", tags)).append("\n");
        }
        return sb.toString();
    }

    public String toPlaywrightStep() {
        if (steps == null || steps.isEmpty())
            return "  // No steps defined";
        StringBuilder sb = new StringBuilder();
        for (String step : steps) {
            sb.append("  // ").append(step).append("\n");
            sb.append(convertStepToPlaywright(step)).append("\n");
        }
        return sb.toString();
    }

    private String convertStepToPlaywright(String step) {
        String s = step.toLowerCase();
        if (s.contains("navigate to") || s.contains("go to") || s.contains("open")) {
            String url = extractUrl(step);
            return url != null ? "  await page.goto('" + url + "');" : "  await page.reload();";
        }
        if (s.contains("click") && elementSelector != null) {
            return "  await page.click('" + elementSelector + "');";
        }
        if ((s.contains("type") || s.contains("enter") || s.contains("fill")) && elementSelector != null) {
            return "  await page.fill('" + elementSelector + "', 'test value');";
        }
        if (s.contains("wait")) {
            return "  await page.waitForLoadState('networkidle');";
        }
        if (s.contains("verify") || s.contains("check") || s.contains("assert")) {
            return "  await expect(page).toHaveTitle(/.+/);";
        }
        return "  // " + step;
    }

    private String extractUrl(String text) {
        int idx = text.indexOf("http");
        if (idx >= 0) {
            String[] parts = text.substring(idx).split("\\s+");
            return parts[0];
        }
        return null;
    }
}