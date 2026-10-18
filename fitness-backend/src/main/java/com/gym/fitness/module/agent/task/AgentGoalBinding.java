package com.gym.fitness.module.agent.task;

import java.util.regex.Pattern;

final class AgentGoalBinding {
    private AgentGoalBinding() {}
    static boolean nestedMatches(String goal,String path){
        var session=Pattern.compile("/training-plans/\\d+/sessions/(\\d+)/(feedback|void)$").matcher(path);
        var trial=Pattern.compile("/leads/\\d+/trials/(\\d+)/result$").matcher(path);
        String id,type,operation;
        if(session.find()){id=session.group(1);type="训练记录|执行记录|记录";operation="feedback".equals(session.group(2))?"反馈":"作废";}
        else if(trial.find()){id=trial.group(1);type="体验预约|体验课|体验|试课";operation="记录为|登记|标记|改为|改成|更新";}
        else return true;
        for(var clause:AgentGoalClauses.parse(goal)){
            var positive=AgentOperationIntent.unquoted(clause).split("但|不要|不必|无需|别",2)[0];if(!Pattern.compile(operation).matcher(positive).find())continue;
            var requested=Pattern.compile("(?:"+type+")(?:编号)?\\s*#?\\s*(\\d+)(?!\\d)").matcher(positive);
            while(requested.find())if(id.equals(requested.group(1).replaceFirst("^0+(?!$)","")))return true;
        }return false;
    }
    static boolean labelMatches(String goal,String name,long id) {
        return labelMatches(goal,name,id,"course");
    }
    static boolean labelMatches(String goal,String name,long id,String kind) {
        String type=switch(kind){case "training"->"训练计划|计划";case "booking"->"预约";case "schedule"->"排课|排期";case "member"->"会员|学员";case "lead"->"潜客";case "account"->"账号|用户";default->"课程";};
        var typed=Pattern.compile("(?:"+type+")(?:编号)?\\s*#?\\s*(\\d+)(?!\\d)").matcher(goal);
        boolean hasTyped=false;
        while(typed.find()){hasTyped=true;if(Long.toString(id).equals(typed.group(1)))return true;}
        if(hasTyped)return false;
        // A bare number is usable only in a single-object request; never borrow a
        // session/member/account number to authorize a course or training plan.
        boolean anyTyped=Pattern.compile("(?:训练计划|计划|训练记录|记录|体验预约|体验课|体验|试课|预约|排课|排期|会员|学员|潜客|账号|用户|课程)(?:编号)?\\s*#?\\s*\\d+").matcher(goal).find();
        var bare=Pattern.compile("(?:编号\\s*#?|#)\\s*(\\d+)(?!\\d)").matcher(goal);
        if(!anyTyped&&bare.find()){String value=bare.group(1);if(!bare.find()&&Long.toString(id).equals(value))return true;}
        if(name==null||name.isBlank())return false;
        String boundary=name.matches("[a-zA-Z0-9_-]+")?"(?<![a-zA-Z0-9_-])"+Pattern.quote(name)+"(?![a-zA-Z0-9_-])":"(?:^|把|将|给|为|打开|查看|编辑|修改|查找|课程|会员|潜客|学员|计划|预约|姓名为|名称为|[‘“\"「])\\s*"+Pattern.quote(name)+"(?=[’”\"」\\s，,。；;:：]|的|价格|费用|售价|容量|时长|强度|类型|发送|发(?:个|一条)?消息|用户|课程|登记|预约|安排|跟进|反馈|创建|发布|训练|编号|签到|取消|$)";
        return Pattern.compile(boundary).matcher(goal).find();
    }
    static boolean messageMatches(String goal,String name,long id,String content) {
        return AgentGoalClauses.parse(goal).stream().anyMatch(clause->singleMessageMatches(clause,name,id,content));
    }
    private static boolean singleMessageMatches(String goal,String name,long id,String content) {
        if(!labelMatches(goal,name,id,"account")||content==null||content.isBlank())return false;
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
