package com.pxbtdev.service;

import com.pxbtdev.model.entity.InteractiveElement;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class MLElementDiscoveryService {

    // Calculate importance score for an InteractiveElement
    public double calculateElementImportance(InteractiveElement interactiveElement) {
        // Convert to Jsoup element for analysis
        String htmlSnippet = createHtmlSnippet(interactiveElement);
        Element element = Jsoup.parse(htmlSnippet).body().child(0);

        return calculateElementImportance(element, interactiveElement);
    }

    // Original method signature for compatibility
    public double calculateElementImportance(Element element) {
        Map<String, Double> features = extractFeatures(element);
        return predictImportance(features);
    }

    // Overloaded method for InteractiveElement
    private double calculateElementImportance(Element element, InteractiveElement interactiveElement) {
        Map<String, Double> features = extractFeatures(element);

        // Add InteractiveElement specific features
        features.put("priority", (double) interactiveElement.getPriority());
        features.put("hasText", interactiveElement.getText() != null && !interactiveElement.getText().isEmpty() ? 1.0 : 0.0);
        features.put("actionTypeWeight", getActionTypeWeight(interactiveElement.getActionType()));

        return predictImportance(features);
    }

    private Map<String, Double> extractFeatures(Element element) {
        Map<String, Double> features = new HashMap<>();

        // Text features
        features.put("hasText", element.hasText() ? 1.0 : 0.0);
        features.put("textLength", (double) element.text().length());

        // Structural features
        features.put("isButton", element.tagName().equals("button") ? 1.0 : 0.0);
        features.put("isInput", element.tagName().equals("input") ? 1.0 : 0.0);
        features.put("isLink", element.tagName().equals("a") ? 1.0 : 0.0);
        features.put("isForm", element.tagName().equals("form") ? 1.0 : 0.0);
        features.put("isSelect", element.tagName().equals("select") ? 1.0 : 0.0);

        // Attribute features
        features.put("hasId", !element.id().isEmpty() ? 1.0 : 0.0);
        features.put("hasClass", !element.className().isEmpty() ? 1.0 : 0.0);
        features.put("hasOnClick", element.hasAttr("onclick") ? 1.0 : 0.0);
        features.put("hasName", element.hasAttr("name") ? 1.0 : 0.0);
        features.put("hasPlaceholder", element.hasAttr("placeholder") ? 1.0 : 0.0);

        // Accessibility features
        features.put("hasAriaLabel", element.hasAttr("aria-label") ? 1.0 : 0.0);
        features.put("hasRole", element.hasAttr("role") ? 1.0 : 0.0);
        features.put("hasAlt", element.hasAttr("alt") ? 1.0 : 0.0);
        features.put("hasTitle", element.hasAttr("title") ? 1.0 : 0.0);

        // Visibility/Interaction features
        features.put("hasType", element.hasAttr("type") ? 1.0 : 0.0);
        features.put("isRequired", element.hasAttr("required") ? 1.0 : 0.0);
        features.put("isDisabled", element.hasAttr("disabled") ? 0.0 : 1.0); // Inverted - disabled is less important

        // Data attributes (often used for testing)
        features.put("hasDataTestId", element.hasAttr("data-testid") ? 1.0 : 0.0);
        features.put("hasDataQa", element.hasAttr("data-qa") ? 1.0 : 0.0);
        features.put("hasDataCy", element.hasAttr("data-cy") ? 1.0 : 0.0);

        // Position/Size estimation
        features.put("isVisible", estimateVisibility(element));

        return features;
    }

    private double predictImportance(Map<String, Double> features) {
        // Simple weighted scoring model (in production, you'd use a trained ML model)
        double score = 0.0;

        // Weights for different features
        score += features.getOrDefault("isButton", 0.0) * 0.3;
        score += features.getOrDefault("isInput", 0.0) * 0.25;
        score += features.getOrDefault("isLink", 0.0) * 0.15;
        score += features.getOrDefault("isForm", 0.0) * 0.4;
        score += features.getOrDefault("isSelect", 0.0) * 0.2;

        score += features.getOrDefault("hasId", 0.0) * 0.1;
        score += features.getOrDefault("hasDataTestId", 0.0) * 0.25;
        score += features.getOrDefault("hasDataQa", 0.0) * 0.2;
        score += features.getOrDefault("hasDataCy", 0.0) * 0.2;

        score += features.getOrDefault("hasAriaLabel", 0.0) * 0.15;
        score += features.getOrDefault("hasText", 0.0) * 0.1;
        score += features.getOrDefault("isRequired", 0.0) * 0.2;

        score += features.getOrDefault("isVisible", 0.0) * 0.25;
        score += features.getOrDefault("hasOnClick", 0.0) * 0.15;

        // Normalize score to 0-1 range
        return Math.min(1.0, Math.max(0.0, score));
    }

    private double estimateVisibility(Element element) {
        // Simple heuristic for visibility
        double visibility = 0.5; // Base visibility

        // Check for common hidden styles
        String style = element.attr("style").toLowerCase();
        if (style.contains("display:none") || style.contains("visibility:hidden")) {
            return 0.0;
        }

        // Check for hidden attribute
        if (element.hasAttr("hidden") || element.hasAttr("aria-hidden")) {
            return 0.0;
        }

        // Check for common hidden classes
        String classes = element.className().toLowerCase();
        if (classes.contains("hidden") || classes.contains("invisible") ||
                classes.contains("d-none") || classes.contains("hide")) {
            return 0.1;
        }

        // Common visible indicators
        if (classes.contains("btn") || classes.contains("button") ||
                classes.contains("input") || classes.contains("form-control")) {
            visibility = 0.9;
        }

        return visibility;
    }

    private String createHtmlSnippet(InteractiveElement interactiveElement) {
        // Create a minimal HTML snippet for the element
        StringBuilder html = new StringBuilder();
        html.append("<").append(interactiveElement.getTagName());

        if (interactiveElement.getId() != null && !interactiveElement.getId().isEmpty()) {
            html.append(" id=\"").append(interactiveElement.getId()).append("\"");
        }

        if (interactiveElement.getClasses() != null && !interactiveElement.getClasses().isEmpty()) {
            html.append(" class=\"").append(interactiveElement.getClasses()).append("\"");
        }

        // Add common attributes based on element type
        if ("input".equalsIgnoreCase(interactiveElement.getTagName())) {
            html.append(" type=\"text\"");
        }

        if (interactiveElement.getText() != null && !interactiveElement.getText().isEmpty()) {
            html.append(">").append(interactiveElement.getText()).append("</").append(interactiveElement.getTagName()).append(">");
        } else {
            html.append("></").append(interactiveElement.getTagName()).append(">");
        }

        return html.toString();
    }

    private double getActionTypeWeight(String actionType) {
        if (actionType == null) return 0.5;

        switch (actionType.toUpperCase()) {
            case "CLICK":
                return 1.0;
            case "TYPE":
                return 0.9;
            case "SELECT":
                return 0.8;
            case "SUBMIT":
                return 1.0;
            case "NAVIGATE":
                return 0.7;
            case "HOVER":
                return 0.6;
            case "SCROLL":
                return 0.4;
            default:
                return 0.5;
        }
    }

    // Additional method to score a list of elements
    public Map<String, Double> scoreElements(List<InteractiveElement> elements) {
        Map<String, Double> scores = new HashMap<>();

        for (InteractiveElement element : elements) {
            double score = calculateElementImportance(element);
            scores.put(element.getSelector(), score);
        }

        return scores;
    }

    // Method to rank elements by importance
    public List<InteractiveElement> rankByImportance(List<InteractiveElement> elements) {
        elements.sort((e1, e2) -> {
            double score1 = calculateElementImportance(e1);
            double score2 = calculateElementImportance(e2);
            return Double.compare(score2, score1); // Descending order
        });

        return elements;
    }
}