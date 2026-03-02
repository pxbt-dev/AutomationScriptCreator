package com.pxbtdev.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.*;

/**
 * Fetches and parses Swagger/OpenAPI specs from a URL.
 * Supports Swagger 2.0 and OpenAPI 3.0.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SwaggerParserService {

    private final WebClient ollamaWebClient; // reusing WebClient for spec fetching
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Parse a Swagger/OpenAPI spec from a URL.
     * Returns a structured map suitable for JSON serialisation.
     */
    public Map<String, Object> parseSpec(String specUrl) {
        log.info("Parsing Swagger spec from: {}", specUrl);

        ParseOptions parseOptions = new ParseOptions();
        parseOptions.setResolve(true);
        parseOptions.setResolveFully(true);

        SwaggerParseResult result = new OpenAPIV3Parser().readLocation(specUrl, null, parseOptions);

        if (result == null || result.getOpenAPI() == null) {
            // Try fetching raw JSON and re-parsing (for Swagger 2.0 behind corporate auth)
            String rawJson = fetchRaw(specUrl);
            if (rawJson != null) {
                result = new OpenAPIV3Parser().readContents(rawJson, null, parseOptions);
            }
        }

        if (result == null || result.getOpenAPI() == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Could not parse Swagger spec from: " + specUrl);
            error.put("messages", result != null ? result.getMessages() : List.of("No result"));
            return error;
        }

        OpenAPI api = result.getOpenAPI();
        return buildStructuredSpec(api, specUrl);
    }

    private Map<String, Object> buildStructuredSpec(OpenAPI api, String specUrl) {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("success", true);
        spec.put("specUrl", specUrl);

        // Info
        if (api.getInfo() != null) {
            spec.put("title", api.getInfo().getTitle());
            spec.put("version", api.getInfo().getVersion());
            spec.put("description", api.getInfo().getDescription());
        }

        // Base URL
        if (api.getServers() != null && !api.getServers().isEmpty()) {
            spec.put("baseUrl", api.getServers().get(0).getUrl());
        }

        // Endpoints
        List<Map<String, Object>> endpoints = new ArrayList<>();
        if (api.getPaths() != null) {
            for (Map.Entry<String, PathItem> pathEntry : api.getPaths().entrySet()) {
                String path = pathEntry.getKey();
                PathItem pathItem = pathEntry.getValue();

                addEndpointIfPresent(endpoints, path, "GET", pathItem.getGet());
                addEndpointIfPresent(endpoints, path, "POST", pathItem.getPost());
                addEndpointIfPresent(endpoints, path, "PUT", pathItem.getPut());
                addEndpointIfPresent(endpoints, path, "DELETE", pathItem.getDelete());
                addEndpointIfPresent(endpoints, path, "PATCH", pathItem.getPatch());
            }
        }

        spec.put("endpoints", endpoints);
        spec.put("endpointCount", endpoints.size());

        // Tags (groups)
        Set<String> tags = new LinkedHashSet<>();
        for (Map<String, Object> ep : endpoints) {
            Object epTags = ep.get("tags");
            if (epTags instanceof List<?> tagList) {
                tagList.forEach(t -> tags.add(t.toString()));
            }
        }
        spec.put("tags", new ArrayList<>(tags));

        return spec;
    }

    private void addEndpointIfPresent(List<Map<String, Object>> endpoints, String path, String method,
            Operation operation) {
        if (operation == null)
            return;

        Map<String, Object> endpoint = new LinkedHashMap<>();
        endpoint.put("path", path);
        endpoint.put("method", method);
        endpoint.put("operationId", operation.getOperationId());
        endpoint.put("summary", operation.getSummary());
        endpoint.put("description", operation.getDescription());
        endpoint.put("tags", operation.getTags() != null ? operation.getTags() : List.of());
        endpoint.put("deprecated", Boolean.TRUE.equals(operation.getDeprecated()));

        // Parameters
        List<Map<String, Object>> params = new ArrayList<>();
        if (operation.getParameters() != null) {
            for (Parameter param : operation.getParameters()) {
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("name", param.getName());
                p.put("in", param.getIn());
                p.put("required", Boolean.TRUE.equals(param.getRequired()));
                p.put("description", param.getDescription());
                if (param.getSchema() != null) {
                    p.put("type", param.getSchema().getType());
                    p.put("default", param.getSchema().getDefault());
                    p.put("example", param.getSchema().getExample());
                }
                params.add(p);
            }
        }
        endpoint.put("parameters", params);

        // Request body
        if (operation.getRequestBody() != null) {
            endpoint.put("hasRequestBody", true);
            endpoint.put("requestBodyRequired", Boolean.TRUE.equals(operation.getRequestBody().getRequired()));
        } else {
            endpoint.put("hasRequestBody", false);
        }

        // Responses
        Map<String, Object> responses = new LinkedHashMap<>();
        if (operation.getResponses() != null) {
            operation.getResponses().forEach((code, response) -> responses.put(code, response.getDescription()));
        }
        endpoint.put("responses", responses);

        endpoints.add(endpoint);
    }

    private String fetchRaw(String url) {
        try {
            // Build a simple WebClient for fetching the spec
            WebClient wc = WebClient.builder()
                    .codecs(c -> c.defaultCodecs().maxInMemorySize(5 * 1024 * 1024))
                    .build();
            return wc.get()
                    .uri(url)
                    .header("Accept", "application/json, application/yaml, */*")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(15));
        } catch (Exception e) {
            log.warn("Failed to fetch raw spec from {}: {}", url, e.getMessage());
            return null;
        }
    }
}
