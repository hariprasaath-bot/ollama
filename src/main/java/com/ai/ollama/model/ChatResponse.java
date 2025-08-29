package com.ai.ollama.model;

import java.util.List;

public class ChatResponse {
    private String model;
    private String response; // aggregated text for non-streaming

    public ChatResponse() {}

    public ChatResponse(String model, String response) {
        this.model = model;
        this.response = response;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }

    // Helper structure to map Ollama's streaming parts if needed
    public static class Message {
        private String role;
        private String content;

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }
}
