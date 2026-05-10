package com.guide.pojo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class RouteRecommendDTO {
    @JsonProperty("user_id")
    private Long userId;

    private List<String> interests;

    @JsonProperty("current_location")
    private Map<String, Object> currentLocation;

    @JsonProperty("duration_minutes")
    private Integer durationMinutes;
}
