package com.smartcommands.model;

import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class CommandSuggestion {
    public enum SuggestionType {
        REGULAR,
        CORRECTION,
        SMART_COMMAND,
        ERROR
    }

    private final String originalInput;
    private final String suggestion;
    private final SuggestionType type;
    private final String message;
    private final LocalDateTime timestamp;

    private CommandSuggestion(String originalInput, String suggestion, SuggestionType type, String message) {
        this.originalInput = originalInput;
        this.suggestion = suggestion;
        this.type = type;
        this.message = message;
        this.timestamp = LocalDateTime.now();
    }

    public static CommandSuggestion regularCommand(String command) {
        return new CommandSuggestion(command, null, SuggestionType.REGULAR, null);
    }

    public static CommandSuggestion correction(String incorrectCommand, String correctCommand) {
        return new CommandSuggestion(incorrectCommand, correctCommand, SuggestionType.CORRECTION,
                "Did you mean: " + correctCommand + "?");
    }

    public static CommandSuggestion smartCommand(String taskDescription, String command) {
        return new CommandSuggestion(taskDescription, command, SuggestionType.SMART_COMMAND,
                "Suggested command: " + command);
    }

    public static CommandSuggestion error(String errorMessage) {
        return new CommandSuggestion(null, null, SuggestionType.ERROR, errorMessage);
    }

    public boolean hasSuggestion() {
        return suggestion != null && !suggestion.trim().isEmpty();
    }

    public boolean isError() {
        return type == SuggestionType.ERROR;
    }

    public boolean needsCorrection() {
        return type == SuggestionType.CORRECTION;
    }

    public boolean isSmartCommand() {
        return type == SuggestionType.SMART_COMMAND;
    }

    @Override
    public String toString() {
        return switch (type) {
            case REGULAR -> "Command: " + originalInput;
            case CORRECTION -> "Correction: '" + originalInput + "' -> '" + suggestion + "'";
            case SMART_COMMAND -> "Smart Command: '" + originalInput + "' -> '" + suggestion + "'";
            case ERROR -> "Error: " + message;
        };
    }
}
