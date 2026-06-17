package com.guide.pojo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

@Data
public class TtsRequestDTO {
    private String text;

    @JsonProperty("voice_id")
    private String voiceId;

    private Double speed;
    private String emotion;
    private String mode;

    @JsonProperty("extra_params")
    private Map<String, Object> extraParams;
}
