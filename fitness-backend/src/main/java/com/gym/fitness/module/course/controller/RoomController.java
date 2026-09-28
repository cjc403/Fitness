package com.gym.fitness.module.course.controller;

import com.gym.fitness.common.response.Result;
import com.gym.fitness.module.course.dto.RoomDtos.*;
import com.gym.fitness.module.course.service.RoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** 教室/场地资源与当日占用。 */
@RestController
@RequestMapping("/api/v1/admin/rooms")
@RequiredArgsConstructor
public class RoomController {
    private final RoomService service;

    @GetMapping
    @PreAuthorize("hasAuthority('VENUE_READ')")
    public Result<List<RoomView>> list() {
        return Result.success(service.list());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('VENUE_WRITE')")
    public Result<RoomView> create(@Valid @RequestBody RoomRequest request) {
        return Result.success(service.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('VENUE_WRITE')")
    public Result<RoomView> update(@PathVariable Long id, @Valid @RequestBody RoomRequest request) {
        return Result.success(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('VENUE_WRITE')")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.success();
    }

    @GetMapping("/occupancy")
    @PreAuthorize("hasAuthority('VENUE_READ')")
    public Result<List<RoomOccupancy>> occupancy(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.success(service.occupancy(date));
    }
}
