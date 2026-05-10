package com.guide.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record RouteRecommendRequest(
        @NotBlank String userId,
        @NotBlank String scenicId,
        List<String> interests,
        Integer durationHours,
        String preference
) {
}