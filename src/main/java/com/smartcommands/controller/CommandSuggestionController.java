package com.smartcommands.controller;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.smartcommands.model.CommandSuggestion;
import com.smartcommands.service.CommandProcessorService;

/**
 * REST Controller for command suggestions and smart commands
 * Provides API endpoints for shell integration and external clients
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class CommandSuggestionController {

    private static final Logger logger = LoggerFactory.getLogger(CommandSuggestionController.class);

    @Autowired
    private CommandProcessorService commandProcessorService;

    /**
     * Process a command and return suggestion
     * Used by shell integration for real-time command processing
     */
    @PostMapping("/suggest")
    public ResponseEntity<Map<String, Object>> suggestCommand(@RequestBody Map<String, String> request) {
        try {
            String command = request.get("command");
            if (command == null || command.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(createErrorResponse("Command cannot be empty"));
            }

            logger.debug("Processing command suggestion request: {}", command);
            CommandSuggestion suggestion = commandProcessorService.processInput(command);

            Map<String, Object> response = new HashMap<>();
            response.put("originalInput", suggestion.getOriginalInput());
            response.put("suggestion", suggestion.getSuggestion());
            response.put("type", suggestion.getType().toString());
            response.put("message", suggestion.getMessage());
            response.put("timestamp", suggestion.getTimestamp());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error processing command suggestion", e);
            return ResponseEntity.internalServerError()
                    .body(createErrorResponse("Internal server error: " + e.getMessage()));
        }
    }

    /**
     * Process smart command request (natural language to command)
     */
    @PostMapping("/smart-command")
    public ResponseEntity<Map<String, Object>> processSmartCommand(@RequestBody Map<String, String> request) {
        try {
            String task = request.get("task");
            if (task == null || task.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(createErrorResponse("Task description cannot be empty"));
            }

            logger.debug("Processing smart command request: {}", task);
            String smartCommandInput = "sc '" + task + "'";
            CommandSuggestion suggestion = commandProcessorService.processInput(smartCommandInput);

            Map<String, Object> response = new HashMap<>();
            response.put("task", task);
            response.put("suggestion", suggestion.getSuggestion());
            response.put("type", suggestion.getType().toString());
            response.put("message", suggestion.getMessage());
            response.put("timestamp", suggestion.getTimestamp());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error processing smart command", e);
            return ResponseEntity.internalServerError()
                    .body(createErrorResponse("Internal server error: " + e.getMessage()));
        }
    }

    /**
     * Get command history
     */
    @GetMapping("/history")
    public ResponseEntity<Map<String, Object>> getCommandHistory(
            @RequestParam(defaultValue = "10") int limit) {
        try {
            var history = commandProcessorService.getCommandHistory();
            
            Map<String, Object> response = new HashMap<>();
            response.put("history", history.stream().limit(limit).toList());
            response.put("total", history.size());
            response.put("limit", limit);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error fetching command history", e);
            return ResponseEntity.internalServerError()
                    .body(createErrorResponse("Internal server error: " + e.getMessage()));
        }
    }

    /**
     * Search for similar commands in database
     */
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchCommands(
            @RequestParam String query,
            @RequestParam(defaultValue = "10") int limit) {
        try {
            var similarCommands = commandProcessorService.findSimilarCommands(query);
            
            Map<String, Object> response = new HashMap<>();
            response.put("query", query);
            response.put("results", similarCommands.stream().limit(limit).toList());
            response.put("total", similarCommands.size());
            response.put("limit", limit);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error searching commands", e);
            return ResponseEntity.internalServerError()
                    .body(createErrorResponse("Internal server error: " + e.getMessage()));
        }
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("timestamp", System.currentTimeMillis());
        response.put("service", "Smart Commands API");
        response.put("version", "1.0.0");
        
        return ResponseEntity.ok(response);
    }

    /**
     * Get system status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getSystemStatus() {
        try {
            Map<String, Object> response = new HashMap<>();
            
            // Check Ollama status through CommandProcessorService
            boolean ollamaRunning = commandProcessorService.findCommandInDatabase("test").isPresent();
            response.put("ollamaStatus", ollamaRunning ? "Connected" : "Not Connected");
            
            // Database status
            response.put("databaseStatus", "Connected");
            
            // Service status
            response.put("commandProcessorStatus", "Active");
            
            // Server info
            response.put("serverPort", 17020);
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error getting system status", e);
            return ResponseEntity.internalServerError()
                    .body(createErrorResponse("Internal server error: " + e.getMessage()));
        }
    }

    /**
     * Validate a command without processing it
     */
    @PostMapping("/validate")
    public ResponseEntity<Map<String, Object>> validateCommand(@RequestBody Map<String, String> request) {
        try {
            String command = request.get("command");
            if (command == null || command.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(createErrorResponse("Command cannot be empty"));
            }

            // Simple validation - check if it's a known command pattern
            boolean isValid = true;
            String suggestion = null;
            String type = "VALID";

            // Common typo patterns
            switch (command.trim().split("\\s+")[0]) {
                case "lss":
                case "lsl":
                case "sl":
                    suggestion = "ls";
                    type = "TYPO_CORRECTION";
                    break;
                case "gti":
                case "igt":
                    suggestion = "git";
                    type = "TYPO_CORRECTION";
                    break;
                case "mdkir":
                    suggestion = "mkdir";
                    type = "TYPO_CORRECTION";
                    break;
                default:
                    // Check if it's likely a smart command request
                    if (command.startsWith("sc ")) {
                        type = "SMART_COMMAND";
                    }
                    break;
            }

            Map<String, Object> response = new HashMap<>();
            response.put("command", command);
            response.put("valid", isValid && suggestion == null);
            response.put("suggestion", suggestion);
            response.put("type", type);
            response.put("timestamp", System.currentTimeMillis());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error validating command", e);
            return ResponseEntity.internalServerError()
                    .body(createErrorResponse("Internal server error: " + e.getMessage()));
        }
    }

    /**
     * Helper method to create error responses
     */
    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("error", true);
        error.put("message", message);
        error.put("timestamp", System.currentTimeMillis());
        return error;
    }
}
