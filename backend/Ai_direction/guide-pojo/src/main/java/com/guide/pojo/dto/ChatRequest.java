package com.guide.pojo.dto;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(
        @NotBlank String userId,
        String digitalId,
        @NotBlank String sessionKey,
        @NotBlank String message,
        boolean queryEmbedding
) {
}