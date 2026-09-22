package com.gym.fitness.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {
    private boolean enabled = true;
    private String baseUrl = "https://api.deepseek.com";
    private String apiKey = "";
    private String model = "deepseek-v4-flash";
    private int connectTimeoutSeconds = 5;
    private int readTimeoutSeconds = 25;
    private int maxTokens = 700;
    private int requestsPerMinute = 10;
    private int circuitFailureThreshold = 3;
    private int circuitOpenSeconds = 60;
}
