package com.contestmate.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Optional;

/**
 * Calls a server exposing the OpenAI Chat Completions + Embeddings HTTP contract.
 * Works unmodified against OpenAI, Ollama (`/v1/chat/completions`, `/v1/embeddings`),
 * or a vLLM server started with `--api-key` disabled or set.
 *
 * Configured via contest-mate.llm.base-url; when blank, every call is treated as
 * "not configured" and callers must fall back to heuristics (see ExtractionService).
 */
@Component
public class OpenAiCompatibleLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleLlmClient.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String baseUrl;
    private final String apiKey;
    private final String chatModel;
    private final String embeddingModel;

    public OpenAiCompatibleLlmClient(RestTemplateBuilder builder,
                                      @Value("${contest-mate.llm.base-url:}") String baseUrl,
                                      @Value("${contest-mate.llm.api-key:}") String apiKey,
                                      @Value("${contest-mate.llm.chat-model:llama3.1}") String chatModel,
                                      @Value("${contest-mate.llm.embedding-model:nomic-embed-text}") String embeddingModel,
                                      @Value("${contest-mate.llm.timeout-ms:20000}") long timeoutMs) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofMillis(timeoutMs))
                .setReadTimeout(Duration.ofMillis(timeoutMs))
                .build();
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        this.apiKey = apiKey;
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
    }

    @Override
    public boolean isConfigured() {
        return !baseUrl.isBlank();
    }

    @Override
    public Optional<String> chatComplete(String systemPrompt, String userPrompt) {
        if (!isConfigured()) {
            return Optional.empty();
        }
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", chatModel);
            body.put("temperature", 0.1);
            ArrayNode messages = body.putArray("messages");
            messages.addObject().put("role", "system").put("content", systemPrompt);
            messages.addObject().put("role", "user").put("content", userPrompt);

            HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(body), jsonHeaders());
            JsonNode response = objectMapper.readTree(
                    restTemplate.postForObject(baseUrl + "/v1/chat/completions", request, String.class));
            String content = response.path("choices").path(0).path("message").path("content").asText(null);
            return Optional.ofNullable(content);
        } catch (Exception e) {
            log.warn("LLM chat completion failed, caller should fall back to heuristics: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Optional<float[]> embed(String text) {
        if (!isConfigured()) {
            return Optional.empty();
        }
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", embeddingModel);
            body.put("input", text);

            HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(body), jsonHeaders());
            JsonNode response = objectMapper.readTree(
                    restTemplate.postForObject(baseUrl + "/v1/embeddings", request, String.class));
            ArrayNode vector = (ArrayNode) response.path("data").path(0).path("embedding");
            float[] result = new float[vector.size()];
            for (int i = 0; i < vector.size(); i++) {
                result[i] = (float) vector.get(i).asDouble();
            }
            return Optional.of(result);
        } catch (Exception e) {
            log.warn("LLM embedding call failed, caller should fall back to keyword dedup: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (apiKey != null && !apiKey.isBlank()) {
            headers.setBearerAuth(apiKey);
        }
        return headers;
    }
}
