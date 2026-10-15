package com.gym.fitness.module.agent;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.util.List;

public final class AgentDtos {
 private AgentDtos() {}
 public record Tool(String name,String label,String description,boolean write,JsonNode parameters) {}
 public record ToolResult(String tool,String label,JsonNode data,String source,LocalDateTime asOf,boolean truncated) {}
 public record Action(String id,String tool,String title,String summary,JsonNode parameters,String status,LocalDateTime createdAt,LocalDateTime expiresAt,JsonNode result,String error) {}
 public record Reply(String answer,List<String> suggestions,String source,String mode,List<ToolResult> results,List<Action> actions,String traceId) {}
}
