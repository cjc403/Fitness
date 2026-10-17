package com.gym.fitness.module.agent.task;

import java.util.regex.Pattern;

final class AgentGoalBinding {
    private AgentGoalBinding() {}
    static boolean labelMatches(String goal,String name,long id) {
        if(goal.matches("(?s).*(?:编号|账号|#)\\s*#?"+id+"(?:\\D|$).*")) return true;
        if(name==null||name.isBlank())return false;
        String boundary=name.matches("[a-zA-Z0-9_-]+")?"(?<![a-zA-Z0-9_-])"+Pattern.quote(name)+"(?![a-zA-Z0-9_-])":"(?:^|把|将|给|为|打开|查看|编辑|修改|查找|课程|会员|潜客|学员|计划|预约|姓名为|名称为|[‘“\"「])\\s*"+Pattern.quote(name)+"(?=[’”\"」\\s，,。；;:：]|的|价格|费用|售价|容量|时长|强度|类型|发送|发(?:个|一条)?消息|用户|课程|登记|预约|安排|跟进|反馈|创建|发布|训练|编号|签到|取消|$)";
        return Pattern.compile(boundary).matcher(goal).find();
    }
    static boolean messageMatches(String goal,String name,long id,String content) {
        return AgentGoalClauses.parse(goal).stream().anyMatch(clause->singleMessageMatches(clause,name,id,content));
    }
    private static boolean singleMessageMatches(String goal,String name,long id,String content) {
        if(!labelMatches(goal,name,id)||content==null||content.isBlank())return false;
        var action=Pattern.compile("发送|发(?:个|一条)?消息|发给").matcher(goal);
        if(!action.find())return false;
        String expected=goal.substring(action.end()).trim();
        if(expected.startsWith("给")&&name!=null&&expected.substring(1).startsWith(name))expected=expected.substring(name.length()+1).trim();
        if("发给".equals(action.group())&&name!=null&&expected.startsWith(name))expected=expected.substring(name.length()).trim();
        expected=expected.replaceFirst("^(?:内容(?:为|是)?|消息内容(?:为|是)?)", "").replaceFirst("^[：:，,\\s]+", "").trim();
        expected=expected.replaceFirst("^[‘’“”\\\"'「]+", "").replaceFirst("[‘’“”\\\"'」]+$", "").trim();
        return !expected.isEmpty()&&expected.equals(content.trim());
    }
}
