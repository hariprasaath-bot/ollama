package com.ai.ollama.service;

import com.ai.ollama.model.ChatResponse;
import com.ai.ollama.model.RagRequest;
import com.ai.ollama.utils.DocumentSearch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    private final ChatClient chatClient;
    private final DocumentSearch documentSearch;
    private final VectorIndex vectorIndex;

    public RagService(ChatClient.Builder chatClientBuilder, DocumentSearch documentSearch, VectorIndex vectorIndex) {
        this.chatClient = chatClientBuilder.build();
        this.documentSearch = documentSearch;
        this.vectorIndex = vectorIndex;
    }

    public ChatResponse chatWithDocs(RagRequest request) {
        long start = System.currentTimeMillis();
        String model = request.getModel();
        String promptText = request.getPrompt() == null ? "" : request.getPrompt().trim();
        if (promptText.isEmpty()) {
            return new ChatResponse(model, "Prompt is empty.");
        }
        try {
            // 1) Ask AI to extract keywords
            List<String> keywords = extractKeywords(promptText, model, request.getOptions());
            log.info("[RagService] Extracted keywords: {}", keywords);

            // 2) Semantic search with vector index using keywords joined or full prompt as fallback
            String query = (keywords == null || keywords.isEmpty()) ? promptText : String.join(", ", keywords);
            List<VectorIndex.SearchResult> results = vectorIndex.search(query, 8);

            StringBuilder aggregated = new StringBuilder();
            if (results != null && !results.isEmpty()) {
                // Group by document and take top snippets per doc
                Map<String, List<VectorIndex.SearchResult>> byDoc = results.stream()
                        .collect(Collectors.groupingBy(r -> r.docName));
                for (Map.Entry<String, List<VectorIndex.SearchResult>> entry : byDoc.entrySet()) {
                    String doc = entry.getKey();
                    List<VectorIndex.SearchResult> top = entry.getValue().stream()
                            .sorted(Comparator.comparingDouble((VectorIndex.SearchResult r) -> r.score).reversed())
                            .limit(2)
                            .toList();
                    StringBuilder snippet = new StringBuilder();
                    top.forEach(r -> snippet.append(r.snippet).append("\n"));
                    String summary = summarizeForDoc(promptText, doc, snippet.toString(), model, request.getOptions());
                    if (summary != null && !summary.isBlank()) {
                        aggregated.append("# ").append(doc).append("\n").append(summary.trim()).append("\n\n");
                    }
                }
            } else {
                // Fallback to keyword trie search if vector search yields nothing
                List<String> fallbackKeywords = !keywords.isEmpty() ? keywords : basicPromptTokens(promptText);
                Map<String, Set<Integer>> hits = fallbackKeywords.isEmpty() ? Map.of() : documentSearch.searchKeywords(fallbackKeywords);

                // If trie-based search still yields nothing, try a naive scan for any prompt token occurrences
                if (hits.isEmpty()) {
                    Map<String, List<String>> docContentsAll = documentSearch.getDocumentContents();
                    for (Map.Entry<String, List<String>> docEntry : docContentsAll.entrySet()) {
                        String doc = docEntry.getKey();
                        List<String> lines = docEntry.getValue();
                        for (int i = 0; i < lines.size(); i++) {
                            String line = lines.get(i);
                            for (String tok : fallbackKeywords) {
                                if (tok.length() >= 3 && line.toLowerCase().contains(tok.toLowerCase())) {
                                    hits.computeIfAbsent(doc, k -> new LinkedHashSet<>()).add(i + 1);
                                    break;
                                }
                            }
                        }
                    }
                }

                if (hits.isEmpty()) {
                    log.info("[RagService] No document hits for keywords, prompt tokens, or vectors.");
                    return new ChatResponse(model, "No relevant information found in indexed documents for your query.");
                }

                Map<String, List<String>> docContents = documentSearch.getDocumentContents();
                for (Map.Entry<String, Set<Integer>> e : hits.entrySet()) {
                    String doc = e.getKey();
                    List<String> lines = docContents.getOrDefault(doc, List.of());
                    if (lines.isEmpty()) continue;
                    List<Integer> lineNums = e.getValue().stream().distinct().sorted().collect(Collectors.toList());
                    int maxLines = 60;
                    if (lineNums.size() > maxLines) lineNums = lineNums.subList(0, maxLines);
                    StringBuilder snippet = new StringBuilder();
                    for (Integer ln : lineNums) {
                        int idx = Math.max(1, ln) - 1;
                        if (idx >= 0 && idx < lines.size()) {
                            snippet.append("[line ").append(ln).append("] ").append(lines.get(idx)).append("\n");
                        }
                    }
                    String summary = summarizeForDoc(promptText, doc, snippet.toString(), model, request.getOptions());
                    if (summary != null && !summary.isBlank()) {
                        aggregated.append("# ").append(doc).append("\n").append(summary.trim()).append("\n\n");
                    }
                }
            }

            String finalText = aggregated.length() == 0 ? "No relevant summaries could be generated from the documents." : aggregated.toString().trim();
            long took = System.currentTimeMillis() - start;
            log.info("[RagService] Completed RAG in {} ms", took);
            return new ChatResponse(model, finalText);
        } catch (Exception ex) {
            long took = System.currentTimeMillis() - start;
            log.error("[RagService] RAG flow failed in {} ms: {}", took, ex.toString());
            throw ex;
        }
    }

    private List<String> extractKeywords(String prompt, String model, Map<String, Object> options) {
        String system = "You extract 3-8 concise search keywords from a user's request strictly as a single comma-separated line. No explanations.";
        String user = "User request: " + prompt + "\nReturn only keywords, comma-separated.";
        String content = callModel(system, user, model, options);
        if (content == null) return List.of();
        String[] parts = content.split(",|\n");
        Pattern wordPattern = Pattern.compile("[A-Za-z0-9][A-Za-z0-9 -]{0,40}");
        Set<String> unique = new LinkedHashSet<>();
        for (String p : parts) {
            String t = p.trim().toLowerCase();
            if (t.isEmpty()) continue;
            if (!wordPattern.matcher(t).find()) continue;
            String cleaned = Arrays.stream(t.split("\\s+")).limit(3).collect(Collectors.joining(" "));
            if (!cleaned.isBlank()) unique.add(cleaned);
        }
        return new ArrayList<>(unique);
    }

    private List<String> basicPromptTokens(String prompt) {
        if (prompt == null) return List.of();
        String[] raw = prompt.toLowerCase().split("[^a-z0-9]+");
        Set<String> stop = Set.of("the","and","or","for","with","from","that","this","into","your","you","are","was","were","will","shall","must","should","can","could","a","an","to","of","in","on","at","by","it","as","is","be","we","our","us");
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        for (String r : raw) {
            if (r == null || r.isBlank()) continue;
            if (r.length() < 3) continue;
            if (stop.contains(r)) continue;
            tokens.add(r);
        }
        return new ArrayList<>(tokens);
    }

    private String summarizeForDoc(String userPrompt, String docName, String snippet, String model, Map<String, Object> options) {
        String system = "You are a helpful assistant. Summarize only using the provided document lines. Cite line numbers inline when relevant. Be concise.";
        String user = "User request: " + userPrompt + "\nDocument: " + docName + "\nRelevant lines (do not hallucinate beyond these):\n```\n" + snippet + "```\nProvide a short summary that answers the user's request using only this content.";
        return callModel(system, user, model, options);
    }

    private String callModel(String systemText, String userText, String model, Map<String, Object> opts) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemText));
        messages.add(new UserMessage(userText));
        OllamaOptions.Builder optionsBuilder = OllamaOptions.builder();
        if (model != null && !model.isBlank()) optionsBuilder = optionsBuilder.model(model);
        if (opts != null) {
            Object temperature = opts.get("temperature");
            if (temperature instanceof Number num) optionsBuilder = optionsBuilder.temperature(num.doubleValue());
            Object topP = opts.get("top_p");
            if (topP instanceof Number num) optionsBuilder = optionsBuilder.topP(num.doubleValue());
            Object topK = opts.get("top_k");
            if (topK instanceof Number num) optionsBuilder = optionsBuilder.topK(num.intValue());
            Object maxTokens = opts.get("max_tokens");
            if (maxTokens instanceof Number num) optionsBuilder = optionsBuilder.numPredict(num.intValue());
        }
        Prompt prompt = new Prompt(messages, optionsBuilder.build());
        return this.chatClient
                .prompt(prompt)
                .call()
                .content();
    }
}
