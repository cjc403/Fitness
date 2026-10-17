package com.gym.fitness.module.agent.task;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.time.LocalDateTime;

/** Retention applies only to terminal task diagnostics, never domain records or business audit. */
@Component @RequiredArgsConstructor
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="spring.flyway.enabled",havingValue="true")
public class AgentTaskMaintenance {
    private final JdbcTemplate jdbc;private final PlatformTransactionManager transactions;
    @Scheduled(initialDelay=600000,fixedDelay=3600000)
    public void purgeExpired(){
        new TransactionTemplate(transactions).execute(ignored->{
            var ids=jdbc.queryForList("SELECT id FROM agent_task WHERE status IN ('SUCCEEDED','PARTIAL','FAILED','CANCELLED') AND updated_at<? ORDER BY updated_at LIMIT 100 FOR UPDATE",String.class,LocalDateTime.now().minusDays(30));
            for(var id:ids){jdbc.update("DELETE FROM agent_task_receipt WHERE task_id=?",id);jdbc.update("DELETE FROM agent_task_step WHERE task_id=?",id);jdbc.update("DELETE FROM agent_task WHERE id=?",id);}return null;
        });
    }
}
