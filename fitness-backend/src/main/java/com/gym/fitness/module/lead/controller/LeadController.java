package com.gym.fitness.module.lead.controller;
import com.gym.fitness.common.response.Result;
import com.gym.fitness.module.lead.dto.LeadDtos.*;
import com.gym.fitness.module.lead.service.LeadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/v1/leads") @RequiredArgsConstructor
public class LeadController {
    private final LeadService service;
    @GetMapping("/workbench") @PreAuthorize("hasAuthority('LEAD_READ')")
    public Result<Map<String,Object>> workbench(@RequestParam(required=false)Long venueId,@RequestParam(defaultValue="ALL")String stage,
        @RequestParam(defaultValue="ALL")String filter,@RequestParam(required=false)String keyword,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int size) {return Result.success(service.workbench(venueId,stage,filter,keyword,page,size));}
    @GetMapping("/options") @PreAuthorize("hasAuthority('LEAD_READ')")
    public Result<Map<String,Object>> options(@RequestParam(required=false)Long venueId) {return Result.success(service.options(venueId));}
    @GetMapping("/{id}") @PreAuthorize("hasAuthority('LEAD_READ')")
    public Result<Map<String,Object>> detail(@PathVariable Long id) {return Result.success(service.detail(id));}
    @GetMapping("/{id}/conversion-options") @PreAuthorize("hasAuthority('LEAD_READ')")
    public Result<List<Map<String,Object>>> conversionOptions(@PathVariable Long id) {return Result.success(service.conversionOptions(id));}
    @PostMapping({"","/"}) @PreAuthorize("hasAuthority('LEAD_WRITE')")
    public Result<Map<String,Object>> create(@Valid @RequestBody CreateRequest request) {return Result.success(service.create(request));}
    @PostMapping("/{id}/profile") @PreAuthorize("hasAuthority('LEAD_WRITE')")
    public Result<Map<String,Object>> profile(@PathVariable Long id,@Valid @RequestBody ProfileRequest request) {return Result.success(service.profile(id,request));}
    @PostMapping("/{id}/follow-ups") @PreAuthorize("hasAuthority('LEAD_WRITE')")
    public Result<Map<String,Object>> follow(@PathVariable Long id,@Valid @RequestBody FollowRequest request) {return Result.success(service.follow(id,request));}
    @PostMapping("/{id}/trials") @PreAuthorize("hasAuthority('LEAD_WRITE')")
    public Result<Map<String,Object>> trial(@PathVariable Long id,@Valid @RequestBody TrialRequest request) {return Result.success(service.bookTrial(id,request));}
    @PostMapping("/{id}/trials/{trialId}/result") @PreAuthorize("hasAuthority('LEAD_WRITE')")
    public Result<Map<String,Object>> result(@PathVariable Long id,@PathVariable Long trialId,@Valid @RequestBody TrialResultRequest request) {return Result.success(service.trialResult(id,trialId,request));}
    @PostMapping("/{id}/convert") @PreAuthorize("hasAuthority('LEAD_WRITE')")
    public Result<Map<String,Object>> convert(@PathVariable Long id,@Valid @RequestBody ConvertRequest request) {return Result.success(service.convert(id,request));}
}
