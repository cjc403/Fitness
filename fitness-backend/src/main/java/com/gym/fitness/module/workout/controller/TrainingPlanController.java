package com.gym.fitness.module.workout.controller;
import com.gym.fitness.common.response.Result;
import com.gym.fitness.module.workout.dto.TrainingPlanDtos.*;
import com.gym.fitness.module.workout.service.TrainingPlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;
@RestController @RequestMapping("/api/v1/training-plans") @RequiredArgsConstructor
public class TrainingPlanController {
 private final TrainingPlanService service;
 @GetMapping("/students") @PreAuthorize("hasAuthority('TRAINING_PLAN_WRITE')")
 public Result<PageView<Student>> students(@RequestParam(required=false)String keyword,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int size){return Result.success(service.students(keyword,page,size));}
 @GetMapping("/workbench") @PreAuthorize("hasAuthority('TRAINING_PLAN_READ')")
 public Result<Workbench> workbench(@RequestParam(required=false)Long venueId,@RequestParam(defaultValue="ALL")String status,@RequestParam(required=false)String keyword,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int size){return Result.success(service.workbench(venueId,status,keyword,page,size,false));}
 @GetMapping("/{id}") @PreAuthorize("hasAuthority('TRAINING_PLAN_READ')")
 public Result<Detail> detail(@PathVariable Long id,@RequestParam(defaultValue="1")int sessionPage,@RequestParam(defaultValue="20")int sessionSize){return Result.success(service.detail(id,sessionPage,sessionSize,false));}
 @PostMapping({"","/"}) @PreAuthorize("hasAuthority('TRAINING_PLAN_WRITE')")
 public Result<Detail> create(@Valid @RequestBody Create r){return Result.success(service.create(r));}
 @PostMapping("/{id}/draft") @PreAuthorize("hasAuthority('TRAINING_PLAN_WRITE')")
 public Result<Detail> draft(@PathVariable Long id,@Valid @RequestBody Draft r){return Result.success(service.draft(id,r));}
 @PostMapping("/{id}/publish") @PreAuthorize("hasAuthority('TRAINING_PLAN_WRITE')")
 public Result<Detail> publish(@PathVariable Long id,@Valid @RequestBody Transition r){return Result.success(service.transition(id,r,true));}
 @PostMapping("/{id}/archive") @PreAuthorize("hasAuthority('TRAINING_PLAN_ARCHIVE')")
 public Result<Detail> archive(@PathVariable Long id,@Valid @RequestBody Transition r){return Result.success(service.transition(id,r,false));}
 @PostMapping("/{id}/sessions") @PreAuthorize("hasAuthority('TRAINING_PLAN_WRITE')")
 public Result<Detail> record(@PathVariable Long id,@Valid @RequestBody RecordSession r){return Result.success(service.record(id,r));}
 @GetMapping("/{id}/booking-options") @PreAuthorize("hasAuthority('TRAINING_PLAN_WRITE')")
 public Result<List<BookingOption>> bookingOptions(@PathVariable Long id,@RequestParam LocalDate performedOn){return Result.success(service.bookingOptions(id,performedOn));}
 @PostMapping("/{id}/sessions/{sessionId}/void") @PreAuthorize("hasAuthority('TRAINING_PLAN_WRITE')")
 public Result<Detail> voidSession(@PathVariable Long id,@PathVariable Long sessionId,@Valid @RequestBody Transition r){return Result.success(service.voidSession(id,sessionId,r));}
}
