// ./src/main/java/com/pxbtdev/service/SimpleOllamaService.java
package com.pxbtdev.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.*;

@Slf4j
@Service
public class SimpleOllamaService {

    private final ChatClient chatClient;

    @Value("${ai.enabled:true}")
    private boolean aiEnabled;

    private final ExecutorService healthCheckExecutor = Executors.newSingleThreadExecutor();

    @Autowired(required = false)
    public SimpleOllamaService(@Qualifier("chatClient") ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    // Fallback constructor
    public SimpleOllamaService() {
        this.chatClient = null;
    }

    public String generate(String prompt) {
        if (!isEnabled()) {
            log.debug("AI service disabled");
            return "";
        }

        try {
            log.debug("Generating AI response for prompt (length: {})", prompt.length());

            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            log.debug("AI response generated (length: {})", response.length());
            return response;

        } catch (Exception e) {
            log.error("AI generation failed: {}", e.getMessage());
            return "";
        }
    }

    // New method with timeout
    public String generateWithTimeout(String prompt, long timeoutMs) {
        if (!isEnabled()) {
            log.debug("AI service disabled");
            return "";
        }

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<String> future = executor.submit(() -> {
            try {
                log.debug("Starting AI generation for prompt (length: {})", prompt.length());
                String response = chatClient.prompt()
                        .user(prompt)
                        .call()
                        .content();
                log.debug("AI generation completed (length: {})", response.length());
                return response;
            } catch (Exception e) {
                log.error("AI generation failed: {}", e.getMessage());
                return "";
            }
        });

        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.warn("AI generation timeout after {}ms", timeoutMs);
            future.cancel(true);
            return ""; // Return empty string on timeout
        } catch (Exception e) {
            log.error("Error getting AI response: {}", e.getMessage());
            return "";
        } finally {
            executor.shutdownNow();
        }
    }

    public boolean isEnabled() {
        return aiEnabled && chatClient != null;
    }

    public String testConnection() {
        if (!isEnabled()) {
            return "AI service is disabled (ai.enabled=false or ChatClient not available)";
        }

        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Testing AI connection...");
                String response = chatClient.prompt()
                        .user("Respond with 'SUCCESS' only. No additional text.")
                        .call()
                        .content();

                log.info("AI connection test response: {}", response);

                if ("SUCCESS".equalsIgnoreCase(response.trim())) {
                    return "AI Connection: SUCCESS";
                } else {
                    return "AI Connection: Unexpected response - '" + response + "'";
                }
            } catch (Exception e) {
                log.error("AI connection test failed", e);
                return "AI Connection Failed: " + e.getMessage();
            }
        }, healthCheckExecutor);

        try {
            // Timeout after 15 seconds
            return future.get(15, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.error("AI connection test timeout after 15 seconds");
            return "AI Connection Timeout: No response after 15 seconds";
        } catch (Exception e) {
            log.error("AI connection test error", e);
            return "AI Connection Error: " + e.getMessage();
        }
    }

    // Clean shutdown
    public void shutdown() {
        healthCheckExecutor.shutdown();
        try {
            if (!healthCheckExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                healthCheckExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            healthCheckExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}