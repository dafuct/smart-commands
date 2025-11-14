package com.smartcommands.dto;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record BaseResponse(Boolean error, String message, LocalDateTime timestamp) implements  GeneralResponse {
}
