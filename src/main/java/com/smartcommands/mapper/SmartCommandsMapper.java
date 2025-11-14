package com.smartcommands.mapper;

import com.smartcommands.dto.GeneralResponse;
import com.smartcommands.dto.HistoryResponse;
import com.smartcommands.dto.SuggestCommandResponse;
import com.smartcommands.model.CommandSuggestion;
import com.smartcommands.service.CommandProcessorService;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@Component
public class SmartCommandsMapper {

    private final CommandProcessorService commandProcessorService;

    public SmartCommandsMapper(CommandProcessorService commandProcessorService) {
        this.commandProcessorService = commandProcessorService;
    }

    public GeneralResponse mapToSuggestCommand(Map<String, String> request) {
        CommandSuggestion suggestion = commandProcessorService.processInput(request.get("command"));

        SuggestCommandResponse.SuggestCommandResponseBuilder builder = getSuggestCommandResponseBuilder();
        Optional.ofNullable(suggestion.getOriginalInput()).ifPresent(builder::originalInput);
        return populateSuggestCommandResponse(suggestion, builder);
    }

    public GeneralResponse mapToSmartCommand(Map<String, String> request) {
        String task = request.get("task");
        String smartCommandInput = "sc '" + task + "'";
        CommandSuggestion suggestion = commandProcessorService.processInput(smartCommandInput);

        SuggestCommandResponse.SuggestCommandResponseBuilder builder = getSuggestCommandResponseBuilder();

        Optional.of(task).ifPresent(builder::task);
        return populateSuggestCommandResponse(suggestion, builder);
    }

    public GeneralResponse mapToHistory(int limit) {
        var history = commandProcessorService.getCommandHistory();

        return HistoryResponse.builder()
                .history(history.stream().limit(limit).toList())
                .total(history.size())
                .limit(limit)
                .build();
    }

    public GeneralResponse mapToSearchCommand(String query, int limit) {
        var similarCommands = commandProcessorService.findSimilarCommands(query);

        return SuggestCommandResponse.builder()
                .query(query)
                .results(similarCommands.stream().limit(limit).toList())
                .total(similarCommands.size())
                .limit(limit)
                .build();
    }

    public GeneralResponse mapToHealth() {
        return SuggestCommandResponse.builder()
                .status("UP")
                .timestamp(LocalDateTime.now())
                .service("Smart Commands API")
                .version("1.0.0")
                .build();
    }

    public GeneralResponse mapToSystemStatus() {
        boolean ollamaRunning = commandProcessorService.findCommandInDatabase("test").isPresent();
        return SuggestCommandResponse.builder()
                .ollamaStatus(ollamaRunning ? "Connected" : "Not Connected")
                .databaseStatus("Connected")
                .commandProcessorStatus("Active")
                .serverPort(17020)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public GeneralResponse mapToValidate(Map<String, String> request) {
        String command = request.get("command");
        if (command == null || command.trim().isEmpty()) {
            return createErrorResponse();
        }

        String suggestion = null;
        String type = "VALID";

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
                if (command.startsWith("sc ")) {
                    type = "SMART_COMMAND";
                }
                break;
        }

        return SuggestCommandResponse.builder()
                .command(command)
                .valid(suggestion == null)
                .suggestion(suggestion)
                .type(type)
                .timestamp(LocalDateTime.now())
                .build();

    }

    private SuggestCommandResponse populateSuggestCommandResponse(CommandSuggestion suggestion, SuggestCommandResponse.SuggestCommandResponseBuilder builder) {
        Optional.ofNullable(suggestion.getSuggestion()).ifPresent(builder::suggestion);
        Optional.ofNullable(suggestion.getType()).ifPresent(type -> builder.type(type.name()));
        Optional.ofNullable(suggestion.getMessage()).ifPresent(builder::message);
        Optional.ofNullable(suggestion.getTimestamp()).ifPresent(builder::timestamp);

        return builder.build();
    }

    private SuggestCommandResponse.SuggestCommandResponseBuilder getSuggestCommandResponseBuilder() {
        return SuggestCommandResponse.builder();
    }

    private SuggestCommandResponse createErrorResponse() {
        return SuggestCommandResponse.builder()
                .command(null)
                .valid(false)
                .message("Command cannot be empty")
                .timestamp(LocalDateTime.now())
                .build();
    }
}
