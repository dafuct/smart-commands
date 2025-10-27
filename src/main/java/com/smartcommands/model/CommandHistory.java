package com.smartcommands.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import javax.persistence.*;
import java.time.LocalDateTime;

@Setter
@Getter
@ToString
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

    public CommandHistory() {
        this.timestamp = LocalDateTime.now();
    }

    public CommandHistory(String originalCommand, String suggestedCommand, 
                         CommandSuggestion.SuggestionType suggestionType, String userInput) {
        this();
        this.originalCommand = originalCommand;
        this.suggestedCommand = suggestedCommand;
        this.suggestionType = suggestionType;
        this.userInput = userInput;
    }
}
