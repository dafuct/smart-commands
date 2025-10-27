package com.smartcommands.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
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

    @Setter
    @Getter
    public static class OllamaOptions {
        @JsonProperty("temperature")
        private double temperature = 0.7;
        
        @JsonProperty("top_p")
        private double topP = 0.9;
        
        @JsonProperty("max_tokens")
        private int maxTokens = 500;

        public OllamaOptions() {}

    }
}
