package com.gym.fitness.module.agent.task;

import java.util.regex.Pattern;
import static com.gym.fitness.module.agent.AgentIdentity.invalid;

/** Apply unambiguous corrections without silently dropping the other requested goals. */
final class AgentGoalRevision {
    private AgentGoalRevision() {}
    static String apply(String goal, String amendment) {
        if (amendment.startsWith("改为任务：") || amendment.startsWith("改为任务:")) return amendment.substring(5).trim();
        if(amendment.matches("(?s).*(?:换成|换为|换到|改用|另一个).*"))throw invalid("更换操作对象请使用“改为任务：”并描述完整新目标，原任务没有被修改");
        var identity=Pattern.compile("(训练计划|训练记录|潜客|会员|学员|课程|排课|预约|账号|用户)(?:编号)?\\s*#?\\s*(\\d+)(?!\\d)");
        var target=identity.matcher(amendment);
        while(target.find())if(!Pattern.compile(Pattern.quote(target.group(1))+"(?:编号)?\\s*#?\\s*"+Pattern.quote(target.group(2))+"(?!\\d)").matcher(goal).find())throw invalid("补充中包含不同对象，请使用“改为任务：”描述完整新目标");
        String next = goal;
        String remaining=amendment;
        boolean replaced = false;
        for (String field : new String[]{"价格|费用|售价", "容量", "时长"}) {
            var pattern = Pattern.compile("(?:" + field + ")[^，,。；;\\n]*?(?:改为|改成|改到|设为|设成|调整为|调整到|调整成|修改为)\\s*[¥￥]?\\s*(\\d+(?:\\.\\d{1,2})?)");
            var change = pattern.matcher(amendment);
            if (!change.find()) continue;
            String value = change.group(1);
            var previous = pattern.matcher(next);
            int count = 0, from = -1, to = -1;
            while (previous.find()) { count++; from = previous.start(1); to = previous.end(1); }
            if (count > 1) throw invalid("存在多个修改对象，请重新创建包含具体对象和新数值的完整任务");
            if (count == 1) { next = next.substring(0, from) + value + next.substring(to); replaced = true;remaining=remaining.replaceFirst(Pattern.quote(change.group()),""); }
        }
        // Full replacement is explicit; ordinary supplements preserve existing requirements.
        remaining=remaining.replaceAll("^[，,。；;\\s]+|[，,。；;\\s]+$","");
        return replaced ? next+(remaining.isBlank()?"":"，"+remaining) : next + "，" + amendment;
    }
}
