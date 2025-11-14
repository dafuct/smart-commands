package com.smartcommands.dto;

import com.smartcommands.model.CommandHistory;
import lombok.Builder;

import java.util.List;

@Builder
public record HistoryResponse(List<CommandHistory> history,
                              Integer total,
                              Integer limit
) implements GeneralResponse {
}
