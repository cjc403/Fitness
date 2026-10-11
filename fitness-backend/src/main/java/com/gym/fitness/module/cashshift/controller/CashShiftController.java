package com.gym.fitness.module.cashshift.controller;
import com.gym.fitness.module.cashshift.service.CashShiftService;
import com.gym.fitness.module.cashshift.dto.CashShiftDtos.*;
import com.gym.fitness.common.response.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequestMapping("/api/v1/cash-shifts") @RequiredArgsConstructor
public class CashShiftController {
 private final CashShiftService service;
 @PostMapping public Result<Map<String,Object>> open(@Valid @RequestBody OpenRequest r){return Result.success(service.open(r));}
 @GetMapping("/{id}") public Result<Map<String,Object>> detail(@PathVariable Long id){return Result.success(service.detail(id));}
 @GetMapping("/workbench") public Result<Map<String,Object>> workbench(@RequestParam(required=false) Long venueId,@RequestParam(required=false) @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate businessDate,@RequestParam(defaultValue="ALL") String status,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size){return Result.success(service.workbench(venueId,businessDate,status,page,size));}
 @GetMapping("/{id}/payments") public Result<Map<String,Object>> payments(@PathVariable Long id,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="100") int size){return Result.success(service.paymentPage(id,page,size));}
 @PostMapping("/{id}/submit") public Result<Map<String,Object>> submit(@PathVariable Long id,@Valid @RequestBody SubmitRequest r){return Result.success(service.submit(id,r));}
 @PostMapping("/{id}/review") public Result<Map<String,Object>> review(@PathVariable Long id,@Valid @RequestBody ReviewRequest r){return Result.success(service.review(id,r));}
}
