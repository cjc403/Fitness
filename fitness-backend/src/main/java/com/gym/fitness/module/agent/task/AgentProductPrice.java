package com.gym.fitness.module.agent.task;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;
import static com.gym.fitness.module.agent.AgentIdentity.invalid;

/** Catalogue price changes only; purchases and other product mutations remain unregistered. */
final class AgentProductPrice {
    private AgentProductPrice() {}
    static boolean target(String path) { return path.matches("/api/v1/commercial/products/\\d+"); }
    static final Map<String,String> UNCHANGED = Map.ofEntries(
        Map.entry("name","name"),Map.entry("category","category"),Map.entry("productType","product_type"),
        Map.entry("description","description"),Map.entry("validityDays","validity_days"),
        Map.entry("totalUses","total_uses"),Map.entry("accessAllowed","access_allowed"),
        Map.entry("venueId","venue_id"),Map.entry("status","status"));
    static BigDecimal expected(String goal, JsonNode before) {
        if(!goal.matches("(?s).*(?:商品|会籍|商业|卡|课包).*"))throw invalid("当前目标没有指定会籍商品售价修改");
        String selected=null; boolean inherited=false;
        for(String clause:AgentGoalClauses.parse(goal)) {
            boolean own=AgentGoalBinding.labelMatches(clause,before.path("name").asText(),before.path("id").asLong(),"product");
            if(!own&&!(inherited&&AgentGoalClauses.fieldOnly(clause))){inherited=false;continue;}
            inherited=true;
            var price=Pattern.compile("(?:价格|售价|费用).*?(?:改为|改成|改到|设为|设成|调整为|调整到|调整成|修改为)\\s*[¥￥]?\\s*(\\d+(?:\\.\\d{1,2})?)(?![\\d.])").matcher(clause);
            while(price.find())selected=price.group(1);
        }
        if(selected==null)throw invalid("请明确商品名称或编号，以及要修改的售价");
        return new BigDecimal(selected);
    }
    static void guard(String goal,JsonNode before,JsonNode body) {
        BigDecimal expected=expected(goal,before);
        if(!body.path("price").isNumber()||body.path("price").decimalValue().compareTo(expected)!=0)throw invalid("拟提交商品售价与用户要求不一致");
        var allowed=new HashSet<>(UNCHANGED.keySet());allowed.addAll(Set.of("id","price"));
        body.fieldNames().forEachRemaining(key->{if(!allowed.contains(key))throw invalid("商品改价不接受额外字段："+key);});
        if(body.has("id")&&(!body.path("id").isIntegralNumber()||body.path("id").asLong()!=before.path("id").asLong()))throw invalid("商品编号与目标不一致");
        for(var field:UNCHANGED.entrySet()) {
            var value=body.path(field.getKey());
            if(value.isMissingNode()||value.isNull()) {
                if("venueId".equals(field.getKey()))value=before.path(field.getValue());
                else if("accessAllowed".equals(field.getKey()))value=com.fasterxml.jackson.databind.node.BooleanNode.TRUE;
                else if("status".equals(field.getKey()))value=com.fasterxml.jackson.databind.node.TextNode.valueOf("ACTIVE");
            }
            if(!same(value,before.path(field.getValue())))throw invalid("本次仅修改商品售价，已阻止附带修改："+field.getKey());
        }
    }
    static boolean same(JsonNode a,JsonNode b) {
        if((a.isNull()||a.isMissingNode())&&(b.isNull()||b.isMissingNode()))return true;
        if(a.isNumber()&&b.isNumber())return a.decimalValue().compareTo(b.decimalValue())==0;
        return a.equals(b);
    }
}
