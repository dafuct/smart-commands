package com.smartcommands.dto;

import com.smartcommands.model.CommandHistory;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Builder
public record SuggestCommandResponse(String originalInput,
                                     String suggestion,
                                     String type,
                                     String message,
                                     String task,
                                     List<CommandHistory> history,
                                     List<CommandHistory> results,
                                     String query,
                                     String command,
                                     Boolean valid,
                                     Integer total,
                                     Integer limit,
                                     String status,
                                     String ollamaStatus,
                                     String databaseStatus,
                                     String commandProcessorStatus,
                                     Integer serverPort,
                                     String version,
                                     String service,
                                     LocalDateTime timestamp) implements GeneralResponse {
}
