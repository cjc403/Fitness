package com.gym.fitness.module.assistant.service;

import com.gym.fitness.common.constant.ErrorCode;
import com.gym.fitness.common.exception.BusinessException;
import com.gym.fitness.config.AiProperties;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AiRateLimiter {
    private final AiProperties properties;
    private final Map<Long, Deque<Instant>> requestTimes = new ConcurrentHashMap<>();

    public AiRateLimiter(AiProperties properties) {
        this.properties = properties;
    }

    public void check(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        Instant cutoff = Instant.now().minusSeconds(60);
        Deque<Instant> times = requestTimes.computeIfAbsent(userId, ignored -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && times.peekFirst().isBefore(cutoff)) {
                times.removeFirst();
            }
            if (times.size() >= Math.max(1, properties.getRequestsPerMinute())) {
                throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "智能服务请求过于频繁，请稍后再试");
            }
            times.addLast(Instant.now());
        }
    }
}
