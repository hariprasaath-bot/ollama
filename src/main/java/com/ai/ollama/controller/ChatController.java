package com.ai.ollama.controller;

import com.ai.ollama.model.ChatRequest;
import com.ai.ollama.model.ChatResponse;
import com.ai.ollama.model.RagRequest;
import com.ai.ollama.service.OllamaClient;
import com.ai.ollama.service.RagService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final OllamaClient ollamaClient;
    private final RagService ragService;

    public ChatController(OllamaClient ollamaClient, RagService ragService) {
        this.ollamaClient = ollamaClient;
        this.ragService = ragService;
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ChatResponse chat(@RequestBody ChatRequest request) {
        long start = System.currentTimeMillis();
        int count = request.getMessages() == null ? 0 : request.getMessages().size();
        log.info("[ChatController] /api/chat called: model={} messages={}", request.getModel(), count);
        try {
            ChatResponse resp = ollamaClient.chat(request);
            long took = System.currentTimeMillis() - start;
            log.info("[ChatController] /api/chat success: model={} took={}ms responseChars={}", request.getModel(), took,
                    resp.getResponse() == null ? 0 : resp.getResponse().length());
            return resp;
        } catch (Exception e) {
            long took = System.currentTimeMillis() - start;
            log.error("[ChatController] /api/chat failed: model={} took={}ms error={}", request.getModel(), took, e.toString());
            throw e;
        }
    }

    @PostMapping(value = "/docs", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ChatResponse chatWithDocs(@RequestBody RagRequest request) {
        long start = System.currentTimeMillis();
        log.info("[ChatController] /api/chat/docs called: model={} promptChars={}", request.getModel(),
                request.getPrompt() == null ? 0 : request.getPrompt().length());
        try {
            ChatResponse resp = ragService.chatWithDocs(request);
            long took = System.currentTimeMillis() - start;
            log.info("[ChatController] /api/chat/docs success: model={} took={}ms responseChars={}", request.getModel(), took,
                    resp.getResponse() == null ? 0 : resp.getResponse().length());
            return resp;
        } catch (Exception e) {
            long took = System.currentTimeMillis() - start;
            log.error("[ChatController] /api/chat/docs failed: model={} took={}ms error={}", request.getModel(), took, e.toString());
            throw e;
        }
    }
}
