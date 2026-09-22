package com.gym.fitness.module.training.controller;
import com.gym.fitness.common.response.Result;
import com.gym.fitness.module.training.dto.TrainingDtos.*;
import com.gym.fitness.module.training.service.TrainingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/v1/training") @RequiredArgsConstructor
public class TrainingController {
    private final TrainingService service;
    @GetMapping("/lessons") @PreAuthorize("hasAnyAuthority('MEMBER_SELF_SERVICE','COACH_WORKBENCH','MEMBERSHIP_READ')")
    public Result<List<LessonResponse>> lessons(){return Result.success(service.listLessons());}
    @GetMapping("/readiness") @PreAuthorize("hasAnyAuthority('COACH_WORKBENCH','MEMBERSHIP_READ')")
    public Result<PrivateReadiness> readiness(){return Result.success(service.readiness());}
    @PostMapping("/lessons/{id}/submit") @PreAuthorize("hasAuthority('COACH_WORKBENCH')")
    public Result<LessonResponse> submit(@PathVariable Long id){return Result.success(service.submit(id));}
    @PostMapping("/lessons/{id}/confirm") @PreAuthorize("hasAuthority('MEMBER_SELF_SERVICE')")
    public Result<LessonResponse> confirm(@PathVariable Long id){return Result.success(service.confirm(id));}
    @PostMapping("/lessons/{id}/reverse") @PreAuthorize("hasAnyRole('SUPER_ADMIN','MANAGER')")
    public Result<LessonResponse> reverse(@PathVariable Long id,@Valid @RequestBody ReasonRequest request){return Result.success(service.reverse(id,request.getReason()));}
    @GetMapping("/commission-rules") @PreAuthorize("hasAnyAuthority('COACH_PERFORMANCE_READ','FINANCE_READ')")
    public Result<List<RuleResponse>> rules(){return Result.success(service.listRules());}
    @PutMapping("/commission-rules") @PreAuthorize("hasAnyRole('SUPER_ADMIN','MANAGER')")
    public Result<RuleResponse> rule(@Valid @RequestBody RuleRequest request){return Result.success(service.updateRule(request));}
    @GetMapping("/settlements") @PreAuthorize("hasAnyAuthority('COACH_PERFORMANCE_READ','FINANCE_READ')")
    public Result<List<SettlementResponse>> settlements(){return Result.success(service.listSettlements());}
    @PostMapping("/settlements") @PreAuthorize("hasAnyRole('SUPER_ADMIN','MANAGER')")
    public Result<SettlementResponse> generate(@Valid @RequestBody SettlementRequest request){return Result.success(service.generate(request));}
    @PostMapping("/settlements/{id}/approve") @PreAuthorize("hasAnyRole('SUPER_ADMIN','MANAGER')")
    public Result<SettlementResponse> approve(@PathVariable Long id){return Result.success(service.approve(id));}
}
