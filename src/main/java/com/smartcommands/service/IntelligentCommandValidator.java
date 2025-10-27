package com.smartcommands.service;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.smartcommands.model.CommandStructure;
import com.smartcommands.model.CommandSuggestion;
import com.smartcommands.parser.CommandParser;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


@Service
public class IntelligentCommandValidator {
    private static final Logger logger = LoggerFactory.getLogger(IntelligentCommandValidator.class);

    private final OllamaService ollamaService;

    @Getter
    private final CommandParser commandParser;
    @Getter
    private final StructuralCommandValidator structuralValidator;

    public IntelligentCommandValidator(
            OllamaService ollamaService,
            CommandParser commandParser,
            StructuralCommandValidator structuralValidator) {
        this.ollamaService = ollamaService;
        this.commandParser = commandParser;
        this.structuralValidator = structuralValidator;
    }

    public CommandSuggestion validateAndCorrect(String command) {
        logger.info("Validating command with three-tier strategy: {}", command);
        CommandSuggestion quickCorrection = tryQuickTypoCorrection(command);
        if (quickCorrection != null) {
            logger.info("Quick typo correction found: {}", quickCorrection.getSuggestion());
            return quickCorrection;
        }

        CommandStructure structure;
        try {
            structure = commandParser.parse(command);
        } catch (Exception e) {
            logger.error("Tier 1 - Failed to parse command: {}", command, e);
            return fallbackValidation(command);
        }

        Optional<CommandSuggestion> structuralValidation =
                structuralValidator.validateStructure(command);

        if (structuralValidation.isPresent()) {
            return structuralValidation.get();
        }

        boolean ollamaRunning = ollamaService.isOllamaRunning();

        if (ollamaRunning) {
            return validateWithOllama(command, structure);
        }

        return CommandSuggestion.regularCommand(command);
    }

    private CommandSuggestion tryQuickTypoCorrection(String command) {
        try {
            CommandStructure baseCorrected = commandParser.correctBaseCommandTypos(command);
            if (!baseCorrected.reconstruct().equals(command)) {
                if (baseCorrected.hasSubcommand()) {
                    CommandStructure fullyCorrected = commandParser.correctSubcommandTypos(
                            baseCorrected.reconstruct());
                    if (!fullyCorrected.reconstruct().equals(baseCorrected.reconstruct())) {
                        return CommandSuggestion.correction(command, fullyCorrected.reconstruct());
                    }
                }
                return CommandSuggestion.correction(command, baseCorrected.reconstruct());
            }

            CommandStructure subcommandCorrected = commandParser.correctSubcommandTypos(command);
            if (!subcommandCorrected.reconstruct().equals(command)) {
                return CommandSuggestion.correction(command, subcommandCorrected.reconstruct());
            }

        } catch (Exception e) {
            logger.error("Quick typo correction failed: {}", e.getMessage());
        }

        return null;
    }

    private CommandSuggestion validateWithOllama(String command, CommandStructure structure) {
        try {
            String prompt = buildEnhancedValidationPrompt(command, structure);
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

    private String buildEnhancedValidationPrompt(String command, CommandStructure structure) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("You are a command line expert. Analyze this command and respond in JSON format:\n\n");
        prompt.append("Command: ").append(command).append("\n\n");
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

    private CommandSuggestion parseOllamaResponse(String originalCommand, String response) {
        try {
            String cleanResponse = response.trim();
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

            String type = extractJsonValue(cleanResponse, "type");
            String suggestion = extractJsonValue(cleanResponse, "suggestion");
            String message = extractJsonValue(cleanResponse, "message");

            if (type == null) {
                logger.warn("Failed to parse type from response, command assumed valid");
                return CommandSuggestion.regularCommand(originalCommand);
            }

            switch (type.toUpperCase()) {
                case "VALID":
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

    private String extractJsonValue(String json, String key) {
        String pattern = "\"" + key + "\"\\s*:\\s*\"([^\"]*)\"";
        Pattern p = Pattern.compile(pattern, Pattern.DOTALL);
        Matcher m = p.matcher(json);

        if (m.find()) {
            return m.group(1);
        }

        return null;
    }

    public CommandSuggestion fallbackValidation(String command) {
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

        return CommandSuggestion.error(
                "Unknown command: '" + firstWord + "'. Type 'sc \"describe what you want to do\"' for help."
        );
    }

    public boolean isOllamaAvailable() {
        return ollamaService.isOllamaRunning();
    }
}