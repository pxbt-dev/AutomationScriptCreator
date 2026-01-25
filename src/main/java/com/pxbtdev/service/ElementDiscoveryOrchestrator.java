package com.pxbtdev.service;

import com.pxbtdev.model.entity.InteractiveElement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ElementDiscoveryOrchestrator {

    private final ElementDiscoveryService elementDiscoveryService;
    private final MLElementDiscoveryService mlDiscoveryService;
    private final SimpleOllamaService ollamaService;

    public List<InteractiveElement> discoverInteractiveElements(String html, String url) {
        log.info("Starting interactive element discovery for URL: {}", url);

        // Use basic element discovery
        List<InteractiveElement> elements = elementDiscoveryService.discoverInteractiveElements(html, url);

        // Apply ML scoring to enhance priority
        elements = applyMLScoring(elements);

        log.info("Discovered {} interactive elements for URL: {}", elements.size(), url);
        return elements;
    }

    public List<InteractiveElement> discoverInteractiveElementsWithML(String html, String url) {
        log.info("Starting ML-enhanced element discovery for URL: {}", url);

        List<InteractiveElement> elements = elementDiscoveryService.discoverInteractiveElements(html, url);

        // Apply ML scoring
        elements = applyMLScoring(elements);

        // Use AI to enhance element descriptions if available
        if (ollamaService != null && ollamaService.isEnabled()) {
            elements = enhanceWithAI(elements, html);
        }

        log.info("ML-enhanced discovery found {} elements for URL: {}", elements.size(), url);
        return elements;
    }

    private List<InteractiveElement> applyMLScoring(List<InteractiveElement> elements) {
        if (elements.isEmpty()) {
            return elements;
        }

        // Score each element using ML
        elements.forEach(element -> {
            try {
                double mlScore = mlDiscoveryService.calculateElementImportance(element);
                // Adjust priority: combine original priority (0-100) with ML score (0-1)
                int newPriority = (int) (element.getPriority() * (1.0 + mlScore));
                element.setPriority(Math.min(100, newPriority)); // Cap at 100

                // Add ML confidence to description
                if (element.getDescription() == null) {
                    element.setDescription(String.format("ML confidence: %.2f", mlScore));
                } else {
                    element.setDescription(element.getDescription() +
                            String.format(" | ML confidence: %.2f", mlScore));
                }
            } catch (Exception e) {
                log.warn("Failed to calculate ML score for element: {}", element.getSelector(), e);
                // Keep original priority if ML fails
            }
        });

        // Sort by enhanced priority
        elements.sort((e1, e2) -> Integer.compare(e2.getPriority(), e1.getPriority()));

        return elements;
    }

    private List<InteractiveElement> enhanceWithAI(List<InteractiveElement> elements, String html) {
        if (!ollamaService.isEnabled() || elements.isEmpty()) {
            return elements;
        }

        log.info("Enhancing {} elements with AI", elements.size());

        // For production, you might want to batch elements or use async processing
        elements.forEach(element -> {
            try {
                String aiDescription = generateAIDescription(element, html);
                if (aiDescription != null && !aiDescription.isEmpty()) {
                    element.setDescription(aiDescription);
                }
            } catch (Exception e) {
                log.warn("AI enhancement failed for element {}: {}",
                        element.getSelector(), e.getMessage());
            }
        });

        return elements;
    }

    private String generateAIDescription(InteractiveElement element, String html) {
        // Create a prompt for AI to describe the element
        String prompt = String.format("""
            Describe this web element for test automation purposes.
            
            Element Details:
            - Type: %s
            - Action: %s
            - Selector: %s
            - Text: %s
            - Tag: %s
            - ID: %s
            - Classes: %s
            
            Provide a brief description of what this element does and how it should be tested.
            Respond in one sentence.
            """,
                element.getElementType(),
                element.getActionType(),
                element.getSelector(),
                element.getText() != null ? element.getText() : "(no text)",
                element.getTagName(),
                element.getId() != null ? element.getId() : "(no id)",
                element.getClasses() != null ? element.getClasses() : "(no classes)"
        );

        return ollamaService.generate(prompt);
    }

    // Method to get top N most important elements
    public List<InteractiveElement> getTopElements(String html, String url, int limit) {
        List<InteractiveElement> elements = discoverInteractiveElementsWithML(html, url);

        if (elements.size() > limit) {
            return elements.subList(0, Math.min(limit, elements.size()));
        }

        return elements;
    }

    // Method to filter elements by minimum priority
    public List<InteractiveElement> filterByPriority(String html, String url, int minPriority) {
        List<InteractiveElement> elements = discoverInteractiveElementsWithML(html, url);

        return elements.stream()
                .filter(e -> e.getPriority() >= minPriority)
                .toList();
    }
}