package com.gym.fitness.module.assistant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gym.fitness.config.AiProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class AgentModelPolicyTest {
    private final ObjectMapper json=new ObjectMapper();
    private final AiProperties properties=new AiProperties();
    private HttpRequest sent;
    private AgentModelClient client(int status,String body) throws Exception {
        properties.setApiKey("fixture-secret");properties.setRequestsPerMinute(100);
        HttpClient http=mock(HttpClient.class);
        @SuppressWarnings("unchecked") HttpResponse<byte[]> response=mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);when(response.body()).thenReturn(body.getBytes(StandardCharsets.UTF_8));
        when(http.sendAsync(any(),any(HttpResponse.BodyHandler.class))).thenAnswer(invocation->{sent=invocation.getArgument(0);return CompletableFuture.completedFuture(response);});
        return new AgentModelClient(properties,json,new SimpleMeterRegistry(),http);
    }
    private final List<Map<String,Object>> tools=List.of(Map.of("type","function","function",Map.of("name","observe","parameters",Map.of("type","object","properties",Map.of(),"additionalProperties",false))));
    private String body(String reason,String reasoning) throws Exception {
        return json.writeValueAsString(Map.of("choices",List.of(Map.of("finish_reason",reason,"message",Map.of("role","assistant","content","","reasoning_content",reasoning,"tool_calls",List.of(Map.of("id","real1","type","function","function",Map.of("name","observe","arguments","{}"))))))));
    }
    private com.fasterxml.jackson.databind.JsonNode payload() throws Exception {
        var bytes=new java.io.ByteArrayOutputStream();sent.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
            public void onSubscribe(Flow.Subscription s){s.request(Long.MAX_VALUE);}public void onNext(ByteBuffer b){byte[] x=new byte[b.remaining()];b.get(x);bytes.writeBytes(x);}public void onError(Throwable e){throw new AssertionError(e);}public void onComplete(){}
        });return json.readTree(bytes.toByteArray());
    }
    @Test void thinkingUsesAutoChoiceAndKeepsOpaqueProviderTurnForNextCall() throws Exception {
        var client=client(200,body("tool_calls","opaque provider context"));
        var outcome=client.completePlanning(List.of(Map.of("role","user","content","多步任务")),tools,true);
        assertTrue(outcome.success());assertEquals("enabled",payload().at("/thinking/type").asText());
        assertEquals("auto",payload().path("tool_choice").asText());assertTrue(payload().path("max_tokens").asInt()>=8192);
        var turn=outcome.completion().providerTurn();assertEquals("opaque provider context",turn.get("reasoning_content"));
        var history=new ArrayList<Map<String,Object>>();history.add(Map.of("role","user","content","多步任务"));history.add(turn);history.add(Map.of("role","tool","tool_call_id","real1","content","actual result"));
        assertTrue(client.completePlanning(history,tools,true).success());assertEquals("opaque provider context",payload().at("/messages/1/reasoning_content").asText());
        assertTrue(sent.timeout().orElseThrow().getSeconds()>=90);
        assertFalse(outcome.toString().contains("opaque provider context"));assertFalse(outcome.toString().contains("fixture-secret"));
    }
    @Test void rateLimitIsTypedAndDoesNotExposeProviderBody() throws Exception {
        var outcome=client(429,"private provider body").completePlanning(List.of(Map.of("role","user","content","查询")),tools,false);
        assertFalse(outcome.success());assertEquals("RATE_LIMITED",outcome.failure().code());assertTrue(outcome.failure().retryable());
        assertFalse(outcome.toString().contains("private provider body"));assertNotNull(outcome.failure().traceId());
    }
    @Test void truncatedResponseNeverBecomesAnAction() throws Exception {
        var outcome=client(200,body("length","partial")).completePlanning(List.of(Map.of("role","user","content","任务")),tools,true);
        assertFalse(outcome.success());assertEquals("MODEL_TRUNCATED",outcome.failure().code());
    }
    @Test void fastPlanningHasOwnBudgetAndDoesNotEnableThinking() throws Exception {
        var outcome=client(200,body("tool_calls","unused")).completePlanning(List.of(Map.of("role","user","content","打开页面")),tools,false);
        assertTrue(outcome.success());assertEquals("disabled",payload().at("/thinking/type").asText());assertTrue(payload().path("max_tokens").asInt()>=1024);
    }
}
