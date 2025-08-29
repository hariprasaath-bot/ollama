package com.ai.ollama.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class HuggingFaceEmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(HuggingFaceEmbeddingClient.class);

    private final WebClient webClient;

    @Value("${huggingface.api.base-url:https://api-inference.huggingface.co/pipeline/feature-extraction}")
    private String                                  baseUrl;

    @Value("${HUGGING_FACE_API_TOKEN:}")
    private String apiTokenEnv;

    @Value("${huggingface.api.token:}")
    private String apiTokenProp;

    @Value("${huggingface.api.model:sentence-transformers/all-MiniLM-L6-v2}")
    private String model;

    public HuggingFaceEmbeddingClient(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    public List<Double> embed(String text) {
        try {
            if (text == null || text.isBlank()) return List.of();
            String token = StringUtils.hasText(apiTokenProp) ? apiTokenProp : apiTokenEnv;
            String uri = baseUrl;
            if (StringUtils.hasText(model)) {
                uri = baseUrl.endsWith("/") ? baseUrl + model : baseUrl + "/" + model;
            }

            Map<String, Object> payload = Map.of(
                    "inputs", text
            );


            WebClient.RequestHeadersSpec<?> spec = this.webClient
                    .post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromValue(payload));
            if (StringUtils.hasText(token)) {
                spec = spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            }

            Object response = spec
                    .retrieve()
                    .bodyToMono(Object.class)
                    .onErrorResume(ex -> {
                        log.warn("[HuggingFaceEmbeddingClient] Embedding call failed: {}", ex.toString());
                        return Mono.just(Collections.emptyList());
                    })
                    .block();

            return normalizeToVector(response);
        } catch (Exception e) {
            log.warn("[HuggingFaceEmbeddingClient] Embedding exception: {}", e.toString());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Double> normalizeToVector(Object response) {
        if (response == null) return List.of();
        // Response can be List<Double> or List<List<Double>> (batch). It may also be List<Number>
        if (response instanceof List<?> list) {
            if (list.isEmpty()) return List.of();
            Object first = list.get(0);
            if (first instanceof Number) {
                // Flat vector
                return list.stream().map(v -> v instanceof Number n ? n.doubleValue() : 0.0).toList();
            } else if (first instanceof List<?>) {
                // Batch: take the first row
                List<?> row = (List<?>) first;
                return row.stream().map(v -> v instanceof Number n ? n.doubleValue() : 0.0).toList();
            } else if (first instanceof Map<?, ?> m && m.containsKey("vector")) {
                // Rare alternative schema {vector: [...]}
                Object vec = m.get("vector");
                if (vec instanceof List<?> vlist) {
                    return vlist.stream().map(v -> v instanceof Number n ? n.doubleValue() : 0.0).toList();
                }
            }
        }
        // Unknown format — do not fail
        return List.of();
    }
}
