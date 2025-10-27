package com.smartcommands.service;

import com.smartcommands.config.OllamaProperties;
import com.smartcommands.model.OllamaRequest;
import com.smartcommands.model.OllamaResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;

@Service
public class OllamaService {
    private static final Logger logger = LoggerFactory.getLogger(OllamaService.class);
    
    private final WebClient webClient;
    private final OllamaProperties ollamaProperties;

    @Autowired
    public OllamaService(OllamaProperties ollamaProperties) {
        this.ollamaProperties = ollamaProperties;
        this.webClient = WebClient.builder()
                .baseUrl(ollamaProperties.getBaseUrl())
                .build();
    }

    /**
     * Check if Ollama is running and accessible
     */
    public boolean isOllamaRunning() {
        try {
            String response = webClient.get()
                    .uri("/api/tags")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(5))
                    .block();
            return response != null && !response.isEmpty();
        } catch (Exception e) {
            logger.debug("Ollama is not running or not accessible: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Get list of available models
     */
    public List<String> getAvailableModels() {
        try {
            String response = webClient.get()
                    .uri("/api/tags")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();
            
            // Parse response to extract model names
            // This is a simplified version - in production, you'd want proper JSON parsing
            if (response != null && response.contains("\"models\"")) {
                logger.debug("Available models response: {}", response);
                // For now, return the configured model if it's likely available
                return List.of(ollamaProperties.getModel());
            }
            return List.of();
        } catch (Exception e) {
            logger.error("Failed to get available models: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Check if the specific model is available
     */
    public boolean isModelAvailable(String modelName) {
        List<String> availableModels = getAvailableModels();
        return availableModels.contains(modelName);
    }

    /**
     * Generate a command suggestion from Ollama
     */
    public String generateCommandSuggestion(String userInput, String context) {
        String prompt = buildPrompt(userInput, context);
        
        OllamaRequest request = new OllamaRequest(ollamaProperties.getModel(), prompt);
        
        try {
            OllamaResponse response = webClient.post()
                    .uri("/api/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(OllamaResponse.class)
                    .timeout(Duration.ofMillis(ollamaProperties.getTimeout()))
                    .retryWhen(Retry.backoff(ollamaProperties.getMaxRetries(), Duration.ofSeconds(1)))
                    .block();
            
            if (response != null && response.getResponse() != null) {
                return response.getResponse().trim();
            }
        } catch (Exception e) {
            logger.error("Failed to generate command suggestion: {}", e.getMessage());
        }
        
        return null;
    }

    /**
     * Generate suggestion for incorrect command
     */
    public String suggestCorrectCommand(String incorrectCommand) {
        String prompt = String.format(
            "The user entered the command '%s' which is incorrect. " +
            "Please suggest the correct Linux/macOS terminal command. " +
            "Respond with ONLY the correct command, no explanation. " +
            "If multiple commands could work, provide the most common one.",
            incorrectCommand
        );
        
        return generateCommandSuggestion(incorrectCommand, prompt);
    }

    /**
     * Generate commands for a specific task
     */
    public String suggestCommandsForTask(String taskDescription) {
        String prompt = String.format(
            "The user wants to: %s. " +
            "Please provide the most appropriate Linux/macOS terminal command(s) to accomplish this task. " +
            "Format your response as a single line with the command(s), separated by && if multiple commands are needed. " +
            "Do not include explanations, only the command(s).",
            taskDescription
        );
        
        return generateCommandSuggestion(taskDescription, prompt);
    }

    /**
     * Build prompt for Ollama based on user input and context
     */
    private String buildPrompt(String userInput, String context) {
        return String.format(
            "You are a Linux/macOS terminal expert. %s\n\n" +
            "User input: %s\n\n" +
            "Provide helpful, accurate terminal commands. Be concise and practical.",
            context, userInput
        );
    }

    /**
     * Get Ollama configuration info
     */
    public OllamaProperties getConfiguration() {
        return ollamaProperties;
    }
}
