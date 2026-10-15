package com.gym.fitness.module.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.gym.fitness.common.response.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import static com.gym.fitness.module.agent.AgentDtos.*;
import static com.gym.fitness.module.agent.AgentIdentity.*;

@RestController @RequestMapping("/api/v1/agent") @RequiredArgsConstructor
@PreAuthorize("hasAuthority('ASSISTANT_USE')")
public class AgentController {
 private final AgentService service;private final AgentBusiness business;private final AgentActions actions;
 @GetMapping("/profile")public Result<Map<String,Object>> profile(){return Result.success(service.profile());}
 @PostMapping("/chat")public Result<Reply> chat(@RequestBody JsonNode request){return Result.success(service.chat(request));}
 @PostMapping("/query")public Result<ToolResult> query(@RequestBody JsonNode request){validate(request);return Result.success(business.query(request.get("tool").asText(),request.get("arguments")));}
 @PostMapping("/actions/preview")public Result<Action> preview(@RequestBody JsonNode request){validate(request);return Result.success(actions.preview(request.get("tool").asText(),request.get("arguments")));}
 @GetMapping("/actions")public Result<List<Action>> list(){return Result.success(actions.list());}
 @PostMapping("/actions/{id}/confirm")public Result<Action> confirm(@PathVariable String id,@RequestBody JsonNode request){AgentService.allow(request,Set.of());return Result.success(actions.confirm(id));}
 @PostMapping("/actions/{id}/cancel")public Result<Action> cancel(@PathVariable String id,@RequestBody JsonNode request){AgentService.allow(request,Set.of());return Result.success(actions.cancel(id));}
 private void validate(JsonNode n){AgentService.allow(n,Set.of("tool","arguments"));if(!n.path("tool").isTextual()||n.get("tool").asText().length()>60||!n.path("arguments").isObject())throw invalid("请提供工具名称和参数对象");}
}
