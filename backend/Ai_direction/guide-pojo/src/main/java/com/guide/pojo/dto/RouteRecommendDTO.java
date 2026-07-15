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

    /** 用户自定义输入的偏好描述（如"我喜欢安静一点的地方"） */
    @JsonProperty("custom_preference")
    private String customPreference;

    /** 用户选择的想去的景点列表 */
    @JsonProperty("selected_attractions")
    private List<String> selectedAttractions;

    /** 会话ID，用于从聊天记录中提取偏好 */
    @JsonProperty("session_id")
    private String sessionId;

    @JsonProperty("current_location")
    private Map<String, Object> currentLocation;

    @JsonProperty("duration_minutes")
    private Integer durationMinutes;

    @JsonProperty("people_count")
    private Integer peopleCount;

    @JsonProperty("start_time")
    private String startTime;

    @JsonProperty("end_time")
    private String endTime;
}
