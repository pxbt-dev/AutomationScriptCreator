package com.pxbtdev.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Direct HTTP integration with Ollama - no Spring AI dependency.
 * Supports qwen2.5-coder:7b (recommended for 16GB RAM no-GPU).
 */
@Slf4j
@Service
public class OllamaService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${ollama.enabled:true}")
    private boolean enabled;

    @Value("${ollama.model:qwen2.5-coder:7b}")
    private String model;

    @Value("${ollama.fallback-model:phi3:mini}")
    private String fallbackModel;

    @Value("${ollama.timeout-seconds:60}")
    private int timeoutSeconds;

    @Value("${ollama.max-tokens:2048}")
    private int maxTokens;

    public OllamaService(WebClient ollamaWebClient) {
        this.webClient = ollamaWebClient;
    }

    /**
     * Check if Ollama is running and the model is available.
     */
    public boolean isAvailable() {
        if (!enabled)
            return false;
        try {
            String response = webClient.get()
                    .uri("/api/tags")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(5));
            return response != null;
        } catch (Exception e) {
            log.debug("Ollama not available: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Get list of available Ollama models.
     */
    public List<String> getAvailableModels() {
        try {
            String response = webClient.get()
                    .uri("/api/tags")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(10));

            if (response == null)
                return List.of();

            JsonNode root = objectMapper.readTree(response);
            JsonNode models = root.get("models");
            if (models == null || !models.isArray())
                return List.of();

            List<String> modelNames = new java.util.ArrayList<>();
            for (JsonNode m : models) {
                JsonNode name = m.get("name");
                if (name != null)
                    modelNames.add(name.asText());
            }
            return modelNames;
        } catch (Exception e) {
            log.warn("Failed to list Ollama models: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Generate a completion using Ollama. Returns empty string if unavailable.
     */
    public String generate(String prompt) {
        return generate(prompt, model);
    }

    /**
     * Generate with a specific model.
     */
    public String generate(String prompt, String modelName) {
        if (!enabled) {
            log.debug("Ollama disabled, skipping AI generation");
            return "";
        }
        try {
            Map<String, Object> request = Map.of(
                    "model", modelName,
                    "prompt", prompt,
                    "stream", false,
                    "options", Map.of(
                            "num_predict", maxTokens,
                            "temperature", 0.3,
                            "top_p", 0.9));

            String body = objectMapper.writeValueAsString(request);

            String response = webClient.post()
                    .uri("/api/generate")
                    .header("Content-Type", "application/json")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(timeoutSeconds));

            if (response == null)
                return "";

            JsonNode root = objectMapper.readTree(response);
            JsonNode responseNode = root.get("response");
            return responseNode != null ? responseNode.asText().trim() : "";

        } catch (Exception e) {
            log.warn("Ollama generation failed (model={}): {}", modelName, e.getMessage());
            // Try fallback model if primary failed
            if (!modelName.equals(fallbackModel)) {
                log.info("Trying fallback model: {}", fallbackModel);
                return generate(prompt, fallbackModel);
            }
            return "";
        }
    }

    /**
     * Generate a chat-style completion (better for instruction-following).
     */
    public String chat(String systemPrompt, String userMessage) {
        if (!enabled)
            return "";
        try {
            Map<String, Object> request = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userMessage)),
                    "stream", false,
                    "options", Map.of(
                            "num_predict", maxTokens,
                            "temperature", 0.3));

            String body = objectMapper.writeValueAsString(request);

            String response = webClient.post()
                    .uri("/api/chat")
                    .header("Content-Type", "application/json")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(timeoutSeconds));

            if (response == null)
                return "";

            JsonNode root = objectMapper.readTree(response);
            JsonNode message = root.path("message").path("content");
            return message.isMissingNode() ? "" : message.asText().trim();

        } catch (Exception e) {
            log.warn("Ollama chat failed: {}", e.getMessage());
            return "";
        }
    }

    /**
     * Stream a response back as Flux for live output (used by WebSocket).
     */
    public Flux<String> stream(String prompt) {
        if (!enabled)
            return Flux.empty();
        try {
            Map<String, Object> request = Map.of(
                    "model", model,
                    "prompt", prompt,
                    "stream", true);
            String body = objectMapper.writeValueAsString(request);

            return webClient.post()
                    .uri("/api/generate")
                    .header("Content-Type", "application/json")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToFlux(String.class)
                    .mapNotNull(chunk -> {
                        try {
                            JsonNode node = objectMapper.readTree(chunk);
                            JsonNode r = node.get("response");
                            return r != null ? r.asText() : null;
                        } catch (Exception e) {
                            return null;
                        }
                    });
        } catch (Exception e) {
            log.warn("Ollama stream failed: {}", e.getMessage());
            return Flux.empty();
        }
    }

    public String getModel() {
        return model;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
