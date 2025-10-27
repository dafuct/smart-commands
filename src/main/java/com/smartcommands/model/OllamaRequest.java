package com.smartcommands.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class OllamaRequest {
    private String model;
    private String prompt;
    private boolean stream = false;
    
    @JsonProperty("options")
    private OllamaOptions options;

    public OllamaRequest() {}

    public OllamaRequest(String model, String prompt) {
        this.model = model;
        this.prompt = prompt;
        this.options = new OllamaOptions();
    }

    // Getters and Setters
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

    public boolean isStream() {
        return stream;
    }

    public void setStream(boolean stream) {
        this.stream = stream;
    }

    public OllamaOptions getOptions() {
        return options;
    }

    public void setOptions(OllamaOptions options) {
        this.options = options;
    }

    public static class OllamaOptions {
        @JsonProperty("temperature")
        private double temperature = 0.7;
        
        @JsonProperty("top_p")
        private double topP = 0.9;
        
        @JsonProperty("max_tokens")
        private int maxTokens = 500;

        public OllamaOptions() {}

        // Getters and Setters
        public double getTemperature() {
            return temperature;
        }

        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }

        public double getTopP() {
            return topP;
        }

        public void setTopP(double topP) {
            this.topP = topP;
        }

        public int getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
        }
    }
}
