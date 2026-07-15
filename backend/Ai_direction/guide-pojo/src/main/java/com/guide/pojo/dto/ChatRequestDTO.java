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

    @JsonProperty("language")
    private String language;

    @JsonProperty("age")
    private Integer age;

    @JsonProperty("gender")
    private String gender;

    @JsonProperty("location")
    private String location;

    @JsonProperty("latitude")
    private Double latitude;

    @JsonProperty("longitude")
    private Double longitude;
}
