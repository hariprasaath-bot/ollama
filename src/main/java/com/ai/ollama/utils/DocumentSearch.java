package com.ai.ollama.utils;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.Set;

class Lexer implements Iterator<String> {
    private final String input;
    private int pos = 0;
    private String nextToken;
    private static final List<Character> IGNORE_CHARS = List.of('"', ')', '(', '?', ']', '[', '=', '-', ',', ';', ':', '.');

    public Lexer(String input) {
        this.input = input;
        advance();
    }

    // Advance to the next token
    private void advance() {
        // Skip whitespace
        while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) {
            pos++;
        }
        if (pos >= input.length()) {
            nextToken = null;
            return;
        }
        // Tokenize the input string
        int start = pos;
        while (pos < input.length() && !Character.isWhitespace(input.charAt(pos))) {
            char ch = input.charAt(pos);
            if (IGNORE_CHARS.contains(ch) || ch < 0x20 || ch > 0x7E || !Character.isLetter(ch)) {
                // If we encounter a punctuation mark or non-letter, we are ignoring that
                if (start < pos) {
                    return;
                }
                pos++;
                return;
            }
            pos++;
        }
        nextToken = input.substring(start, pos);
    }

    @Override
    public boolean hasNext() {
        return nextToken != null;
    }

    @Override
    public String next() {
        if (!hasNext())
            throw new NoSuchElementException();
        String token = nextToken;
        advance();
        return token;
    }
}

class DocumentNode {
    char c;
    DocumentNode[] children = new DocumentNode[26];
    boolean isEndOfWord;
    Map<String, List<Integer>> documentLines = new HashMap<>(); // document name -> list of line numbers

    public DocumentNode(char c) {
        this.c = c;
    }
}

class DocumentTrie {
    private DocumentNode root = new DocumentNode(' ');

    public void insert(String word, String document, int lineNumber) {
        DocumentNode temp = root;
        for (char ch : word.toCharArray()) {
            if (ch < 'a' || ch > 'z') continue; // skip invalid chars
            int index = ch - 'a';
            if (temp.children[index] == null) {
                temp.children[index] = new DocumentNode(ch);
            }
            temp = temp.children[index];
        }
        temp.isEndOfWord = true;
        temp.documentLines.computeIfAbsent(document, k -> new ArrayList<>()).add(lineNumber);
    }

    public Map<String, List<Integer>> searchDocuments(String word) {
        DocumentNode temp = root;
        for (char ch : word.toLowerCase().toCharArray()) {
            if (ch < 'a' || ch > 'z') {
                return new HashMap<>();
            }
            int index = ch - 'a';
            if (temp.children[index] == null) {
                // Try fuzzy search
                return fuzzySearch(word);
            }
            temp = temp.children[index];
        }
        if (temp.isEndOfWord) {
            return new HashMap<>(temp.documentLines);
        }
        // Try fuzzy search
        return fuzzySearch(word);
    }

    private Map<String, List<Integer>> fuzzySearch(String word) {
        List<String> allWords = getAllWords();
        String closest = null;
        int minDist = Integer.MAX_VALUE;
        for (String w : allWords) {
            int dist = levenshtein(word, w);
            if (dist < minDist) {
                minDist = dist;
                closest = w;
            }
        }
        if (minDist <= 2 && closest != null) {
            return searchDocuments(closest); // Exact search for closest
        }
        return new HashMap<>();
    }

    private List<String> getAllWords() {
        List<String> words = new ArrayList<>();
        getAllWordsHelper(root, new StringBuilder(), words);
        return words;
    }

    private void getAllWordsHelper(DocumentNode node, StringBuilder sb, List<String> words) {
        if (node.isEndOfWord && node.c != ' ') {
            words.add(sb.toString());
        }
        for (DocumentNode child : node.children) {
            if (child != null) {
                sb.append(child.c);
                getAllWordsHelper(child, sb, words);
                sb.deleteCharAt(sb.length() - 1);
            }
        }
    }

    private int levenshtein(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                if (a.charAt(i - 1) == b.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = 1 + Math.min(dp[i - 1][j - 1], Math.min(dp[i - 1][j], dp[i][j - 1]));
                }
            }
        }
        return dp[a.length()][b.length()];
    }
}

@Component
public class DocumentSearch {
    private DocumentTrie trie = new DocumentTrie();
    private Map<String, List<String>> documentContents = new HashMap<>();

    public void indexDocuments(List<Path> files) {
        for (Path file : files) {
            try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                String line;
                int lineNumber = 0;
                List<String> lines = new ArrayList<>();
                while ((line = reader.readLine()) != null) {
                    lineNumber++;
                    lines.add(line);
                    Lexer lex = new Lexer(line);
                    while (lex.hasNext()) {
                        String token = lex.next().toLowerCase();
                        trie.insert(token, file.getFileName().toString(), lineNumber);
                    }
                }
                documentContents.put(file.getFileName().toString(), lines);
            } catch (IOException e) {
                System.err.println("Error reading file: " + file);
            }
        }
    }

    public void indexDocument(String documentName, List<String> lines) {
        if (documentName == null || lines == null) return;
        int lineNumber = 0;
        for (String line : lines) {
            lineNumber++;
            Lexer lex = new Lexer(line == null ? "" : line);
            while (lex.hasNext()) {
                String token = lex.next().toLowerCase();
                trie.insert(token, documentName, lineNumber);
            }
        }
        documentContents.put(documentName, new ArrayList<>(lines));
    }

    public Map<String, Set<Integer>> searchKeywords(List<String> keywords) {
        Map<String, Set<Integer>> result = new HashMap<>();
        for (String keyword : keywords) {
            Map<String, List<Integer>> docs = trie.searchDocuments(keyword.toLowerCase());
            for (Map.Entry<String, List<Integer>> entry : docs.entrySet()) {
                result.computeIfAbsent(entry.getKey(), k -> new HashSet<>()).addAll(entry.getValue());
            }
        }
        return result;
    }

    public Map<String, List<String>> getDocumentContents() {
        return documentContents;
    }

    public static void main(String[] args) {
        DocumentSearch ds = new DocumentSearch();

        // Example: index all .txt files in current directory
        List<Path> files = new ArrayList<>();
        try {
            Files.list(Paths.get("."))
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".txt"))
                    .forEach(files::add);
        } catch (IOException e) {
            System.err.println("Error listing files");
        }

        System.out.println("Indexing documents...");
        ds.indexDocuments(files);
        System.out.println("Indexing complete. Found " + files.size() + " documents.");

        Scanner sc = new Scanner(System.in);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            sc.close();
            System.out.println("\nScanner closed. Program interrupted (Ctrl+C).");
        }));

        while (true) {
            System.out.print("Enter keywords (comma separated, or 'exit' to quit): ");
            String input = sc.nextLine().trim();
            if (input.equalsIgnoreCase("exit")) break;
            if (input.isEmpty()) continue;

            List<String> keywords = Arrays.asList(input.split("\\s*,\\s*"));
            Map<String, Set<Integer>> results = ds.searchKeywords(keywords);
            if (results.isEmpty()) {
                System.out.println("Documents containing keywords: NONE");
            } else {
                System.out.println("Documents containing keywords:");
                for (Map.Entry<String, Set<Integer>> entry : results.entrySet()) {
                    String doc = entry.getKey();
                    List<String> lines = ds.getDocumentContents().get(doc);
                    for (int lineNum : entry.getValue()) {
                        System.out.println("  " + doc + " line " + lineNum + ": " + lines.get(lineNum - 1));
                    }
                }
            }
        }
        // sc.close(); // Handled by shutdown hook
    }
}

