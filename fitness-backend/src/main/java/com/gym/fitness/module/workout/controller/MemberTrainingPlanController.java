package com.gym.fitness.module.workout.controller;
import com.gym.fitness.common.response.Result;
import com.gym.fitness.module.workout.dto.TrainingPlanDtos.*;
import com.gym.fitness.module.workout.service.TrainingPlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/member/training-plans") @RequiredArgsConstructor @PreAuthorize("hasAuthority('MEMBER_SELF_SERVICE')")
public class MemberTrainingPlanController {
 private final TrainingPlanService service;
 @GetMapping({"","/"}) public Result<Workbench> list(@RequestParam(defaultValue="ALL")String status,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int size){return Result.success(service.workbench(null,status,null,page,size,true));}
 @GetMapping("/{id}") public Result<Detail> detail(@PathVariable Long id,@RequestParam(defaultValue="1")int sessionPage,@RequestParam(defaultValue="20")int sessionSize){return Result.success(service.detail(id,sessionPage,sessionSize,true));}
 @PostMapping("/{id}/sessions/{sessionId}/feedback") public Result<Detail> feedback(@PathVariable Long id,@PathVariable Long sessionId,@Valid @RequestBody Feedback r){return Result.success(service.feedback(id,sessionId,r));}
}
