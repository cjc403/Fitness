package com.gym.fitness.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 开启定时任务调度（任务本身见 module.operations.job）。 */
@Configuration
@EnableScheduling
public class OperationsSchedulingConfig {
}
