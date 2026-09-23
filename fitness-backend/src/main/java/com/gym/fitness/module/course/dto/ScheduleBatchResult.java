package com.gym.fitness.module.course.dto;

import java.util.List;

/**
 * 批量排课结果。{@code created} 是本次（或将）生成的课次，{@code skipped} 是跳过的课次及原因。
 * {@code dryRun=true} 表示这是预检结果，数据库未发生变化。
 */
public record ScheduleBatchResult(List<Slot> created, List<Slot> skipped, boolean dryRun) {
    public record Slot(String label, String note) {
    }
}
