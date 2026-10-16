package com.gym.fitness.module.assistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gym.fitness.config.AiProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AgentModelClientTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private AiProperties properties;
    private HttpClient http;
    private AgentModelClient client;
    private HttpRequest sent;
    private HttpResponse.BodyHandler<byte[]> handler;
    private static final List<Map<String, Object>> MESSAGES = List.of(Map.of("role", "user", "content", "查询课程"));
    private static final List<Map<String, Object>> TOOLS = List.of(Map.of(
            "type", "function", "function", Map.of("name", "courses", "description", "查询可预约课程",
                    "parameters", Map.of("type", "object", "properties", Map.of(), "additionalProperties", false))));

    @BeforeEach
    void setup() {
        properties = new AiProperties();
        properties.setApiKey("test-provider-key");
        properties.setRequestsPerMinute(100);
        http = mock(HttpClient.class);
        client = new AgentModelClient(properties, mapper, new SimpleMeterRegistry(), http);
    }

    @Test
    void configuredStatusDoesNotClaimAProviderRequestOrToolSupport() {
        assertTrue(client.isConfigured());
        assertEquals("unknown", client.status().state());
        assertFalse(client.status().toolCalling());
        verifyNoInteractions(http);
    }

    @Test
    void validFunctionSelectionIsReturnedWithoutExecutingAnything() throws Exception {
        respond(200, completion(null, List.of(call("call_13812345678", "courses", "{\"date\":\"2026-10-15\"}"))));
        var completed = client.complete(MESSAGES, TOOLS);
        assertTrue(completed.isPresent());
        var result = completed.orElseThrow();
        assertEquals("", result.content());
        assertEquals(List.of(new AgentModelClient.Call("call_13812345678", "courses", "{\"date\":\"2026-10-15\"}")), result.calls());
        assertEquals("deepseek-v4-flash", result.model());
        assertEquals(31, result.totalTokens());
        assertEquals("available", client.status().state());
        assertTrue(client.status().toolCalling());
    }

    @Test
    void normalTextResponseRemainsUsableAndDoesNotProveToolSupport() throws Exception {
        respond(200, completion("  找到课程。  ", List.of()));
        var completed = client.complete(MESSAGES, TOOLS);
        assertTrue(completed.isPresent());
        var result = completed.orElseThrow();
        assertEquals("找到课程。", result.content());
        assertTrue(result.calls().isEmpty());
        assertEquals("available", client.status().state());
        assertFalse(client.status().toolCalling());
    }

    @Test
    void computerPlanningCanRequireAnActionWhileBusinessChatKeepsAutomaticChoice() throws Exception {
        respond(200, completion(null, List.of(call("decision1", "courses", "{}"))));
        assertTrue(client.complete(MESSAGES, TOOLS, true).isPresent());
        assertEquals("required", payload().path("tool_choice").asText());
        assertEquals("disabled", payload().path("thinking").path("type").asText());
        assertTrue(client.complete(MESSAGES, TOOLS).isPresent());
        assertEquals("auto", payload().path("tool_choice").asText());
    }

    @Test
    void outgoingPayloadRedactsContentAndArgumentValuesWhilePreservingProtocolAndJsonKeys() throws Exception {
        respond(200, completion("已查询", List.of()));
        var assistant = new LinkedHashMap<String, Object>();
        assistant.put("role", "assistant");
        assistant.put("content", null);
        assistant.put("tool_calls", List.of(call("call_13812345678", "courses",
                "{\"13812345678\":\"13812345678\",\"nested\":{\"email\":\"a@example.com\"}}")));
        var messages = List.<Map<String, Object>>of(
                Map.of("role", "system", "content", "联系电话13812345678"),
                Map.of("role", "user", "content", "身份证11010519491231002X 邮箱a@example.com"),
                assistant,
                Map.of("role", "tool", "tool_call_id", "call_13812345678", "content", "{\"13812345678\":\"13812345678\"}"));
        assertTrue(client.complete(messages, TOOLS).isPresent());
        JsonNode payload = payload();
        assertEquals("call_13812345678", payload.at("/messages/2/tool_calls/0/id").asText());
        assertEquals("call_13812345678", payload.at("/messages/3/tool_call_id").asText());
        JsonNode arguments = mapper.readTree(payload.at("/messages/2/tool_calls/0/function/arguments").asText());
        assertEquals("[手机号已脱敏]", arguments.path("13812345678").asText());
        assertEquals("[邮箱已脱敏]", arguments.at("/nested/email").asText());
        assertEquals("[手机号已脱敏]", mapper.readTree(payload.at("/messages/3/content").asText()).path("13812345678").asText());
        assertFalse(payload.at("/messages/0/content").asText().contains("13812345678"));
        assertFalse(payload.at("/messages/1/content").asText().contains("11010519491231002X"));
        assertEquals("{\"13812345678\":\"13812345678\",\"nested\":{\"email\":\"a@example.com\"}}",
                ((Map<?, ?>) ((Map<?, ?>) ((List<?>) assistant.get("tool_calls")).get(0)).get("function")).get("arguments"));
    }

    @Test
    void requestUsesExpectedSchemaAuthenticationAndBoundedTimeout() throws Exception {
        properties.setBaseUrl("https://provider.example/v1/");
        properties.setReadTimeoutSeconds(2);
        respond(200, completion("完成", List.of()));
        assertTrue(client.complete(MESSAGES, TOOLS).isPresent());
        JsonNode payload = payload();
        assertEquals(URI.create("https://provider.example/v1/chat/completions"), sent.uri());
        assertEquals("Bearer test-provider-key", sent.headers().firstValue("Authorization").orElseThrow());
        assertTrue(sent.timeout().orElseThrow().compareTo(Duration.ofSeconds(3)) >= 0);
        assertEquals("auto", payload.path("tool_choice").asText());
        assertFalse(payload.path("stream").asBoolean());
        assertEquals("disabled", payload.at("/thinking/type").asText());
        assertEquals(mapper.valueToTree(TOOLS), payload.path("tools"));
    }

    @Test
    void jsonSensitiveFieldsMaskStringNumericAndNestedValuesBeforeProviderSubmission() throws Exception {
        respond(200, completion("ok", List.of()));
        String content = "{\"phone\":13800000000,\"email\":\"private-address\",\"id_card\":110105194912310020,"
                + "\"password\":\"private-password\",\"nested\":{\"contactPhone\":\"private-phone\","
                + "\"access_token\":\"private-token\",\"birthday\":\"2000-01-01\"}}";
        assertTrue(client.complete(List.of(Map.of("role", "user", "content", content)), TOOLS).isPresent());
        var submitted = mapper.readTree(payload().at("/messages/0/content").asText());
        assertEquals("[手机号已脱敏]", submitted.path("phone").asText());
        assertEquals("[邮箱已脱敏]", submitted.path("email").asText());
        assertEquals("[证件号已脱敏]", submitted.path("id_card").asText());
        assertEquals("[敏感信息已脱敏]", submitted.path("password").asText());
        assertEquals("[手机号已脱敏]", submitted.at("/nested/contactPhone").asText());
        assertEquals("[敏感信息已脱敏]", submitted.at("/nested/access_token").asText());
        assertEquals("[敏感信息已脱敏]", submitted.at("/nested/birthday").asText());
        assertFalse(submitted.toString().contains("private-"));
    }

    @Test
    void unlabelledNumericPhoneAndIdentityValuesAreAlsoMaskedInNestedJson() throws Exception {
        respond(200, completion("ok", List.of()));
        String content = "{\"contact\":13800000000,\"scientific\":1.3800000000E10,\"values\":[110105194912310020,{\"value\":13800000001}]}";
        assertTrue(client.complete(List.of(Map.of("role", "user", "content", content)), TOOLS).isPresent());
        var submitted = mapper.readTree(payload().at("/messages/0/content").asText());
        assertEquals("[手机号已脱敏]", submitted.path("contact").asText());
        assertEquals("[手机号已脱敏]", submitted.path("scientific").asText());
        assertEquals("[证件号已脱敏]", submitted.at("/values/0").asText());
        assertEquals("[手机号已脱敏]", submitted.at("/values/1/value").asText());
    }

    @Test
    void numericBusinessIdentifiersAndFunctionProtocolSurviveSensitiveArgumentRedaction() throws Exception {
        respond(200, completion("ok", List.of()));
        String arguments = "{\"memberId\":13800000000,\"scheduleId\":\"13800000001\",\"ids\":[13800000002],"
                + "\"details\":{\"phone\":13800000003}}";
        var assistant = new LinkedHashMap<String, Object>();
        assistant.put("role", "assistant");
        assistant.put("content", null);
        assistant.put("tool_calls", List.of(call("call_13800000000", "courses", arguments)));
        assertTrue(client.complete(List.of(assistant, Map.of("role", "tool", "tool_call_id", "call_13800000000", "content", arguments)), TOOLS).isPresent());
        var submitted = mapper.readTree(payload().at("/messages/0/tool_calls/0/function/arguments").asText());
        assertEquals(13800000000L, submitted.path("memberId").asLong());
        assertEquals("13800000001", submitted.path("scheduleId").asText());
        assertEquals(13800000002L, submitted.at("/ids/0").asLong());
        assertEquals("[手机号已脱敏]", submitted.at("/details/phone").asText());
        assertEquals("call_13800000000", payload().at("/messages/0/tool_calls/0/id").asText());
        assertEquals("call_13800000000", payload().at("/messages/1/tool_call_id").asText());
        assertEquals(mapper.valueToTree(TOOLS), payload().path("tools"));
    }

    @Test
    void emptyToolCatalogKeepsOrdinaryCompletionWireFormat() throws Exception {
        respond(200, completion("普通问答", List.of()));
        assertTrue(client.complete(MESSAGES, List.of()).isPresent());
        assertFalse(payload().has("tools"));
        assertFalse(payload().has("tool_choice"));
    }

    @Test
    void malformedResponsesFailClosedAndExposeOnlyDegradedStatus() throws Exception {
        for (String body : List.of("not-json", "{}", "[]", completion(null, List.of()),
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":{\"secret\":\"value\"}}}]}")) {
            setup();
            respond(200, body);
            assertTrue(client.complete(MESSAGES, TOOLS).isEmpty(), body);
            assertEquals("degraded", client.status().state());
            assertFalse(client.status().toolCalling());
        }
    }

    @Test
    void invalidCallsNeverReachTheOrchestrator() throws Exception {
        var invalid = new ArrayList<Map<String, Object>>();
        invalid.add(call("x", "courses", "[]"));
        invalid.add(call("x", "courses", "{\"x\":1} trailing"));
        invalid.add(call("x", "courses", "{\"x\":1,\"x\":2}"));
        invalid.add(call("x", "shell", "{}"));
        invalid.add(call("", "courses", "{}"));
        invalid.add(call("x", "courses", "{broken"));
        invalid.add(Map.of("id", "x", "type", "custom", "function", Map.of("name", "courses", "arguments", "{}")));
        for (var item : invalid) {
            setup();
            respond(200, completion(null, List.of(item)));
            assertTrue(client.complete(MESSAGES, TOOLS).isEmpty(), item.toString());
            assertFalse(client.status().toolCalling());
        }
    }

    @Test
    void duplicateIdsTooManyCallsOversizedArgumentsAndOversizedContentAreRejected() throws Exception {
        var tooMany = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < 7; i++) tooMany.add(call("call_" + i, "courses", "{}"));
        for (String body : List.of(completion(null, tooMany),
                completion(null, List.of(call("same", "courses", "{}"), call("same", "courses", "{}"))),
                completion(null, List.of(call("x", "courses", "{\"value\":\"" + "a".repeat(9000) + "\"}"))),
                completion("a".repeat(17000), List.of()), " ".repeat(70000) + completion("ok", List.of()))) {
            setup();
            respond(200, body);
            assertTrue(client.complete(MESSAGES, TOOLS).isEmpty());
        }
    }

    @Test
    void sixCallsAreAcceptedAsOneBoundedResponse() throws Exception {
        var calls = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < 6; i++) calls.add(call("call_" + i, "courses", "{}"));
        respond(200, completion(null, calls));
        var completed = client.complete(MESSAGES, TOOLS);
        assertTrue(completed.isPresent());
        assertEquals(6, completed.orElseThrow().calls().size());
    }

    @Test
    void incompleteProviderGenerationIsNotPresentedAsACompleteAction() throws Exception {
        respond(200, "{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"role\":\"assistant\",\"content\":\"partial\"}}]}");
        assertTrue(client.complete(MESSAGES, TOOLS).isEmpty());
    }

    @Test
    void unconfiguredAndUnsafeEndpointsNeverContactProvider() throws Exception {
        properties.setApiKey(" ");
        assertTrue(client.complete(MESSAGES, TOOLS).isEmpty());
        assertFalse(client.isConfigured());
        for (String endpoint : List.of("http://provider.example", "https://user:password@provider.example", "https://provider.example/?key=secret", "file:///tmp/model")) {
            setup();
            properties.setBaseUrl(endpoint);
            assertTrue(client.complete(MESSAGES, TOOLS).isEmpty());
            verifyNoInteractions(http);
        }
    }

    @Test
    void oversizedHistoryAndInvalidToolSchemaFailBeforeSending() {
        assertTrue(client.complete(List.of(Map.of("role", "user", "content", "x".repeat(40000))), TOOLS).isEmpty());
        assertTrue(client.complete(MESSAGES, List.of(Map.of("type", "function", "function", Map.of("name", "courses")))).isEmpty());
        verifyNoInteractions(http);
    }

    @Test
    void providerFailuresOpenCircuitAndDoNotExposeResponseBody() throws Exception {
        properties.setCircuitFailureThreshold(2);
        respond(401, "secret-provider-response");
        assertTrue(client.complete(MESSAGES, TOOLS).isEmpty());
        assertTrue(client.complete(MESSAGES, TOOLS).isEmpty());
        assertTrue(client.complete(MESSAGES, TOOLS).isEmpty());
        verify(http, times(2)).sendAsync(any(), any(HttpResponse.BodyHandler.class));
        assertEquals("degraded", client.status().state());
        assertFalse(client.status().toString().contains("secret-provider-response"));
    }

    @Test
    void providerIoFailureIsSafeAndAFollowingSuccessRecoversState() throws Exception {
        when(http.sendAsync(any(), any(HttpResponse.BodyHandler.class)))
                .thenReturn(CompletableFuture.failedFuture(new IOException("secret-api-key")));
        assertTrue(client.complete(MESSAGES, TOOLS).isEmpty());
        assertEquals("degraded", client.status().state());
        respond(200, completion("恢复", List.of()));
        assertTrue(client.complete(MESSAGES, TOOLS).isPresent());
        assertEquals("available", client.status().state());
    }

    @Test
    void interruptionRemainsVisibleToCaller() throws Exception {
        var pending = new CompletableFuture<HttpResponse<Object>>();
        when(http.sendAsync(any(), any(HttpResponse.BodyHandler.class))).thenReturn(pending);
        try {
            Thread.currentThread().interrupt();
            assertTrue(client.complete(MESSAGES, TOOLS).isEmpty());
            assertTrue(Thread.currentThread().isInterrupted());
            assertTrue(pending.isCancelled());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void configuredRequestRateBoundsProviderCalls() throws Exception {
        properties.setRequestsPerMinute(1);
        respond(200, completion("完成", List.of()));
        assertTrue(client.complete(MESSAGES, TOOLS).isPresent());
        assertTrue(client.complete(MESSAGES, TOOLS).isEmpty());
        verify(http, times(1)).sendAsync(any(), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void bodySubscriberCancelsBeforeBufferingAnOversizedProviderBody() throws Exception {
        respond(200, completion("完成", List.of()));
        client.complete(MESSAGES, TOOLS);
        assertNotNull(handler);
        var subscriber = handler.apply(mock(HttpResponse.ResponseInfo.class));
        var subscription = mock(Flow.Subscription.class);
        subscriber.onSubscribe(subscription);
        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[70000])));
        assertThrows(CompletionException.class, () -> subscriber.getBody().toCompletableFuture().join());
        verify(subscription).cancel();
    }

    @Test
    void readTimeoutAlsoBoundsAProviderThatSendsHeadersAndThenStallsTheBody() throws Exception {
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("localhost", 0), 0);
        var releaseBody = new CountDownLatch(1);
        byte[] validBody = completion("late", List.of()).getBytes(StandardCharsets.UTF_8);
        server.createContext("/chat/completions", exchange -> {
            try (var output = exchange.getResponseBody()) {
                exchange.sendResponseHeaders(200, 0);
                output.write(' ');
                output.flush();
                try { releaseBody.await(7, TimeUnit.SECONDS); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                output.write(validBody);
            } catch (IOException ignored) { }
        });
        server.start();
        try {
            properties.setBaseUrl("http://localhost:" + server.getAddress().getPort());
            properties.setReadTimeoutSeconds(3);
            var realClient = new AgentModelClient(properties, mapper, new SimpleMeterRegistry());
            long started = System.nanoTime();
            assertTrue(realClient.complete(MESSAGES, TOOLS).isEmpty());
            assertTrue(Duration.ofNanos(System.nanoTime() - started).compareTo(Duration.ofSeconds(5)) < 0);
            assertEquals("degraded", realClient.status().state());
        } finally {
            releaseBody.countDown();
            server.stop(0);
        }
    }

    private Map<String, Object> call(String id, String name, String arguments) {
        return Map.of("id", id, "type", "function", "function", Map.of("name", name, "arguments", arguments));
    }

    private String completion(String content, List<Map<String, Object>> calls) throws Exception {
        var message = new LinkedHashMap<String, Object>();
        message.put("role", "assistant");
        message.put("content", content);
        if (!calls.isEmpty()) message.put("tool_calls", calls);
        return mapper.writeValueAsString(Map.of("choices", List.of(Map.of("finish_reason", calls.isEmpty() ? "stop" : "tool_calls", "message", message)),
                "model", "deepseek-v4-flash", "usage", Map.of("total_tokens", 31)));
    }

    @SuppressWarnings("unchecked")
    private void respond(int status, String body) throws Exception {
        HttpResponse<byte[]> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body.getBytes(StandardCharsets.UTF_8));
        when(http.sendAsync(any(), any(HttpResponse.BodyHandler.class))).thenAnswer(invocation -> {
            sent = invocation.getArgument(0);
            handler = invocation.getArgument(1);
            return CompletableFuture.completedFuture(response);
        });
    }

    private JsonNode payload() throws Exception {
        var buffer = new java.io.ByteArrayOutputStream();
        sent.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<>() {
            public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            public void onNext(ByteBuffer item) { var bytes = new byte[item.remaining()]; item.get(bytes); buffer.writeBytes(bytes); }
            public void onError(Throwable error) { fail(error); }
            public void onComplete() { }
        });
        return mapper.readTree(buffer.toByteArray());
    }
}
