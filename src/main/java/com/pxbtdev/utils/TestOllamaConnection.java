// ./src/main/java/com/pxbtdev/utils/TestOllamaConnection.java
package com.pxbtdev.utils;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.stereotype.Component;

import java.util.concurrent.*;

@Component
public class TestOllamaConnection {

    // Add an executor for timeouts
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public String testConnection() {
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            try {
                OllamaApi api = new OllamaApi("http://localhost:11434");
                OllamaOptions options = OllamaOptions.builder()
                        .model("llama2")
                        .temperature(0.1)
                        .build();

                OllamaChatModel chatModel = OllamaChatModel.builder()
                        .ollamaApi(api)
                        .defaultOptions(options)
                        .build();

                ChatClient client = ChatClient.builder(chatModel).build();

                String response = client.prompt()
                        .user("Respond with only the word 'SUCCESS' and nothing else.")
                        .call()
                        .content();

                return "Ollama Connection Test: " + response.trim();

            } catch (Exception e) {
                return "Ollama Connection Failed: " + e.getMessage();
            }
        });

        try {
            // Timeout after 10 seconds
            return future.get(10, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            return "Ollama Connection Timeout: Server not responding after 10 seconds";
        } catch (Exception e) {
            return "Ollama Connection Error: " + e.getMessage();
        }
    }

    public String testWithChatClient(ChatClient chatClient) {
        if (chatClient == null) {
            return "ChatClient is not available (AI disabled or not configured)";
        }

        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            try {
                String response = chatClient.prompt()
                        .user("Say 'TEST OK' and nothing else.")
                        .call()
                        .content();

                return "ChatClient Test: " + response;
            } catch (Exception e) {
                return "ChatClient Test Failed: " + e.getMessage();
            }
        });

        try {
            // Timeout after 10 seconds
            return future.get(10, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            return "ChatClient Test Timeout: No response after 10 seconds";
        } catch (Exception e) {
            return "ChatClient Test Error: " + e.getMessage();
        }
    }

    // Clean shutdown
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}