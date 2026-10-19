package com.gym.fitness.module.agent;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.util.List;

public final class AgentDtos {
 private AgentDtos() {}
 public record Tool(String name,String label,String description,boolean write,JsonNode parameters) {}
 public record Pagination(int page,int size,long total,int returned,List<String> keys,Integer nextPage,boolean contentTruncated) {
  public Pagination contentTruncated(boolean value){return new Pagination(page,size,total,returned,keys,nextPage,value);}
 }
 public record ToolResult(String tool,String label,JsonNode data,String source,LocalDateTime asOf,boolean truncated,Pagination pagination) {
  public ToolResult(String tool,String label,JsonNode data,String source,LocalDateTime asOf,boolean truncated){this(tool,label,data,source,asOf,truncated,null);}
 }
 public record Action(String id,String tool,String title,String summary,JsonNode parameters,String status,LocalDateTime createdAt,LocalDateTime expiresAt,JsonNode result,String error) {}
 public record Reply(String answer,List<String> suggestions,String source,String mode,List<ToolResult> results,List<Action> actions,String traceId,String contextId) {}
}
