package com.guide.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "guide")
public class GuideProperties {

    private Whisper whisper = new Whisper();
    private Chroma chroma = new Chroma();
    private Tts tts = new Tts();
    private TencentCloud tencentCloud = new TencentCloud();

    @Data
    public static class Whisper {
        private String baseUrl = "https://api.openai.com";
        private String apiKey = "";
        private String transcribePath = "/v1/audio/transcriptions";
        private String model = "whisper-1";
    }

    @Data
    public static class Chroma {
        private String baseUrl = "http://localhost:8000";
        private String defaultCollection = "travel_kb";
    }

    @Data
    public static class Tts {
        private String baseUrl = "";
        private String apiKey = "";
    }

    @Data
    public static class TencentCloud {
        private String secretId = "";
        private String secretKey = "";
        private String region = "ap-guangzhou";
        private String ttsEndpoint = "tts.tencentcloudapi.com";
        private String virtualmanKey = "";
        private String appKey = "";
        private String accessToken = "";
    }
}
