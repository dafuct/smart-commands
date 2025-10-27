package com.smartcommands.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.smartcommands.model.CommandStructure;
import com.smartcommands.model.CommandSuggestion;
import com.smartcommands.parser.CommandParser;

import java.util.Optional;

/**
 * Intelligent command validator using three-tier validation strategy:
 * Tier 1: Structural parsing (CommandParser)
 * Tier 2: Reference-based validation (StructuralCommandValidator)
 * Tier 3: AI-enhanced validation (Ollama)
 *
 * This architecture ensures that obvious errors (like "docker sp -a") are caught
 * even if the AI model doesn't detect them.
 */
@Service
public class IntelligentCommandValidator {
    private static final Logger logger = LoggerFactory.getLogger(IntelligentCommandValidator.class);
    
    private final OllamaService ollamaService;
    private final CommandParser commandParser;
    private final StructuralCommandValidator structuralValidator;
    
    @Autowired
    public IntelligentCommandValidator(
            OllamaService ollamaService,
            CommandParser commandParser,
            StructuralCommandValidator structuralValidator) {
        this.ollamaService = ollamaService;
        this.commandParser = commandParser;
        this.structuralValidator = structuralValidator;
    }
    
    /**
     * Validate command using three-tier strategy and return suggestion
     */
    public CommandSuggestion validateAndCorrect(String command) {
        logger.info("Validating command with three-tier strategy: {}", command);
        System.out.println("DEBUG: Validating command: " + command);
        
        // FAST PATH: Try quick typo correction first
        CommandSuggestion quickCorrection = tryQuickTypoCorrection(command);
        if (quickCorrection != null) {
            logger.info("Quick typo correction found: {}", quickCorrection.getSuggestion());
            return quickCorrection;
        }
        
        // TIER 1: Parse command structure
        CommandStructure structure;
        try {
            structure = commandParser.parse(command);
            logger.debug("Tier 1 - Parsed structure: {}", structure);
        } catch (Exception e) {
            logger.error("Tier 1 - Failed to parse command: {}", command, e);
            return fallbackValidation(command);
        }
        
        // TIER 2: Structural validation against metadata
        Optional<CommandSuggestion> structuralValidation = 
            structuralValidator.validateStructure(command);
            
        if (structuralValidation.isPresent()) {
            logger.info("Tier 2 - Structural validation found issue, returning correction");
            return structuralValidation.get();
        }
        
        logger.debug("Tier 2 - Structural validation passed");
        
        // TIER 3: AI validation (only if Ollama available and previous tiers passed)
        boolean ollamaRunning = ollamaService.isOllamaRunning();
        logger.debug("Tier 3 - Ollama running status: {}", ollamaRunning);
        
        if (ollamaRunning) {
            logger.info("Tier 3 - Using Ollama for semantic validation");
            return validateWithOllama(command, structure);
        }
        
        // All tiers passed or unavailable - command is considered valid
        logger.info("All validation tiers passed for: {}", command);
        return CommandSuggestion.regularCommand(command);
    }

    /**
     * Fast path for common typo corrections
     * This handles the most common cases without expensive parsing or validation
     */
    private CommandSuggestion tryQuickTypoCorrection(String command) {
        System.out.println("DEBUG: Trying quick typo correction for: " + command);
        try {
            // First try base command correction
            CommandStructure baseCorrected = commandParser.correctBaseCommandTypos(command);
            System.out.println("DEBUG: Base corrected: " + baseCorrected.reconstruct());
            if (!baseCorrected.reconstruct().equals(command)) {
                // Base command was corrected, check if subcommand also needs correction
                if (baseCorrected.hasSubcommand()) {
                    CommandStructure fullyCorrected = commandParser.correctSubcommandTypos(
                        baseCorrected.reconstruct());
                    System.out.println("DEBUG: Fully corrected: " + fullyCorrected.reconstruct());
                    if (!fullyCorrected.reconstruct().equals(baseCorrected.reconstruct())) {
                        return CommandSuggestion.correction(command, fullyCorrected.reconstruct());
                    }
                }
                return CommandSuggestion.correction(command, baseCorrected.reconstruct());
            }
            
            // Then try subcommand correction
            CommandStructure subcommandCorrected = commandParser.correctSubcommandTypos(command);
            System.out.println("DEBUG: Subcommand corrected: " + subcommandCorrected.reconstruct());
            if (!subcommandCorrected.reconstruct().equals(command)) {
                return CommandSuggestion.correction(command, subcommandCorrected.reconstruct());
            }
            
        } catch (Exception e) {
            System.out.println("DEBUG: Quick typo correction failed: " + e.getMessage());
            logger.debug("Quick typo correction failed: {}", e.getMessage());
        }
        
        System.out.println("DEBUG: No quick correction found");
        return null; // No quick correction found
    }
    
    /**
     * Use Ollama for semantic validation (Tier 3)
     * Enhanced with structural context from previous tiers
     */
    private CommandSuggestion validateWithOllama(String command, CommandStructure structure) {
        try {
            String prompt = buildEnhancedValidationPrompt(command, structure);
            logger.debug("Calling Ollama with enhanced prompt");
            String response = ollamaService.generateCommandSuggestion(command, prompt);
            
            if (response == null || response.trim().isEmpty()) {
                logger.warn("Ollama returned empty response, command assumed valid");
                return CommandSuggestion.regularCommand(command);
            }
            
            logger.info("Ollama response received, length: {}", response.length());
            return parseOllamaResponse(command, response);
            
        } catch (Exception e) {
            logger.error("Error validating command with Ollama: {}", command, e);
            return CommandSuggestion.regularCommand(command);
        }
    }
    
    /**
     * Build enhanced validation prompt with structural context
     * This provides better context to the AI model about what to validate
     */
    private String buildEnhancedValidationPrompt(String command, CommandStructure structure) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("You are a command line expert. Analyze this command and respond in JSON format:\n\n");
        prompt.append("Command: ").append(command).append("\n\n");
        
        // Add structural context
        prompt.append("Command structure analysis:\n");
        prompt.append("- Base command: ").append(structure.getBaseCommand()).append("\n");
        if (structure.hasSubcommand()) {
            prompt.append("- Subcommand: ").append(structure.getSubcommand()).append("\n");
        }
        if (structure.hasFlags()) {
            prompt.append("- Flags: ").append(structure.getFlags()).append("\n");
        }
        prompt.append("\n");
        
        prompt.append("Respond with exactly one of these JSON objects:\n\n");
        prompt.append("1. If command is correct:\n");
        prompt.append("{\"type\":\"VALID\",\"message\":\"Command is correct\"}\n\n");
        prompt.append("2. If command has typos or errors:\n");
        prompt.append("{\"type\":\"CORRECTION\",\"suggestion\":\"corrected_command\",\"message\":\"Brief explanation\"}\n\n");
        prompt.append("3. If you have alternative suggestions:\n");
        prompt.append("{\"type\":\"SUGGESTION\",\"suggestion\":\"suggested_command\",\"message\":\"Did you mean...?\"}\n\n");
        
        prompt.append("VALIDATION FOCUS:\n");
        prompt.append("- Validate semantic correctness (will this command do what's intended?)\n");
        prompt.append("- Check for runtime issues (missing required flags, wrong flag combinations)\n");
        prompt.append("- Verify argument ordering and syntax\n");
        prompt.append("- Suggest improvements for clarity or efficiency\n");
        prompt.append("- Structural validation already passed, focus on semantic issues\n");
        prompt.append("- Response must be valid JSON only, no extra text\n");
        
        return prompt.toString();
    }
    
    /**
     * Parse Ollama response and create CommandSuggestion
     */
    private CommandSuggestion parseOllamaResponse(String originalCommand, String response) {
        try {
            logger.debug("Raw Ollama response for '{}': {}", originalCommand, response);
            
            // Clean response to ensure it's valid JSON
            String cleanResponse = response.trim();
            
            // Remove any markdown code blocks if present
            if (cleanResponse.startsWith("```json")) {
                cleanResponse = cleanResponse.substring(7);
            }
            if (cleanResponse.startsWith("```sh")) {
                cleanResponse = cleanResponse.substring(5);
            }
            if (cleanResponse.startsWith("```")) {
                cleanResponse = cleanResponse.substring(3);
            }
            if (cleanResponse.endsWith("```")) {
                cleanResponse = cleanResponse.substring(0, cleanResponse.length() - 3);
            }
            cleanResponse = cleanResponse.trim();
            
            logger.debug("Cleaned Ollama response: {}", cleanResponse);
            
            // Simple JSON parsing (avoiding external dependencies)
            String type = extractJsonValue(cleanResponse, "type");
            String suggestion = extractJsonValue(cleanResponse, "suggestion");
            String message = extractJsonValue(cleanResponse, "message");
            
            logger.debug("Parsed values - type: {}, suggestion: {}, message: {}", type, suggestion, message);
            
            if (type == null) {
                logger.warn("Failed to parse type from response, command assumed valid");
                return CommandSuggestion.regularCommand(originalCommand);
            }
            
            switch (type.toUpperCase()) {
                case "VALID":
                    logger.debug("AI validation: command is valid");
                    return CommandSuggestion.regularCommand(originalCommand);
                    
                case "CORRECTION":
                    if (suggestion != null && !suggestion.isEmpty()) {
                        logger.info("AI suggests correction: {} -> {}", originalCommand, suggestion);
                        return CommandSuggestion.correction(originalCommand, suggestion);
                    }
                    break;
                    
                case "SUGGESTION":
                    if (suggestion != null && !suggestion.isEmpty()) {
                        logger.info("AI provides suggestion: {} -> {}", originalCommand, suggestion);
                        return CommandSuggestion.correction(originalCommand, suggestion);
                    }
                    break;
            }
            
        } catch (Exception e) {
            logger.error("Error parsing Ollama response: {}", response, e);
        }
        
        logger.warn("Parsing failed or no valid suggestion, command assumed valid");
        return CommandSuggestion.regularCommand(originalCommand);
    }
    
    /**
     * Extract JSON value by key (simple implementation)
     */
    private String extractJsonValue(String json, String key) {
        String pattern = "\"" + key + "\"\\s*:\\s*\"([^\"]*)\"";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern, java.util.regex.Pattern.DOTALL);
        java.util.regex.Matcher m = p.matcher(json);
        
        if (m.find()) {
            return m.group(1);
        }
        
        return null;
    }
    
    /**
     * Fallback validation when parsing fails
     */
    public CommandSuggestion fallbackValidation(String command) {
        logger.debug("Using fallback validation for: {}", command);
        
        // Basic validation - if it's a known command, consider it valid
        String[] commonCommands = {"ls", "cd", "pwd", "mkdir", "rm", "cp", "mv", "cat", "grep", 
                                  "find", "chmod", "chown", "ps", "kill", "top", "df", "du", 
                                  "tar", "gzip", "ssh", "scp", "wget", "curl", "git", "npm", 
                                  "pip", "docker", "kubectl", "java", "python", "node", "mvn", 
                                  "gradle", "echo", "date", "whoami", "id", "uname", "which"};
        
        String firstWord = command.trim().split("\\s+")[0];
        
        for (String commonCommand : commonCommands) {
            if (commonCommand.equals(firstWord)) {
                return CommandSuggestion.regularCommand(command);
            }
        }
        
        // If not a common command, suggest it might be a typo or need help
        return CommandSuggestion.error(
            "Unknown command: '" + firstWord + "'. Type 'sc \"describe what you want to do\"' for help."
        );
    }
    
    /**
     * Check if Ollama is available for validation
     */
    public boolean isOllamaAvailable() {
        return ollamaService.isOllamaRunning();
    }
    
    /**
     * Get command parser for direct access
     */
    public CommandParser getCommandParser() {
        return commandParser;
    }
    
    /**
     * Get structural validator for direct access
     */
    public StructuralCommandValidator getStructuralValidator() {
        return structuralValidator;
    }
}