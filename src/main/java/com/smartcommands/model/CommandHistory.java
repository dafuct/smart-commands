package com.smartcommands.model;

import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "command_history")
public class CommandHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "original_command", nullable = false, length = 1000)
    private String originalCommand;
    
    @Column(name = "suggested_command", length = 1000)
    private String suggestedCommand;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "suggestion_type", nullable = false)
    private CommandSuggestion.SuggestionType suggestionType;
    
    @Column(name = "user_input", nullable = false, length = 1000)
    private String userInput;
    
    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;
    
    @Column(name = "executed", nullable = false)
    private boolean executed = false;
    
    @Column(name = "execution_result", length = 2000)
    private String executionResult;
    
    @Column(name = "session_id", length = 100)
    private String sessionId;
    
    // Default constructor
    public CommandHistory() {
        this.timestamp = LocalDateTime.now();
    }
    
    // Constructor with essential fields
    public CommandHistory(String originalCommand, String suggestedCommand, 
                         CommandSuggestion.SuggestionType suggestionType, String userInput) {
        this();
        this.originalCommand = originalCommand;
        this.suggestedCommand = suggestedCommand;
        this.suggestionType = suggestionType;
        this.userInput = userInput;
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getOriginalCommand() {
        return originalCommand;
    }
    
    public void setOriginalCommand(String originalCommand) {
        this.originalCommand = originalCommand;
    }
    
    public String getSuggestedCommand() {
        return suggestedCommand;
    }
    
    public void setSuggestedCommand(String suggestedCommand) {
        this.suggestedCommand = suggestedCommand;
    }
    
    public CommandSuggestion.SuggestionType getSuggestionType() {
        return suggestionType;
    }
    
    public void setSuggestionType(CommandSuggestion.SuggestionType suggestionType) {
        this.suggestionType = suggestionType;
    }
    
    public String getUserInput() {
        return userInput;
    }
    
    public void setUserInput(String userInput) {
        this.userInput = userInput;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
    
    public boolean isExecuted() {
        return executed;
    }
    
    public void setExecuted(boolean executed) {
        this.executed = executed;
    }
    
    public String getExecutionResult() {
        return executionResult;
    }
    
    public void setExecutionResult(String executionResult) {
        this.executionResult = executionResult;
    }
    
    public String getSessionId() {
        return sessionId;
    }
    
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
    
    @Override
    public String toString() {
        return "CommandHistory{" +
                "id=" + id +
                ", originalCommand='" + originalCommand + '\'' +
                ", suggestedCommand='" + suggestedCommand + '\'' +
                ", suggestionType=" + suggestionType +
                ", userInput='" + userInput + '\'' +
                ", timestamp=" + timestamp +
                ", executed=" + executed +
                '}';
    }
}
