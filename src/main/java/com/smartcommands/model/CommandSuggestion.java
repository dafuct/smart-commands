package com.smartcommands.model;

import java.time.LocalDateTime;

public class CommandSuggestion {
    public enum SuggestionType {
        REGULAR,      // Normal command, no suggestion needed
        CORRECTION,   // Incorrect command with correction
        SMART_COMMAND, // Smart command request with suggestion
        ERROR         // Error occurred
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
    
    // Getters
    public String getOriginalInput() {
        return originalInput;
    }
    
    public String getSuggestion() {
        return suggestion;
    }
    
    public SuggestionType getType() {
        return type;
    }
    
    public String getMessage() {
        return message;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
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
        switch (type) {
            case REGULAR:
                return "Command: " + originalInput;
            case CORRECTION:
                return "Correction: '" + originalInput + "' -> '" + suggestion + "'";
            case SMART_COMMAND:
                return "Smart Command: '" + originalInput + "' -> '" + suggestion + "'";
            case ERROR:
                return "Error: " + message;
            default:
                return "Unknown suggestion type";
        }
    }
}
