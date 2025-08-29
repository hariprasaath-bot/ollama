package com.ai.ollama.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class VectorIndex {

    private static final Logger log = LoggerFactory.getLogger(VectorIndex.class);

    private final HuggingFaceEmbeddingClient embeddingClient;

    public VectorIndex(HuggingFaceEmbeddingClient embeddingClient) {
        this.embeddingClient = embeddingClient;
    }

    private static class Entry {
        final String docName;
        final int startLine;
        final int endLine;
        final String text;
        final double[] vector;

        Entry(String docName, int startLine, int endLine, String text, List<Double> vector) {
            this.docName = docName;
            this.startLine = startLine;
            this.endLine = endLine;
            this.text = text;
            this.vector = vector == null ? new double[0] : vector.stream().mapToDouble(Double::doubleValue).toArray();
        }
    }

    public static class SearchResult {
        public final String docName;
        public final int startLine;
        public final int endLine;
        public final String snippet;
        public final double score;

        public SearchResult(String docName, int startLine, int endLine, String snippet, double score) {
            this.docName = docName;
            this.startLine = startLine;
            this.endLine = endLine;
            this.snippet = snippet;
            this.score = score;
        }
    }

    private final List<Entry> entries = new ArrayList<>();

    public void clear() {
        synchronized (entries) {
            entries.clear();
        }
    }

    public void indexDocument(String docName, List<String> lines) {
        if (docName == null || lines == null || lines.isEmpty()) return;
        int chunkSize = 20;
        int overlap = 5;
        int i = 0;
        while (i < lines.size()) {
            int start = i + 1; // 1-based
            int end = Math.min(lines.size(), i + chunkSize);
            String text = joinLines(lines, start, end);
            if (text.trim().length() >= 32) {
                List<Double> vec = embeddingClient.embed(text);
                if (!vec.isEmpty()) {
                    Entry e = new Entry(docName, start, end, text, vec);
                    synchronized (entries) {
                        entries.add(e);
                    }
                }
            }
            if (end == lines.size()) break;
            i = i + (chunkSize - overlap);
        }
        log.info("[VectorIndex] Indexed document {} into {} chunks", docName, Math.max(1, (lines.size() + (chunkSize - 1)) / (chunkSize - overlap)));
    }

    private String joinLines(List<String> lines, int startInclusive1, int endInclusive1) {
        StringBuilder sb = new StringBuilder();
        for (int ln = startInclusive1; ln <= endInclusive1; ln++) {
            int idx = ln - 1;
            if (idx >= 0 && idx < lines.size()) {
                sb.append("[line ").append(ln).append("] ").append(lines.get(idx)).append("\n");
            }
        }
        return sb.toString();
    }

    public List<SearchResult> search(String query, int topK) {
        List<Double> qVec = embeddingClient.embed(Objects.requireNonNullElse(query, "").trim());
        if (qVec.isEmpty()) return List.of();
        double[] q = qVec.stream().mapToDouble(Double::doubleValue).toArray();
        List<Entry> snapshot;
        synchronized (entries) {
            snapshot = new ArrayList<>(entries);
        }
        List<SearchResult> scored = snapshot.stream()
                .map(e -> new SearchResult(e.docName, e.startLine, e.endLine, e.text, cosine(q, e.vector)))
                .sorted(Comparator.comparingDouble((SearchResult s) -> s.score).reversed())
                .limit(Math.max(1, topK))
                .collect(Collectors.toList());
        return scored;
    }

    private double cosine(double[] a, double[] b) {
        if (a.length == 0 || b.length == 0 || a.length != b.length) return -1.0;
        double dot = 0.0, na = 0.0, nb = 0.0;
        for (int i = 0; i < a.length; i++) {
            double x = a[i];
            double y = b[i];
            dot += x * y;
            na += x * x;
            nb += y * y;
        }
        if (na == 0 || nb == 0) return -1.0;
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}
