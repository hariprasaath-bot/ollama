package com.ai.ollama.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class OllamaEmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaEmbeddingClient.class);

    private final WebClient webClient;

    @Value("${OLLAMA_EMBEDDING_MODEL:${ollama.embedding-model:nomic-embed-text}}")
    private String embeddingModel;

    @Value("${spring.ai.ollama.base-url:${ollama.base-url:http://localhost:11434}}")
    private String baseUrl;

    public OllamaEmbeddingClient(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    public List<Double> embed(String text) {
        try {
            Map<String, Object> payload = Map.of(
                    "model", embeddingModel,
                    "prompt", text
            );
            Map<String, Object> response = this.webClient
                    .post()
                    .uri(baseUrl + "/api/embeddings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromValue(payload))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .onErrorResume(ex -> {
                        log.error("[OllamaEmbeddingClient] Embedding call failed: {}", ex.toString());
                        return Mono.just(Collections.emptyMap());
                    })
                    .block();
            if (response == null) return List.of();
            Object emb = response.get("embedding");
            if (emb instanceof List<?> list) {
                // Convert to List<Double>
                return list.stream()
                        .map(v -> v instanceof Number n ? n.doubleValue() : 0.0)
                        .toList();
            }
            return List.of();
        } catch (Exception e) {
            log.error("[OllamaEmbeddingClient] Embedding exception: {}", e.toString());
            return List.of();
        }
    }
}
