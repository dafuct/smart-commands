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

    public List<String> getAvailableModels() {
        try {
            String response = webClient.get()
                    .uri("/api/tags")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            if (response != null && response.contains("\"models\"")) {
                return List.of(ollamaProperties.getModel());
            }
            return List.of();
        } catch (Exception e) {
            logger.error("Failed to get available models: {}", e.getMessage());
            return List.of();
        }
    }

    public boolean isModelAvailable(String modelName) {
        List<String> availableModels = getAvailableModels();
        return availableModels.contains(modelName);
    }

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
                String rawResponse = response.getResponse().trim();
                return cleanCommandResponse(rawResponse);
            }
        } catch (Exception e) {
            logger.error("Failed to generate command suggestion: {}", e.getMessage());
        }

        return null;
    }

    public String suggestCorrectCommand(String incorrectCommand) {
        String prompt = String.format(
                "The user entered the command '%s' which is incorrect. " +
                        "You must suggest the correct Linux/macOS terminal command. " +
                        "IMPORTANT: Respond with ONLY the exact command name and arguments, nothing else. " +
                        "Do NOT suggest script names, do NOT suggest file names, do NOT add explanations. " +
                        "Examples: 'colma start' -> 'colima start', 'lsl' -> 'ls', 'docker ps -a' -> 'docker ps -a'. " +
                        "If the command is a typo, fix the typo. If it's a wrong subcommand, suggest the correct subcommand. " +
                        "Respond with ONLY the corrected command, no markdown, no code blocks, no explanations. " +
                        "CRITICAL: Do NOT include ```bash, ```sh, or any backticks in your response.",
                incorrectCommand
        );

        return generateCommandSuggestion(incorrectCommand, prompt);
    }

    public String suggestCommandsForTask(String taskDescription) {
        String prompt = String.format(
                "The user wants to: %s. " +
                        "Please provide the most appropriate Linux/macOS terminal command(s) to accomplish this task. " +
                        "Format your response as a single line with the command(s), separated by && if multiple commands are needed. " +
                        "Do not include explanations, only the command(s), no markdown formatting, no code blocks. " +
                        "CRITICAL: Do NOT include ```bash, ```sh, or any backticks in your response.",
                taskDescription
        );

        return generateCommandSuggestion(taskDescription, prompt);
    }

    private String cleanCommandResponse(String response) {
        if (response == null || response.trim().isEmpty()) {
            return response;
        }

        String cleaned = response.trim();

        // Remove markdown code blocks with language specifiers (```bash, ```sh, etc.)
        // Handle both with and without spaces: ```bash command and ```bashcommand
        cleaned = cleaned.replaceAll("```(?:bash|sh|shell|zsh)?\\s*", "");
        cleaned = cleaned.replaceAll("```$", "");

        // Remove any remaining backticks at start or end
        cleaned = cleaned.replaceAll("^`+|`+$", "");

        // Remove any remaining markdown formatting
        cleaned = cleaned.replaceAll("\\*\\*(.*?)\\*\\*", "$1"); // Bold
        cleaned = cleaned.replaceAll("\\*(.*?)\\*", "$1"); // Italic
        cleaned = cleaned.replaceAll("`(.*?)`", "$1"); // Inline code

        // Clean up extra whitespace
        cleaned = cleaned.replaceAll("\\s+", " ").trim();

        // Remove common explanatory prefixes
        cleaned = cleaned.replaceAll("^(?:Command:|Suggestion:|Here's the command:|Use:|Run:)\\s*", "");

        return cleaned;
    }

    private String buildPrompt(String userInput, String context) {
        return String.format(
                "You are a Linux/macOS terminal expert. %s\n\n" +
                        "User input: %s\n\n" +
                        "Provide helpful, accurate terminal commands. Be concise and practical.",
                context, userInput
        );
    }

    public OllamaProperties getConfiguration() {
        return ollamaProperties;
    }
}
