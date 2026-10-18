package com.gym.fitness.module.agent.task;

import com.fasterxml.jackson.databind.*;
import com.gym.fitness.common.response.Result;
import com.gym.fitness.module.agent.AgentService;
import com.gym.fitness.module.agent.computer.ComputerAgentService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import static com.gym.fitness.module.agent.AgentIdentity.invalid;

@RestController @RequestMapping("/api/v1/agent/tasks")
@PreAuthorize("hasAuthority('ASSISTANT_USE')") @RequiredArgsConstructor
public class AgentTaskController {
    private final AgentTaskStore store;private final AgentTaskPlanner planner;private final ComputerAgentService computer;private final ObjectMapper json;
    private final AgentMutationReceiptService receipts;
    private final AgentOutcomeVerifier verifier;
    private final com.gym.fitness.config.AiProperties aiProperties;
    private void requireEnabled(){if(!aiProperties.isTaskV2Enabled())throw invalid("持续网页Agent暂未启用，请刷新助手状态");}
    @GetMapping("/capabilities") public Result<Map<String,Object>> capabilities(){computer.session();boolean ready=aiProperties.isTaskV2Enabled()&&store.schemaAvailable();return Result.success(Map.of("version",ready?2:1,"durableTasks",ready,"providerEpisodes",ready,"confirmedReceipts",ready));}
    @PostMapping public Result<AgentTaskStore.View> create(@RequestBody String raw){requireEnabled();var r=read(raw);AgentService.allow(r,Set.of("document","goal"));return Result.success(store.create(computer.session(),text(r,"document"),text(r,"goal")));}
    @GetMapping public Result<List<AgentTaskStore.View>> list(){return Result.success(store.list(computer.session()));}
    @GetMapping("/{id}") public Result<AgentTaskStore.View> get(@PathVariable String id){return Result.success(store.get(computer.session(),id));}
    @GetMapping("/{id}/diagnostics") public Result<Map<String,Object>> diagnostics(@PathVariable String id){var me=computer.session();var task=store.get(me,id);return Result.success(Map.of("taskId",id,"status",task.status(),"version",task.version(),"modelCalls",task.checkpoint().path("modelCalls").asInt(),"totalTokens",task.checkpoint().path("totalTokens").asInt(),"lastFailure",task.checkpoint().path("lastFailure"),"verification",task.checkpoint().path("verification"),"receipts",receipts.list(me,id).stream().map(r->Map.of("id",r.id(),"status",r.status(),"method",r.method(),"path",r.path())).toList()));}
    @PostMapping("/{id}/next") public Result<AgentTaskPlanner.Turn> next(@PathVariable String id,@RequestBody String raw){requireEnabled();return Result.success(planner.next(id,read(raw)));}
    @PostMapping("/{id}/prepare") public Result<AgentMutationReceiptService.Receipt> prepare(@PathVariable String id,@RequestBody String raw){requireEnabled();var r=read(raw);AgentService.allow(r,Set.of("version","document","method","path","body"));version(r);return Result.success(receipts.prepare(computer.session(),id,r.get("version").asLong(),text(r,"document"),text(r,"method"),text(r,"path"),r.path("body")));}
    @PostMapping("/{id}/approve/{receipt}") public Result<AgentMutationReceiptService.Receipt> approve(@PathVariable String id,@PathVariable String receipt,@RequestBody String raw){requireEnabled();var r=command(raw);var me=computer.session();if(!receipts.get(me,receipt).taskId().equals(id))throw invalid("确认不属于此任务");return Result.success(receipts.approve(me,receipt,r.get("version").asLong(),text(r,"document")));}
    @GetMapping("/{id}/receipts") public Result<List<AgentMutationReceiptService.Receipt>> receipts(@PathVariable String id){return Result.success(receipts.list(computer.session(),id));}
    @PostMapping("/{id}/verify") public Result<AgentOutcomeVerifier.Report> verify(@PathVariable String id,@RequestBody String raw){var r=read(raw);AgentService.allow(r,Set.of("version","document","context"));version(r);long previous=r.get("version").asLong();var report=verifier.verify(computer.session(),id,previous,text(r,"document"),r.path("context"));if(report.complete())planner.forget(id);else planner.acknowledgeVerification(id,previous,report.task().version());return Result.success(report);}
    @PostMapping("/{id}/close") public Result<AgentTaskStore.View> close(@PathVariable String id,@RequestBody String raw){var r=read(raw);AgentService.allow(r,Set.of("version","document","status"));version(r);String status=text(r,"status");if(!Set.of("FAILED","PARTIAL","CANCELLED").contains(status))throw invalid("此入口不能声明任务成功");var me=computer.session();var task=store.check(me,id,r.get("version").asLong(),text(r,"document"));if(Set.of("SUCCEEDED","FAILED","PARTIAL","CANCELLED").contains(task.status()))return Result.success(task);planner.forget(id);return Result.success(store.update(me,id,task.version(),text(r,"document"),status,task.plan(),task.checkpoint()));}
    @PostMapping("/{id}/cancel") public Result<AgentTaskStore.View> cancel(@PathVariable String id,@RequestBody String raw){var r=command(raw);var result=store.cancel(computer.session(),id,r.get("version").asLong(),text(r,"document"));planner.forget(id);return Result.success(result);}
    @PostMapping("/{id}/pause") public Result<AgentTaskStore.View> pause(@PathVariable String id,@RequestBody String raw){var r=read(raw);AgentService.allow(r,Set.of("document"));var me=computer.session();var result=store.pause(me,id,text(r,"document"));receipts.revokePending(me,id);planner.forget(id);return Result.success(result);}
    @PostMapping("/{id}/recover") public Result<AgentTaskStore.View> recover(@PathVariable String id,@RequestBody String raw){var r=command(raw);var me=computer.session();var result=store.recover(me,id,r.get("version").asLong(),text(r,"document"));receipts.revokePending(me,id);planner.forget(id);return Result.success(result);}
    @PostMapping("/{id}/resume") public Result<AgentTaskStore.View> resume(@PathVariable String id,@RequestBody String raw){var r=command(raw);var me=computer.session();var t=store.check(me,id,r.get("version").asLong(),text(r,"document"));if(!"PAUSED".equals(t.status()))throw invalid("该任务无需恢复");return Result.success(store.update(me,id,t.version(),text(r,"document"),"RUNNING",t.plan(),t.checkpoint()));}
    @PostMapping("/{id}/revise") public Result<AgentTaskStore.View> revise(@PathVariable String id,@RequestBody String raw){var r=read(raw);AgentService.allow(r,Set.of("version","document","amendment"));version(r);var result=store.revise(computer.session(),id,r.get("version").asLong(),text(r,"document"),text(r,"amendment"));receipts.revokePending(computer.session(),id);planner.forget(id);return Result.success(result);}
    private JsonNode command(String raw){var r=read(raw);AgentService.allow(r,Set.of("version","document"));version(r);return r;}
    private void version(JsonNode r){if(!r.path("version").isIntegralNumber()||r.get("version").asLong()<0)throw invalid("任务版本格式不正确");}
    private String text(JsonNode r,String key){if(!r.path(key).isTextual())throw invalid("缺少"+key);return r.get(key).asText();}
    private JsonNode read(String raw){if(raw==null||raw.length()>48000)throw invalid("任务请求过长");try{JsonNode n=json.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS,DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY).readTree(raw);if(n==null||!n.isObject())throw invalid("任务请求须为对象");return n;}catch(java.io.IOException e){throw invalid("任务请求不是有效JSON");}}
}
