package com.gym.fitness;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class FitnessApplication {
    static {
        // Existing TIMESTAMP columns store Shanghai wall time. Set the VM clock before
        // Spring/Hibernate caches it so generated follow-up and payment times agree.
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Shanghai"));
    }

    public static void main(String[] args) {
        SpringApplication.run(FitnessApplication.class, args);
    }
}
