package com.gym.fitness.module.assistant.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.gym.fitness.config.AiProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/** Bounded, tool-aware provider transport. Business execution belongs to the agent service. */
@Component
public class AgentModelClient {
    private static final int MAX_RESPONSE_BYTES = 65_536;
    private static final int MAX_REQUEST_BYTES = 98_304;
    private static final int MAX_CONTENT_CHARS = 16_384;
    private static final int MAX_ARGUMENT_CHARS = 8_192;
    private static final int MAX_CALLS = 6;
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9_-]{1,128}");
    private static final Pattern FUNCTION_NAME = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");
    private static final Pattern ID_CARD = Pattern.compile("(?<!\\d)\\d{17}[0-9Xx](?!\\d)");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    private final AiProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient http;
    private final Counter successes;
    private final Counter failures;
    private final Counter fallbacks;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicReference<Instant> circuitOpenUntil = new AtomicReference<>(Instant.EPOCH);
    private final AtomicReference<String> state = new AtomicReference<>("unknown");
    private final AtomicBoolean toolCalling = new AtomicBoolean();
    private Instant rateWindow = Instant.EPOCH;
    private int requestsInWindow;

    @Autowired
    public AgentModelClient(AiProperties properties, ObjectMapper mapper, MeterRegistry registry) {
        this(properties, mapper, registry, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(bounded(properties.getConnectTimeoutSeconds(), 1, 30)))
                .followRedirects(HttpClient.Redirect.NEVER).build());
    }

    AgentModelClient(AiProperties properties, ObjectMapper mapper, MeterRegistry registry, HttpClient http) {
        this.properties = properties;
        this.mapper = mapper;
        this.http = http;
        successes = Counter.builder("fitness_agent_model_requests_total").tag("outcome", "success").register(registry);
        failures = Counter.builder("fitness_agent_model_requests_total").tag("outcome", "failure").register(registry);
        fallbacks = Counter.builder("fitness_agent_model_requests_total").tag("outcome", "fallback").register(registry);
    }

    public Optional<Completion> complete(List<Map<String, Object>> messages, List<Map<String, Object>> tools) {
        return complete(messages, tools, false);
    }

    public Optional<Completion> complete(List<Map<String, Object>> messages, List<Map<String, Object>> tools, boolean requireAction) {
        return Optional.ofNullable(exchange(messages, tools, requireAction, false, false).completion());
    }

    /** V2 only: callers own the entire provider episode, including opaque assistant context. */
    public Outcome completePlanning(List<Map<String, Object>> messages, List<Map<String, Object>> tools, boolean thinking) {
        return exchange(messages, tools, true, true, thinking && properties.isPlannerThinkingEnabled());
    }

    private Outcome exchange(List<Map<String,Object>> messages,List<Map<String,Object>> tools,
                             boolean requireAction,boolean planning,boolean thinking) {
        String trace=java.util.UUID.randomUUID().toString();
        if (!isConfigured() || Instant.now().isBefore(circuitOpenUntil.get())) {
            fallbacks.increment();
            return Outcome.failed(isConfigured()?"CIRCUIT_OPEN":"NOT_CONFIGURED",isConfigured(),trace);
        }
        try {
            URI endpoint = chatEndpoint();
            Set<String> names = new HashSet<>();
            var preparedTools = prepareTools(tools, names);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", modelName());
            payload.put("messages", prepareMessages(messages, names, planning));
            payload.put("thinking", Map.of("type", thinking?"enabled":"disabled"));
            if(thinking)payload.put("reasoning_effort","high");else payload.put("temperature", 0.3);
            payload.put("max_tokens", planning?bounded(thinking?properties.getPlannerThinkingTokens():properties.getPlannerFastTokens(),1024,16384):bounded(properties.getMaxTokens(),128,4096));
            payload.put("stream", false);
            if (!preparedTools.isEmpty()) {
                payload.put("tools", preparedTools);
                payload.put("tool_choice", requireAction&&!thinking ? "required" : "auto");
            }
            byte[] body = mapper.writeValueAsBytes(payload);
            require(body.length <= (planning?262_144:MAX_REQUEST_BYTES), "Model request exceeded limit");
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(planning?bounded(properties.getPlannerReadTimeoutSeconds(),30,120):bounded(properties.getReadTimeoutSeconds(),3,60)))
                    .header("Authorization", "Bearer " + properties.getApiKey().trim())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
            if (!acquireRatePermit()) {
                fallbacks.increment();
                return Outcome.failed("RATE_LIMITED",true,trace);
            }
            // JDK 17 cancels its request timer after headers. The future deadline also
            // bounds body consumption; cancellation aborts a stalled provider exchange.
            var pending = http.sendAsync(request, ignored -> new LimitedBodySubscriber(planning?262_144:MAX_RESPONSE_BYTES));
            HttpResponse<byte[]> response;
            try {
                response = pending.get(request.timeout().orElseThrow().toMillis(), TimeUnit.MILLISECONDS);
            } finally {
                if (!pending.isDone()) pending.cancel(true);
            }
            if(response.statusCode()<200||response.statusCode()>=300){
                int status=response.statusCode();
                if(status!=429)registerFailure();
                return Outcome.failed(status==429?"RATE_LIMITED":status==401||status==403?"PROVIDER_AUTH":status==402?"PROVIDER_BALANCE":status==400?"PROVIDER_PROTOCOL":"PROVIDER_UNAVAILABLE",status==429||status>=500,trace);
            }
            Completion result = parse(response.body(), names,planning);
            consecutiveFailures.set(0);
            circuitOpenUntil.set(Instant.EPOCH);
            state.set("available");
            if (!result.calls().isEmpty()) toolCalling.set(true);
            successes.increment();
            return new Outcome(result,null);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            registerFailure();
            return Outcome.failed("CANCELLED",false,trace);
        } catch (java.util.concurrent.TimeoutException | java.net.http.HttpTimeoutException timeout) {
            registerFailure();return Outcome.failed("MODEL_TIMEOUT",true,trace);
        } catch (Truncated truncated) {
            registerFailure();return Outcome.failed("MODEL_TRUNCATED",false,trace);
        } catch (Exception ignored) {
            // Provider bodies, exception messages and request credentials must never reach logs.
            registerFailure();
        }
        return Outcome.failed("MODEL_PROTOCOL",false,trace);
    }

    public boolean isConfigured() {
        return properties.isEnabled() && properties.getApiKey() != null && !properties.getApiKey().isBlank()
                && properties.getModel() != null && !properties.getModel().isBlank();
    }

    public String modelName() { return properties.getModel(); }

    public Status status() {
        return new Status(isConfigured(), modelName(), toolCalling.get(), state.get());
    }

    private URI chatEndpoint() {
        require(properties.getBaseUrl() != null, "Missing provider URL");
        URI base = URI.create(properties.getBaseUrl().replaceAll("/+$", ""));
        boolean secure = "https".equalsIgnoreCase(base.getScheme());
        boolean local = "http".equalsIgnoreCase(base.getScheme()) && "localhost".equalsIgnoreCase(base.getHost());
        require((secure || local) && base.getHost() != null && base.getRawUserInfo() == null
                && base.getRawQuery() == null && base.getRawFragment() == null, "Unsafe provider URL");
        return URI.create(base + "/chat/completions");
    }

    private List<JsonNode> prepareTools(List<Map<String, Object>> tools, Set<String> names) {
        require(tools != null && tools.size() <= 32, "Invalid tool catalog");
        List<JsonNode> prepared = new ArrayList<>();
        for (var tool : tools) {
            JsonNode node = mapper.valueToTree(tool);
            require(node.isObject() && "function".equals(node.path("type").asText()), "Invalid tool type");
            JsonNode function = node.path("function");
            String name = text(function.path("name"), 64);
            require(FUNCTION_NAME.matcher(name).matches() && names.add(name), "Invalid tool name");
            JsonNode parameters = function.path("parameters");
            require(parameters.isObject() && "object".equals(parameters.path("type").asText())
                    && parameters.path("properties").isObject()
                    && parameters.path("additionalProperties").isBoolean()
                    && !parameters.path("additionalProperties").asBoolean(), "Invalid tool schema");
            // Schema keys and enum values define the protocol; they are not user context.
            prepared.add(node.deepCopy());
        }
        return prepared;
    }

    private List<Map<String, Object>> prepareMessages(List<Map<String, Object>> messages, Set<String> names,boolean planning) throws IOException {
        require(messages != null && !messages.isEmpty() && messages.size() <= (planning?64:24), "Invalid message history");
        List<Map<String, Object>> prepared = new ArrayList<>();
        for (Map<String, Object> message : messages) {
            require(message != null, "Invalid message");
            Object roleValue = message.get("role");
            require(roleValue instanceof String, "Invalid message role");
            String role = (String) roleValue;
            require(Set.of("system", "user", "assistant", "tool").contains(role), "Invalid message role");
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("role", role);
            Object content = message.get("content");
            require(content == null || content instanceof String, "Invalid message content");
            if (content instanceof String value) {
                require(value.length() <= MAX_CONTENT_CHARS, "Message exceeded limit");
                item.put("content", sanitizeContent(value));
            } else {
                require("assistant".equals(role) && message.containsKey("tool_calls"), "Missing message content");
                item.put("content", null);
            }
            if ("tool".equals(role)) {
                Object id = message.get("tool_call_id");
                require(id instanceof String && IDENTIFIER.matcher((String) id).matches(), "Invalid tool result ID");
                item.put("tool_call_id", id);
            }
            if (message.containsKey("tool_calls")) {
                require("assistant".equals(role), "Invalid tool call role");
                List<Call> calls = parseCalls(mapper.valueToTree(message.get("tool_calls")), names);
                List<Map<String, Object>> sanitizedCalls = new ArrayList<>();
                for (Call call : calls) {
                    String arguments = mapper.writeValueAsString(sanitizeTree(readJson(call.arguments()), 0));
                    sanitizedCalls.add(Map.of("id", call.id(), "type", "function", "function",
                            Map.of("name", call.name(), "arguments", arguments)));
                }
                item.put("tool_calls", sanitizedCalls);
            }
            if(planning&&message.containsKey("reasoning_content")){
                require("assistant".equals(role)&&message.get("reasoning_content") instanceof String,"Invalid provider context");
                String context=(String)message.get("reasoning_content");require(context.length()<=131072,"Provider context exceeded limit");
                item.put("reasoning_content",context);
            }
            prepared.add(item);
        }
        return prepared;
    }

    private Completion parse(byte[] body, Set<String> names,boolean planning) throws IOException {
        require(body != null && body.length > 0 && body.length <= (planning?262144:MAX_RESPONSE_BYTES), "Invalid provider body");
        JsonNode root = readJson(new String(body, StandardCharsets.UTF_8));
        require(root.isObject() && root.path("choices").isArray() && root.path("choices").size() == 1, "Invalid provider choices");
        JsonNode choice = root.path("choices").get(0);
        String reason = choice.path("finish_reason").asText("");
        if("length".equals(reason))throw new Truncated();
        require(!Set.of("content_filter", "insufficient_system_resource","aborted").contains(reason), "Incomplete provider response");
        JsonNode message = choice.path("message");
        require(message.isObject() && "assistant".equals(message.path("role").asText()), "Invalid provider message");
        JsonNode contentNode = message.path("content");
        String content = contentNode.isMissingNode() || contentNode.isNull() ? "" : text(contentNode, MAX_CONTENT_CHARS).trim();
        List<Call> calls = message.has("tool_calls") ? parseCalls(message.path("tool_calls"), names) : List.of();
        require(!content.isBlank() || !calls.isEmpty(), "Empty provider response");
        JsonNode tokens = root.path("usage").path("total_tokens");
        int totalTokens = tokens.isIntegralNumber() && tokens.canConvertToInt() ? Math.max(0, tokens.asInt()) : 0;
        Map<String,Object> turn=new LinkedHashMap<>();turn.put("role","assistant");turn.put("content",content);
        if(!calls.isEmpty())turn.put("tool_calls",calls.stream().map(c->Map.of("id",c.id(),"type","function","function",Map.of("name",c.name(),"arguments",c.arguments()))).toList());
        if(planning&&message.has("reasoning_content"))turn.put("reasoning_content",text(message.get("reasoning_content"),131072));
        return new Completion(content, List.copyOf(calls), modelName(), totalTokens,Collections.unmodifiableMap(turn));
    }

    private List<Call> parseCalls(JsonNode nodes, Set<String> names) throws IOException {
        require(nodes.isArray() && nodes.size() <= MAX_CALLS, "Invalid tool call count");
        List<Call> calls = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonNode node : nodes) {
            require(node.isObject() && "function".equals(node.path("type").asText()), "Invalid function call");
            String id = text(node.path("id"), 128);
            require(IDENTIFIER.matcher(id).matches() && ids.add(id), "Invalid function call ID");
            JsonNode function = node.path("function");
            String name = text(function.path("name"), 64);
            require(FUNCTION_NAME.matcher(name).matches() && names.contains(name), "Unadvertised function call");
            String arguments = text(function.path("arguments"), MAX_ARGUMENT_CHARS);
            require(readJson(arguments).isObject(), "Function arguments must be an object");
            calls.add(new Call(id, name, arguments));
        }
        return calls;
    }

    private JsonNode readJson(String json) throws IOException {
        JsonNode node = mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
                DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY).readTree(json);
        require(node != null, "Missing JSON value");
        return node;
    }

    private String sanitizeContent(String content) throws IOException {
        String trimmed = content.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            JsonNode parsed;
            try { parsed = readJson(content); }
            catch (IOException | IllegalArgumentException invalidJson) { return sanitize(content); }
            return mapper.writeValueAsString(sanitizeTree(parsed, 0));
        }
        return sanitize(content);
    }

    private JsonNode sanitizeTree(JsonNode node, int depth) { return sanitizeTree(node, depth, null); }

    private JsonNode sanitizeTree(JsonNode node, int depth, String field) {
        require(depth <= 20, "Context nesting exceeded limit");
        String mask = sensitiveMask(field);
        if (mask != null && !node.isNull()) return TextNode.valueOf(mask);
        // IDs remain usable in function round trips even when their digits resemble a phone.
        boolean identifier = field != null && (normalizedField(field).endsWith("id") || normalizedField(field).endsWith("ids"));
        if (identifier && node.isValueNode()) return node.deepCopy();
        if (node.isTextual()) return TextNode.valueOf(sanitize(node.asText()));
        if (node.isNumber() && node.decimalValue().stripTrailingZeros().scale() <= 0) {
            String numeric = node.decimalValue().toBigIntegerExact().toString();
            String sanitized = sanitize(numeric);
            if (!numeric.equals(sanitized)) return TextNode.valueOf(sanitized);
        }
        if (node.isObject()) {
            ObjectNode copy = mapper.createObjectNode();
            node.fields().forEachRemaining(entry -> copy.set(entry.getKey(), sanitizeTree(entry.getValue(), depth + 1, entry.getKey())));
            return copy;
        }
        if (node.isArray()) {
            ArrayNode copy = mapper.createArrayNode();
            node.forEach(value -> copy.add(sanitizeTree(value, depth + 1, field)));
            return copy;
        }
        return node.deepCopy();
    }

    private static String normalizedField(String field) {
        return field.toLowerCase(Locale.ROOT).replaceAll("[_-]", "");
    }

    private static String sensitiveMask(String field) {
        if (field == null) return null;
        return switch (normalizedField(field)) {
            case "phone", "phonenumber", "mobile", "mobilenumber", "telephone", "contactphone", "手机号", "电话" -> "[手机号已脱敏]";
            case "email", "contactemail", "邮箱" -> "[邮箱已脱敏]";
            case "idcard", "idnumber", "identitycard", "nationalid", "身份证", "身份证号" -> "[证件号已脱敏]";
            case "password", "passwordhash", "token", "accesstoken", "refreshtoken", "apikey", "authorization",
                    "secret", "address", "birthday", "birthdate", "密码", "密钥", "住址", "生日" -> "[敏感信息已脱敏]";
            default -> null;
        };
    }

    private String sanitize(String value) {
        String sanitized = ID_CARD.matcher(value).replaceAll("[证件号已脱敏]");
        sanitized = PHONE.matcher(sanitized).replaceAll("[手机号已脱敏]");
        return EMAIL.matcher(sanitized).replaceAll("[邮箱已脱敏]");
    }

    private synchronized boolean acquireRatePermit() {
        Instant now = Instant.now();
        if (!now.isBefore(rateWindow.plusSeconds(60))) {
            rateWindow = now;
            requestsInWindow = 0;
        }
        if (requestsInWindow >= Math.max(1, properties.getRequestsPerMinute())) return false;
        requestsInWindow++;
        return true;
    }

    private void registerFailure() {
        failures.increment();
        state.set("degraded");
        if (consecutiveFailures.incrementAndGet() >= Math.max(1, properties.getCircuitFailureThreshold())) {
            circuitOpenUntil.set(Instant.now().plusSeconds(bounded(properties.getCircuitOpenSeconds(), 10, 300)));
            consecutiveFailures.set(0);
        }
    }

    private static String text(JsonNode node, int limit) {
        require(node.isTextual() && node.asText().length() <= limit, "Invalid string value");
        return node.asText();
    }

    private static int bounded(int value, int minimum, int maximum) { return Math.max(minimum, Math.min(maximum, value)); }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    private static final class LimitedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final int limit;
        LimitedBodySubscriber(int limit){this.limit=limit;}
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private Flow.Subscription subscription;

        @Override public CompletionStage<byte[]> getBody() { return result; }

        @Override public void onSubscribe(Flow.Subscription incoming) {
            if (subscription != null) { incoming.cancel(); return; }
            subscription = incoming;
            incoming.request(1);
        }

        @Override public void onNext(List<ByteBuffer> items) {
            if (result.isDone()) return;
            long incomingSize = items.stream().mapToLong(ByteBuffer::remaining).sum();
            if (incomingSize > limit - buffer.size()) {
                subscription.cancel();
                result.completeExceptionally(new IOException("Provider response exceeded limit"));
                return;
            }
            for (ByteBuffer item : items) {
                byte[] bytes = new byte[item.remaining()];
                item.get(bytes);
                buffer.writeBytes(bytes);
            }
            subscription.request(1);
        }

        @Override public void onError(Throwable error) { result.completeExceptionally(error); }
        @Override public void onComplete() { result.complete(buffer.toByteArray()); }
    }

    public record Call(String id, String name, String arguments) { }
    private static final class Truncated extends IOException {}
    public record Completion(String content, List<Call> calls, String model, int totalTokens,
                             @com.fasterxml.jackson.annotation.JsonIgnore Map<String,Object> providerTurn) {
        public Completion(String content,List<Call> calls,String model,int totalTokens){this(content,calls,model,totalTokens,Map.of());}
        @Override public String toString(){return "Completion[model="+model+", totalTokens="+totalTokens+"]";}
    }
    public record Failure(String code,boolean retryable,String traceId){}
    public record Outcome(Completion completion,Failure failure){
        public boolean success(){return completion!=null;}
        static Outcome failed(String code,boolean retryable,String trace){return new Outcome(null,new Failure(code,retryable,trace));}
    }
    public record Status(boolean configured, String model, boolean toolCalling, String state) { }
}
