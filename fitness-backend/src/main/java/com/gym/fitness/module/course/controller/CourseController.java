package com.gym.fitness.module.course.controller;

import com.gym.fitness.common.response.Result;
import com.gym.fitness.module.course.dto.CourseRequest;
import com.gym.fitness.module.course.dto.ScheduleBatchRequest;
import com.gym.fitness.module.course.dto.ScheduleBatchResult;
import com.gym.fitness.module.course.dto.ScheduleRequest;
import com.gym.fitness.module.course.dto.ScheduleUpdateRequest;
import com.gym.fitness.module.course.entity.CourseEntity;
import com.gym.fitness.module.course.entity.CourseScheduleEntity;
import com.gym.fitness.module.course.service.CourseService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@Tag(name = "课程与排课")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class CourseController {
    private final CourseService service;
    @GetMapping("/courses")
    @PreAuthorize("hasAuthority('COURSE_READ')")
    public Result<List<CourseEntity>> courses() { return Result.success(service.listCourses()); }
    @GetMapping("/coaches")
    @PreAuthorize("hasAuthority('SCHEDULE_WRITE')")
    public Result<List<CourseService.CoachOption>> coaches() { return Result.success(service.listCoaches()); }
    @PostMapping("/courses")
    @PreAuthorize("hasAuthority('COURSE_WRITE')")
    public Result<CourseEntity> create(@Valid @RequestBody CourseRequest request) { return Result.success(service.createCourse(request)); }
    @PutMapping("/courses/{id}")
    @PreAuthorize("hasAuthority('COURSE_WRITE')")
    public Result<CourseEntity> update(@PathVariable Long id, @Valid @RequestBody CourseRequest request) { return Result.success(service.updateCourse(id, request)); }
    @DeleteMapping("/courses/{id}")
    @PreAuthorize("hasAuthority('COURSE_WRITE')")
    public Result<Void> delete(@PathVariable Long id) { service.deleteCourse(id); return Result.success(); }
    @GetMapping("/schedules")
    @PreAuthorize("hasAuthority('COURSE_READ')")
    public Result<List<CourseScheduleEntity>> schedules(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return Result.success(service.listSchedules(from, to));
    }
    @PostMapping("/schedules")
    @PreAuthorize("hasAuthority('SCHEDULE_WRITE')")
    public Result<CourseScheduleEntity> schedule(@Valid @RequestBody ScheduleRequest request) { return Result.success(service.createSchedule(request)); }
    @PostMapping("/schedules/batch")
    @PreAuthorize("hasAuthority('SCHEDULE_WRITE')")
    public Result<ScheduleBatchResult> batchSchedules(@Valid @RequestBody ScheduleBatchRequest request) {
        return Result.success(service.createBatchSchedule(request));
    }
    @PutMapping("/schedules/{id}")
    @PreAuthorize("hasAuthority('SCHEDULE_WRITE')")
    public Result<CourseScheduleEntity> updateSchedule(@PathVariable Long id,
                                                       @Valid @RequestBody ScheduleUpdateRequest request) {
        return Result.success(service.updateSchedule(id, request));
    }
    @DeleteMapping("/schedules/{id}")
    @PreAuthorize("hasAuthority('SCHEDULE_WRITE')")
    public Result<Void> deleteSchedule(@PathVariable Long id) { service.deleteSchedule(id); return Result.success(); }
}
