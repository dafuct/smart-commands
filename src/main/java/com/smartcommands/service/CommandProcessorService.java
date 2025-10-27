package com.smartcommands.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.smartcommands.model.CommandHistory;
import com.smartcommands.model.CommandSuggestion;
import com.smartcommands.repository.CommandHistoryRepository;

@Service
public class CommandProcessorService {
    private static final Logger logger = LoggerFactory.getLogger(CommandProcessorService.class);
    
    private final OllamaService ollamaService;
    private final CommandHistoryRepository commandHistoryRepository;
    private final IntelligentCommandValidator intelligentValidator;
    
    // Pattern to detect smart command requests
    private static final Pattern SMART_COMMAND_PATTERN = Pattern.compile("^sc\\s+['\"](.+?)['\"]\\s*$");
    
    // Circuit breaker for Ollama failures
    private static final int MAX_CONSECUTIVE_FAILURES = 3;
    private static final long CIRCUIT_BREAKER_RESET_TIME_MS = 60000; // 1 minute
    private final AtomicInteger consecutiveOllamaFailures = new AtomicInteger(0);
    private final AtomicLong lastFailureTimestamp = new AtomicLong(0);
    
    @Autowired
    public CommandProcessorService(OllamaService ollamaService, 
                                  CommandHistoryRepository commandHistoryRepository, 
                                  IntelligentCommandValidator intelligentValidator) {
        this.ollamaService = ollamaService;
        this.commandHistoryRepository = commandHistoryRepository;
        this.intelligentValidator = intelligentValidator;
    }

    /**
     * Process user input and determine if it's a smart command, incorrect command, or regular command
     * Enhanced with circuit breaker pattern and better error handling
     */
    public CommandSuggestion processInput(String userInput) {
        logger.debug("Processing input: {}", userInput);
        System.out.println("DEBUG: Processing input: " + userInput);
        
        // Validate input
        if (userInput == null || userInput.trim().isEmpty()) {
            return CommandSuggestion.error("Command cannot be empty");
        }
        
        String trimmedInput = userInput.trim();
        
        try {
            // Check if it's a smart command request
            if (isSmartCommand(trimmedInput)) {
                return handleSmartCommandWithFallback(trimmedInput);
            }
            
            // Skip validation for certain command types
            if (shouldSkipValidation(trimmedInput)) {
                logger.debug("Skipping validation for: {}", trimmedInput);
                System.out.println("DEBUG: Skipping validation for: " + trimmedInput);
                saveToHistory(trimmedInput, trimmedInput, "REGULAR");
                return CommandSuggestion.regularCommand(trimmedInput);
            }
            
            System.out.println("DEBUG: About to call intelligentValidator.validateAndCorrect");
            // Check circuit breaker before attempting Ollama validation
            if (isCircuitBreakerOpen()) {
                logger.warn("Circuit breaker open, using fallback validation directly");
                return intelligentValidator.fallbackValidation(trimmedInput);
            }
            
            // Try intelligent validation with Ollama
            if (intelligentValidator.isOllamaAvailable()) {
                try {
                    CommandSuggestion intelligentSuggestion = intelligentValidator.validateAndCorrect(trimmedInput);
                    
                    // Reset circuit breaker on success
                    resetCircuitBreaker();
                    
                    // Save to history based on the suggestion type
                    saveSuggestionToHistory(trimmedInput, intelligentSuggestion);
                    return intelligentSuggestion;
                    
                } catch (Exception e) {
                    logger.error("Ollama validation failed: {}", e.getMessage(), e);
                    recordOllamaFailure();
                    return intelligentValidator.fallbackValidation(trimmedInput);
                }
            }
            
            // Ollama not available, use fallback
            return intelligentValidator.fallbackValidation(trimmedInput);
            
        } catch (Exception e) {
            logger.error("Unexpected error processing command: {}", trimmedInput, e);
            return CommandSuggestion.error("Error processing command: " + e.getMessage());
        }
    }

    /**
     * Handle smart command with fallback
     */
    private CommandSuggestion handleSmartCommandWithFallback(String input) {
        try {
            CommandSuggestion suggestion = handleSmartCommand(input);
            saveSuggestionToHistory(input, suggestion);
            return suggestion;
        } catch (Exception e) {
            logger.error("Smart command processing failed: {}", e.getMessage(), e);
            recordOllamaFailure();
            return CommandSuggestion.error(
                "Failed to process smart command. Ollama may be unavailable. Error: " + e.getMessage()
            );
        }
    }

    /**
     * Process regular command with validation
     */
    private CommandSuggestion processRegularCommand(String trimmedInput) {
        if (shouldSkipValidation(trimmedInput)) {
            logger.debug("Skipping validation for: {}", trimmedInput);
            return CommandSuggestion.regularCommand(trimmedInput);
        }

        // Check if Ollama is available
        if (!ollamaService.isOllamaRunning()) {
            logger.debug("Ollama not running, using fallback validation");
            return intelligentValidator.fallbackValidation(trimmedInput);
        }

        try {
            // Use intelligent validator with circuit breaker
            CommandSuggestion suggestion = intelligentValidator.validateAndCorrect(trimmedInput);
            
            if (suggestion.isError()) {
                logger.debug("Validation returned error, trying fallback");
                return intelligentValidator.fallbackValidation(trimmedInput);
            }
            
            saveSuggestionToHistory(trimmedInput, suggestion);
            return suggestion;
            
        } catch (Exception e) {
            logger.error("Error during intelligent validation: {}", e.getMessage());
            return intelligentValidator.fallbackValidation(trimmedInput);
        }
    }

    /**
     * Save suggestion to history based on type
     */
    private void saveSuggestionToHistory(String originalInput, CommandSuggestion suggestion) {
        String type = "REGULAR";
        if (suggestion.needsCorrection()) {
            type = "CORRECTION";
        } else if (suggestion.isSmartCommand()) {
            type = "SMART_COMMAND";
        } else if (suggestion.isError()) {
            type = "ERROR";
        }
        
        saveToHistory(originalInput, suggestion.getSuggestion(), type);
    }

    /**
     * Check if circuit breaker should prevent Ollama calls
     */
    private boolean isCircuitBreakerOpen() {
        if (consecutiveOllamaFailures.get() < MAX_CONSECUTIVE_FAILURES) {
            return false;
        }
        
        // Check if enough time has passed to reset
        long timeSinceLastFailure = System.currentTimeMillis() - lastFailureTimestamp.get();
        if (timeSinceLastFailure > CIRCUIT_BREAKER_RESET_TIME_MS) {
            resetCircuitBreaker();
            return false;
        }
        
        return true;
    }

    /**
     * Record Ollama failure for circuit breaker
     */
    private void recordOllamaFailure() {
        int failures = consecutiveOllamaFailures.incrementAndGet();
        lastFailureTimestamp.set(System.currentTimeMillis());
        
        if (failures >= MAX_CONSECUTIVE_FAILURES) {
            logger.warn("Circuit breaker opened after {} consecutive Ollama failures", failures);
        }
    }

    /**
     * Reset circuit breaker on successful Ollama call
     */
    private void resetCircuitBreaker() {
        if (consecutiveOllamaFailures.get() > 0) {
            logger.info("Circuit breaker reset after successful Ollama call");
            consecutiveOllamaFailures.set(0);
        }
    }

    /**
     * Check if validation should be skipped for certain command types
     */
    private boolean shouldSkipValidation(String input) {
        String trimmed = input.trim();
        
        // Skip empty input or comments
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
            return true;
        }
        
        String firstWord = trimmed.split("\\s+")[0];
        
        // Skip local scripts and paths
        if (firstWord.startsWith("./") || firstWord.startsWith("/") || firstWord.contains("/")) {
            return true;
        }
        
        // Skip variable assignments
        if (trimmed.matches("^[A-Z_][A-Z0-9_]*=.*")) {
            return true;
        }
        
        // Skip pipes and redirections (complex shell constructs)
        if (trimmed.contains("|") || trimmed.contains(">") || trimmed.contains("<")) {
            // Only validate the first command in the pipe
            String firstCommand = trimmed.split("[|><]")[0].trim();
            if (firstCommand.isEmpty()) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Check if input is a smart command request
     */
    private boolean isSmartCommand(String input) {
        return SMART_COMMAND_PATTERN.matcher(input.trim()).matches();
    }

    /**
     * Handle smart command requests (e.g., "sc 'find big file'")
     */
    private CommandSuggestion handleSmartCommand(String input) {
        var matcher = SMART_COMMAND_PATTERN.matcher(input.trim());
        if (matcher.matches()) {
            String taskDescription = matcher.group(1);
            logger.info("Smart command request: {}", taskDescription);
            
            if (!ollamaService.isOllamaRunning()) {
                return CommandSuggestion.error(
                    "Ollama is not running. Please start Ollama to use smart commands. " +
                    "Run: ollama serve"
                );
            }
            
            try {
                String suggestion = ollamaService.suggestCommandsForTask(taskDescription);
                if (suggestion != null && !suggestion.trim().isEmpty()) {
                    return CommandSuggestion.smartCommand(taskDescription, suggestion);
                } else {
                    return CommandSuggestion.error(
                        "Failed to generate command suggestion. Please try rephrasing your request."
                    );
                }
            } catch (Exception e) {
                logger.error("Error generating smart command suggestion", e);
                throw new RuntimeException("Ollama request failed: " + e.getMessage(), e);
            }
        }
        
        return CommandSuggestion.error("Invalid smart command format. Use: sc 'describe what you want to do'");
    }

    /**
     * Get command history for context
     */
    public List<CommandHistory> getCommandHistory() {
        try {
            return commandHistoryRepository.findByExecutedTrueOrderByTimestampDesc();
        } catch (Exception e) {
            logger.error("Error fetching command history: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Save command to history
     */
    public void saveToHistory(String command, String suggestion, String type) {
        try {
            CommandHistory history = new CommandHistory();
            history.setOriginalCommand(command);
            history.setSuggestedCommand(suggestion);
            history.setSuggestionType(CommandSuggestion.SuggestionType.valueOf(type.toUpperCase()));
            history.setUserInput(command);
            history.setTimestamp(LocalDateTime.now());
            history.setExecuted(false);
            history.setSessionId(System.currentTimeMillis() + "");
            
            commandHistoryRepository.save(history);
            logger.debug("Saved to history - Command: {}, Suggestion: {}, Type: {}", command, suggestion, type);
        } catch (Exception e) {
            logger.error("Error saving to history: {}", e.getMessage());
        }
    }

    /**
     * Find command in database by exact match
     */
    public Optional<CommandHistory> findCommandInDatabase(String command) {
        try {
            List<CommandHistory> results = commandHistoryRepository.searchCommands(command);
            return results.stream().filter(ch -> ch.getOriginalCommand().equals(command)).findFirst();
        } catch (Exception e) {
            logger.error("Error searching command in database: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Find similar commands in database
     */
    public List<CommandHistory> findSimilarCommands(String command) {
        try {
            return commandHistoryRepository.searchCommands(command);
        } catch (Exception e) {
            logger.error("Error finding similar commands: {}", e.getMessage());
            return List.of();
        }
    }
}