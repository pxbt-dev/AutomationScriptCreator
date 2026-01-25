package com.pxbtdev.service;

import com.pxbtdev.model.entity.TestCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class AITestEnhancerService {

    private final ChatClient chatClient;
    private final boolean aiEnabled;

    // Inject the "chatClient" bean by name
    @Autowired(required = false)
    public AITestEnhancerService(
            @Qualifier("chatClient") ChatClient chatClient,
            @Value("${ai.enabled:true}") boolean aiEnabled) {
        this.chatClient = chatClient;
        this.aiEnabled = aiEnabled && chatClient != null;
        log.info("AITestEnhancerService initialized. AI enabled: {}", this.aiEnabled);
    }

    // Fallback constructor
    public AITestEnhancerService(@Value("${ai.enabled:true}") boolean aiEnabled) {
        this.chatClient = null;
        this.aiEnabled = false;
        log.info("AITestEnhancerService initialized without ChatClient");
    }

    public TestCase enhanceWithAI(TestCase testCase, String htmlSnippet) {
        if (!aiEnabled || chatClient == null) {
            log.debug("AI enhancement disabled or ChatClient not available");
            return testCase;
        }

        log.info("Enhancing test case {} with AI", testCase.getId());

        try {
            String prompt = createStrictPrompt(testCase, htmlSnippet);
            log.debug("Sending prompt to AI (length: {})", prompt.length());

            String aiResponse = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            log.debug("AI Response received (first 200 chars): {}",
                    aiResponse.substring(0, Math.min(200, aiResponse.length())));

            TestCase enhanced = parseAIResponse(testCase, aiResponse);
            log.info("Successfully enhanced test case {}", testCase.getId());
            return enhanced;

        } catch (Exception e) {
            log.error("AI enhancement failed for test case {}: {}", testCase.getId(), e.getMessage());

            // Return original with AI failure note
            return TestCase.builder()
                    .id(testCase.getId())
                    .title(testCase.getTitle() + " (AI Enhancement Failed)")
                    .description(testCase.getDescription() +
                            "\n\nAI Enhancement Failed: " + e.getMessage())
                    .precondition(testCase.getPrecondition())
                    .steps(testCase.getSteps())
                    .expectedResults(testCase.getExpectedResults())
                    .priority(testCase.getPriority())
                    .tags(addTag(testCase.getTags(), "ai-failed"))
                    .build();
        }
    }

    private String createStrictPrompt(TestCase testCase, String htmlSnippet) {
        return String.format("""
            ENHANCE THIS TEST CASE. RESPOND ONLY IN THE SPECIFIED FORMAT.
            
            INPUT TEST CASE:
            TITLE: %s
            DESCRIPTION: %s
            STEPS:
            %s
            EXPECTED RESULTS:
            %s
            
            HTML CONTEXT (first 1000 characters):
            %s
            
            GENERATE ENHANCEMENTS. RESPONSE MUST BE EXACTLY:
            ENHANCED_STEPS:
            1. [enhanced step 1 - be specific and actionable]
            2. [enhanced step 2]
            3. [enhanced step 3]
            
            ENHANCED_RESULTS:
            1. [specific verifiable result 1]
            2. [specific verifiable result 2]
            3. [specific verifiable result 3]
            
            EDGE_CASES:
            - [practical edge case 1]
            - [practical edge case 2]
            
            IMPORTANT: Do NOT add any other text, explanations, markdown, or formatting.
            """,
                testCase.getTitle(),
                testCase.getDescription(),
                String.join("\n", testCase.getSteps()),
                String.join("\n", testCase.getExpectedResults()),
                htmlSnippet.substring(0, Math.min(1000, htmlSnippet.length()))
        );
    }

    private TestCase parseAIResponse(TestCase original, String aiResponse) {
        // Clean up the response first
        String cleanedResponse = cleanAIResponse(aiResponse);
        log.debug("Cleaned AI response: {}", cleanedResponse);

        // Extract sections
        List<String> enhancedSteps = extractEnhancedSteps(cleanedResponse, original.getSteps());
        List<String> enhancedExpected = extractEnhancedResults(cleanedResponse, original.getExpectedResults());
        List<String> edgeCases = extractEdgeCases(cleanedResponse);

        // Build enhanced test case
        StringBuilder enhancedDescription = new StringBuilder(original.getDescription());
        if (!edgeCases.isEmpty()) {
            enhancedDescription.append("\n\nAI-Identified Edge Cases:\n");
            for (String edgeCase : edgeCases) {
                enhancedDescription.append("• ").append(edgeCase).append("\n");
            }
        }

        // Determine if AI actually enhanced anything
        boolean wasEnhanced = !enhancedSteps.equals(original.getSteps()) ||
                !enhancedExpected.equals(original.getExpectedResults()) ||
                !edgeCases.isEmpty();

        return TestCase.builder()
                .id(original.getId())
                .title(wasEnhanced ? original.getTitle() + " (AI-Enhanced)" : original.getTitle())
                .description(enhancedDescription.toString())
                .precondition(original.getPrecondition())
                .steps(enhancedSteps)
                .expectedResults(enhancedExpected)
                .priority(original.getPriority())
                .tags(addTag(original.getTags(), wasEnhanced ? "ai-enhanced" : "ai-no-change"))
                .build();
    }

    private List<String> extractEnhancedSteps(String aiResponse, List<String> originalSteps) {
        List<String> enhancedSteps = new ArrayList<>(originalSteps);

        Pattern pattern = Pattern.compile("ENHANCED_STEPS:(.*?)(?=ENHANCED_RESULTS:|EDGE_CASES:|$)", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(aiResponse);

        if (matcher.find()) {
            String stepsSection = matcher.group(1).trim();
            List<String> extractedSteps = extractNumberedItems(stepsSection);
            if (!extractedSteps.isEmpty()) {
                enhancedSteps = extractedSteps;
            }
        }

        return enhancedSteps;
    }

    private List<String> extractEnhancedResults(String aiResponse, List<String> originalResults) {
        List<String> enhancedResults = new ArrayList<>(originalResults);

        Pattern pattern = Pattern.compile("ENHANCED_RESULTS:(.*?)(?=EDGE_CASES:|ENHANCED_STEPS:|$)", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(aiResponse);

        if (matcher.find()) {
            String resultsSection = matcher.group(1).trim();
            List<String> extractedResults = extractNumberedItems(resultsSection);
            if (!extractedResults.isEmpty()) {
                enhancedResults = extractedResults;
            }
        }

        return enhancedResults;
    }

    private List<String> extractEdgeCases(String aiResponse) {
        List<String> edgeCases = new ArrayList<>();

        Pattern pattern = Pattern.compile("EDGE_CASES:(.*?)(?=ENHANCED_STEPS:|ENHANCED_RESULTS:|$)", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(aiResponse);

        if (matcher.find()) {
            String edgeCasesSection = matcher.group(1).trim();
            edgeCases = extractBulletItems(edgeCasesSection);
        }

        return edgeCases;
    }

    private List<String> extractNumberedItems(String text) {
        List<String> items = new ArrayList<>();
        String[] lines = text.split("\n");

        for (String line : lines) {
            line = line.trim();
            // Match patterns like: "1. text", "2) text", "1- text"
            if (line.matches("^\\d+[.)-]\\s+.*")) {
                String item = line.replaceFirst("^\\d+[.)-]\\s+", "").trim();
                if (!item.isEmpty()) {
                    items.add(item);
                }
            }
        }

        return items;
    }

    private List<String> extractBulletItems(String text) {
        List<String> items = new ArrayList<>();
        String[] lines = text.split("\n");

        for (String line : lines) {
            line = line.trim();
            // Match patterns like: "- text", "* text", "• text"
            if (line.matches("^[-*•]\\s+.*")) {
                String item = line.replaceFirst("^[-*•]\\s+", "").trim();
                if (!item.isEmpty()) {
                    items.add(item);
                }
            }
        }

        return items;
    }

    private String cleanAIResponse(String response) {
        if (response == null) {
            return "";
        }

        // Remove common unwanted prefixes
        String cleaned = response
                .replaceAll("(?i)^\\s*(here('s| is) (the )?(response|output|result):?\\s*)", "")
                .replaceAll("```(json|text|plain)?", "")
                .replaceAll("\"", "")
                .trim();

        return cleaned.trim();
    }

    private List<String> addTag(List<String> originalTags, String newTag) {
        if (originalTags == null) {
            return Arrays.asList(newTag);
        }

        List<String> tags = new ArrayList<>(originalTags);
        if (!tags.contains(newTag)) {
            tags.add(newTag);
        }
        return tags;
    }

    public String testAIConnection() {
        if (!aiEnabled || chatClient == null) {
            return "AI is disabled or ChatClient not available";
        }

        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            try {
                String response = chatClient.prompt()
                        .user("Say 'TEST OK' if you can hear me. Do not add anything else.")
                        .call()
                        .content();

                return "AI Connection Test: " + response;
            } catch (Exception e) {
                return "AI Connection Failed: " + e.getMessage();
            }
        });

        try {
            // Add timeout
            return future.get(15, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            return "AI Connection Timeout: No response after 15 seconds";
        } catch (Exception e) {
            return "AI Connection Error: " + e.getMessage();
        }
    }
}