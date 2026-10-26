package com.example.commerce.api;

import com.example.commerce.domain.CommerceCommand;
import jakarta.validation.constraints.NotBlank;

/** Explicit command fields override NLP; pass one stable conversationId for all turns. */
public record ChatRequest(
    @NotBlank String conversationId,
    String message,
    CommerceCommand command,
    String query,
    String productReference,
    Integer quantity
) {}
