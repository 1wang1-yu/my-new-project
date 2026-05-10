package com.guide.pojo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class TtsRequestDTO {
    private String text;

    @JsonProperty("voice_id")
    private String voiceId;

    private Double speed;
    private String emotion;
}
