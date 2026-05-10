package com.guide.pojo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ChatRequestDTO {
    @JsonProperty("session_id")
    private String sessionId;

    private String message;

    @JsonProperty("input_type")
    private String inputType = "text";

    @JsonProperty("audio_data")
    private String audioData;

    @JsonProperty("user_id")
    private Long userId;
}
