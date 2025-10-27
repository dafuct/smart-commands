package com.smartcommands.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class OllamaResponse {
    private String model;
    private String response;
    private boolean done;
    
    @JsonProperty("created_at")
    private String createdAt;
    
    private OllamaMetrics metrics;

    public OllamaResponse() {}

    // Getters and Setters
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

    public boolean isDone() {
        return done;
    }

    public void setDone(boolean done) {
        this.done = done;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public OllamaMetrics getMetrics() {
        return metrics;
    }

    public void setMetrics(OllamaMetrics metrics) {
        this.metrics = metrics;
    }

    public static class OllamaMetrics {
        @JsonProperty("prompt_eval_count")
        private int promptEvalCount;
        
        @JsonProperty("prompt_eval_duration")
        private long promptEvalDuration;
        
        @JsonProperty("eval_count")
        private int evalCount;
        
        @JsonProperty("eval_duration")
        private long evalDuration;

        public OllamaMetrics() {}

        // Getters and Setters
        public int getPromptEvalCount() {
            return promptEvalCount;
        }

        public void setPromptEvalCount(int promptEvalCount) {
            this.promptEvalCount = promptEvalCount;
        }

        public long getPromptEvalDuration() {
            return promptEvalDuration;
        }

        public void setPromptEvalDuration(long promptEvalDuration) {
            this.promptEvalDuration = promptEvalDuration;
        }

        public int getEvalCount() {
            return evalCount;
        }

        public void setEvalCount(int evalCount) {
            this.evalCount = evalCount;
        }

        public long getEvalDuration() {
            return evalDuration;
        }

        public void setEvalDuration(long evalDuration) {
            this.evalDuration = evalDuration;
        }
    }
}
