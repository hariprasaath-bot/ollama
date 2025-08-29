package com.ai.ollama.config;

import com.ai.ollama.service.VectorIndex;
import com.ai.ollama.utils.DocumentSearch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
public class DocumentIndexer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DocumentIndexer.class);

    private final DocumentSearch documentSearch;
    private final VectorIndex vectorIndex;

    public DocumentIndexer(DocumentSearch documentSearch, VectorIndex vectorIndex) {
        this.documentSearch = documentSearch;
        this.vectorIndex = vectorIndex;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:/docs/**");
        int indexedFiles = 0;
        for (Resource res : resources) {
            if (!res.isReadable() || res.isFile() == false) {
                // Still try to read if readable
            }
            if (res.getFilename() == null) continue;
            // Skip directories by checking if it has an extension or is readable stream
            try (BufferedReader br = new BufferedReader(new InputStreamReader(res.getInputStream(), StandardCharsets.UTF_8))) {
                List<String> lines = new ArrayList<>();
                String line;
                while ((line = br.readLine()) != null) {
                    lines.add(line);
                }
                documentSearch.indexDocument(res.getFilename(), lines);
                vectorIndex.indexDocument(res.getFilename(), lines);
                indexedFiles++;
            } catch (Exception e) {
                log.warn("[DocumentIndexer] Failed to index resource {}: {}", res.getFilename(), e.toString());
            }
        }
        log.info("[DocumentIndexer] Indexed {} documents from classpath:/docs/**", indexedFiles);
    }
}
