package com.gym.fitness.module.assistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gym.fitness.config.AiProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

@Slf4j
@Component
public class DeepSeekClient {
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");
    private static final Pattern ID_CARD = Pattern.compile("(?<!\\d)\\d{17}[0-9Xx](?!\\d)");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    private final AiProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Counter successCounter;
    private final Counter failureCounter;
    private final Counter fallbackCounter;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicReference<Instant> circuitOpenUntil = new AtomicReference<>(Instant.EPOCH);

    public DeepSeekClient(AiProperties properties, ObjectMapper objectMapper, MeterRegistry registry) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(1, properties.getConnectTimeoutSeconds())))
                .build();
        this.successCounter = Counter.builder("fitness_ai_requests_total").tag("outcome", "success").register(registry);
        this.failureCounter = Counter.builder("fitness_ai_requests_total").tag("outcome", "failure").register(registry);
        this.fallbackCounter = Counter.builder("fitness_ai_requests_total").tag("outcome", "fallback").register(registry);
    }

    public Optional<AiCompletion> complete(String systemPrompt, String userPrompt) {
        return complete(systemPrompt, List.of(), userPrompt);
    }

    public Optional<AiCompletion> complete(String systemPrompt, List<AiMessage> history, String userPrompt) {
        if (!isConfigured() || Instant.now().isBefore(circuitOpenUntil.get())) {
            fallbackCounter.increment();
            return Optional.empty();
        }
        try {
            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", systemPrompt));
            history.stream().limit(6).forEach(item -> messages.add(Map.of(
                    "role", item.role(),
                    "content", sanitize(item.content())
            )));
            messages.add(Map.of("role", "user", "content", sanitize(userPrompt)));
            Map<String, Object> payload = Map.of(
                    "model", properties.getModel(),
                    "messages", messages,
                    "thinking", Map.of("type", "disabled"),
                    "temperature", 0.3,
                    "max_tokens", Math.max(128, properties.getMaxTokens()),
                    "stream", false
            );
            HttpRequest request = HttpRequest.newBuilder(chatEndpoint())
                    .timeout(Duration.ofSeconds(Math.max(3, properties.getReadTimeoutSeconds())))
                    .header("Authorization", "Bearer " + properties.getApiKey().trim())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("DeepSeek returned HTTP " + response.statusCode());
            }
            JsonNode root = objectMapper.readTree(response.body());
            String content = root.path("choices").path(0).path("message").path("content").asText("").trim();
            if (content.isBlank()) {
                throw new IllegalStateException("DeepSeek returned an empty response");
            }
            int totalTokens = root.path("usage").path("total_tokens").asInt(0);
            consecutiveFailures.set(0);
            circuitOpenUntil.set(Instant.EPOCH);
            successCounter.increment();
            return Optional.of(new AiCompletion(content, properties.getModel(), totalTokens));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            registerFailure(e);
            return Optional.empty();
        } catch (Exception e) {
            registerFailure(e);
            return Optional.empty();
        }
    }

    public boolean isConfigured() {
        return properties.isEnabled() && properties.getApiKey() != null && !properties.getApiKey().isBlank();
    }

    public String modelName() {
        return properties.getModel();
    }

    private URI chatEndpoint() {
        String base = properties.getBaseUrl().replaceAll("/+$", "");
        URI uri = URI.create(base + "/chat/completions");
        if (!"https".equalsIgnoreCase(uri.getScheme())
                && !("http".equalsIgnoreCase(uri.getScheme()) && "localhost".equalsIgnoreCase(uri.getHost()))) {
            throw new IllegalArgumentException("AI base URL must use HTTPS");
        }
        return uri;
    }

    private String sanitize(String value) {
        String sanitized = PHONE.matcher(value).replaceAll("[手机号已脱敏]");
        sanitized = ID_CARD.matcher(sanitized).replaceAll("[证件号已脱敏]");
        return EMAIL.matcher(sanitized).replaceAll("[邮箱已脱敏]");
    }

    private void registerFailure(Exception error) {
        failureCounter.increment();
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= Math.max(1, properties.getCircuitFailureThreshold())) {
            circuitOpenUntil.set(Instant.now().plusSeconds(Math.max(10, properties.getCircuitOpenSeconds())));
            consecutiveFailures.set(0);
        }
        log.warn("DeepSeek request failed; using local fallback: {}", error.getMessage());
    }

    public record AiCompletion(String content, String model, int totalTokens) {
    }

    public record AiMessage(String role, String content) {
    }
}
