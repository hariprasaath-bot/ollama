package com.ai.ollama.service;

import com.ai.ollama.model.ChatRequest;
import com.ai.ollama.model.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class OllamaClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaClient.class);

    private final ChatClient chatClient;

    public OllamaClient(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public ChatResponse chat(ChatRequest request) {
        long start = System.currentTimeMillis();
        String model = request.getModel();
        int messageCount = request.getMessages() == null ? 0 : request.getMessages().size();
        log.info("[OllamaClient] Chat request received: model={}, messages={} optionsKeys={}", model, messageCount,
                request.getOptions() == null ? "-" : request.getOptions().keySet());
        try {
            // Build Spring AI Messages
            List<Message> aiMessages = new ArrayList<>();
            if (request.getMessages() != null) {
                for (ChatRequest.Message m : request.getMessages()) {
                    String role = m.getRole() == null ? "user" : m.getRole().toLowerCase();
                    String content = m.getContent() == null ? "" : m.getContent();
                    switch (role) {
                        case "system" -> aiMessages.add(new SystemMessage(content));
                        case "assistant" -> aiMessages.add(new AssistantMessage(content));
                        default -> aiMessages.add(new UserMessage(content));
                    }
                }
            }

            // Map basic options
            OllamaOptions.Builder optionsBuilder = OllamaOptions.builder();
            if (model != null && !model.isBlank()) {
                optionsBuilder = optionsBuilder.model(model);
            }
            Map<String, Object> opts = request.getOptions();
            if (opts != null) {
                Object temperature = opts.get("temperature");
                if (temperature instanceof Number num) {
                    optionsBuilder = optionsBuilder.temperature(num.doubleValue());
                }
                Object topP = opts.get("top_p");
                if (topP instanceof Number num) {
                    optionsBuilder = optionsBuilder.topP(num.doubleValue());
                }
                Object topK = opts.get("top_k");
                if (topK instanceof Number num) {
                    optionsBuilder = optionsBuilder.topK(num.intValue());
                }
                Object maxTokens = opts.get("max_tokens");
                if (maxTokens instanceof Number num) {
                    optionsBuilder = optionsBuilder.numPredict(num.intValue());
                }
            }

            Prompt prompt = new Prompt(aiMessages, optionsBuilder.build());

            String content = this.chatClient
                    .prompt(prompt)
                    .call()
                    .content();

            long took = System.currentTimeMillis() - start;
            log.info("[OllamaClient] Chat completed: model={} took={}ms responseChars={}", model, took,
                    content == null ? 0 : content.length());
            return new ChatResponse(model, content == null ? "" : content);
        } catch (Exception e) {
            long took = System.currentTimeMillis() - start;
            log.error("[OllamaClient] Chat failed: model={} took={}ms error={}", model, took, e.toString());
            throw e;
        }
    }
}
