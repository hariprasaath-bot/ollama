package com.ai.ollama.model;

import java.util.Map;

public class RagRequest {
    private String model; // optional override
    private String prompt;
    private Map<String, Object> options; // optional generation options

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public Map<String, Object> getOptions() {
        return options;
    }

    public void setOptions(Map<String, Object> options) {
        this.options = options;
    }
}
