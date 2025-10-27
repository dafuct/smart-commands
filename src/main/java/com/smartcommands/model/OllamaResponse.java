package com.smartcommands.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class OllamaResponse {

    private String model;
    private String response;
    private boolean done;
    
    @JsonProperty("created_at")
    private String createdAt;
    
    private OllamaMetrics metrics;

    public OllamaResponse() {}

    @Setter
    @Getter
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

    }
}
