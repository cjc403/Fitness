package com.gym.fitness.common.response;

import lombok.Data;

import java.util.List;

/**
 * 分页响应结果
 */
@Data
public class PageResult<T> {
    
    private List<T> items;
    private long total;
    private int page;
    private int size;
    private int totalPages;
    
    public PageResult(List<T> items, long total, int page, int size) {
        this.items = items;
        this.total = total;
        this.page = page;
        this.size = size;
        this.totalPages = (int) Math.ceil((double) total / size);
    }
    
    public static <T> PageResult<T> of(List<T> items, long total, int page, int size) {
        return new PageResult<>(items, total, page, size);
    }
}
