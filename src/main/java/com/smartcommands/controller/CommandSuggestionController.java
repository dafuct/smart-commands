package com.smartcommands.controller;

import com.smartcommands.dto.BaseResponse;
import com.smartcommands.dto.GeneralResponse;
import com.smartcommands.mapper.SmartCommandsMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class CommandSuggestionController {

    private static final Logger logger = LoggerFactory.getLogger(CommandSuggestionController.class);

    private final SmartCommandsMapper smartCommandsMapper;

    public CommandSuggestionController(SmartCommandsMapper smartCommandsMapper) {
        this.smartCommandsMapper = smartCommandsMapper;
    }

    @PostMapping("/suggest")
    public ResponseEntity<GeneralResponse> suggestCommand(@RequestBody Map<String, String> request) {
        try {
            return Optional.ofNullable(request.get("command"))
                    .filter(value -> !value.trim().isEmpty())
                    .map(value -> ResponseEntity.ok(smartCommandsMapper.mapToSuggestCommand(request)))
                    .orElseGet(() -> ResponseEntity.badRequest().body(createErrorResponse("Command cannot be empty")));
        } catch (Exception e) {
            logger.error("Error processing command suggestion", e);
            return ResponseEntity.internalServerError()
                    .body(createErrorResponse("Internal server error: " + e.getMessage()));
        }
    }

    @PostMapping("/smart-command")
    public ResponseEntity<GeneralResponse> processSmartCommand(@RequestBody Map<String, String> request) {
        try {
            return Optional.ofNullable(request.get("task"))
                    .filter(value -> !value.trim().isEmpty())
                    .map(value -> ResponseEntity.ok(smartCommandsMapper.mapToSmartCommand(request)))
                    .orElseGet(() -> ResponseEntity.badRequest().body(createErrorResponse("Task description cannot be empty")));
        } catch (Exception e) {
            logger.error("Error processing smart command", e);
            return ResponseEntity.internalServerError()
                    .body(createErrorResponse("Internal server error: " + e.getMessage()));
        }
    }

    @GetMapping("/history")
    public ResponseEntity<GeneralResponse> getCommandHistory(
            @RequestParam(defaultValue = "10") int limit) {
        try {
            return ResponseEntity.ok(smartCommandsMapper.mapToHistory(limit));
        } catch (Exception e) {
            logger.error("Error fetching command history", e);
            return ResponseEntity.internalServerError()
                    .body(createErrorResponse("Internal server error: " + e.getMessage()));
        }
    }

    @GetMapping("/search")
    public ResponseEntity<GeneralResponse> searchCommands(
            @RequestParam String query,
            @RequestParam(defaultValue = "10") int limit) {
        try {
            return ResponseEntity.ok(smartCommandsMapper.mapToSearchCommand(query, limit));
        } catch (Exception e) {
            logger.error("Error searching commands", e);
            return ResponseEntity.internalServerError()
                    .body(createErrorResponse("Internal server error: " + e.getMessage()));
        }
    }

    @GetMapping("/health")
    public ResponseEntity<GeneralResponse> healthCheck() {
        return ResponseEntity.ok(smartCommandsMapper.mapToHealth());
    }

    @GetMapping("/status")
    public ResponseEntity<GeneralResponse> getSystemStatus() {
        try {
            return ResponseEntity.ok(smartCommandsMapper.mapToSystemStatus());
        } catch (Exception e) {
            logger.error("Error getting system status", e);
            return ResponseEntity.internalServerError()
                    .body(createErrorResponse("Internal server error: " + e.getMessage()));
        }
    }

    @PostMapping("/validate")
    public ResponseEntity<GeneralResponse> validateCommand(@RequestBody Map<String, String> request) {
        try {
            return Optional.ofNullable(request.get("command"))
                    .filter(value -> !value.trim().isEmpty())
                    .map(value -> ResponseEntity.ok(smartCommandsMapper.mapToValidate(request)))
                    .orElseGet(() -> ResponseEntity.badRequest().body(createErrorResponse("Command cannot be empty")));
        } catch (Exception e) {
            logger.error("Error validating command", e);
            return ResponseEntity.internalServerError()
                    .body(createErrorResponse("Internal server error: " + e.getMessage()));
        }
    }

    private BaseResponse createErrorResponse(String message) {
        return BaseResponse.builder()
                .error(Boolean.TRUE)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
