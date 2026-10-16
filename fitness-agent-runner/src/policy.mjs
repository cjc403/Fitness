export class AgentError extends Error { constructor(message, status=400, code='INVALID_REQUEST'){super(message);this.status=status;this.code=code;} }
export const terminal = new Set(['SUCCEEDED','FAILED','CANCELLED']);
export const ownerKey = s => JSON.stringify([s.userId,s.role,s.venueId,s.sessionKey]);
export function requiresBusinessWrite(goal){
 return String(goal).split(/[，,。；;、]/).some(clause=>{
  for(const match of clause.matchAll(/保存|创建|新建|新增|修改|删除|提交|发布|退款|开卡|签到|取消预约|预约|发送|发(?:一条|条|个)?消息/g)){
   const prefix=clause.slice(0,match.index);
   if(/(?:不(?:要|用|必|需要)?(?:做|进行)?|无需|禁止|勿|别)(?:自动|直接|实际|任何|你|帮我|代我)*$/.test(prefix))continue;
   if(/保存|创建|新建|新增/.test(match[0])&&/文件|CSV|导出|课表/i.test(clause)&&!/会员|潜客|预约|消息|订单|训练计划|排课|会籍|退款|签到/.test(clause))continue;
   return true;
  }return false;
 });
}
const routes={
 '/accounts':['ACCOUNT_MANAGE'], '/dashboard':['DASHBOARD_READ'], '/analytics':['DASHBOARD_READ'],
 '/messages':['MEMBER_READ'], '/chat':[], '/members':['MEMBER_READ'],
 '/training-plans':['TRAINING_PLAN_READ',['COACH','MANAGER','SUPER_ADMIN']],
 '/cash-shifts':['CASH_SHIFT_READ',['RECEPTION','MANAGER','SUPER_ADMIN']],
 '/leads':['LEAD_READ',['SALES','MANAGER','SUPER_ADMIN']], '/retention':['MEMBER_READ',['SALES','MANAGER','SUPER_ADMIN']],
 '/commercial':['PRODUCT_READ'], '/venues':['VENUE_READ'], '/courses':['COURSE_READ'], '/schedule-week':['COURSE_READ'],
 '/bookings':['BOOKING_READ'], '/coach-workbench':['COACH_WORKBENCH',['COACH']],
 '/member-home':['MEMBER_SELF_SERVICE',['MEMBER']], '/advisor-workbench':['MEMBER_READ',['SALES']]
};
export function allowedPath(p,s){
 if(typeof p!=='string'||!/^\/[a-z][a-z-]*$/.test(p))return false;
 const r=routes[p];return !!r&&(!r[0]||s.permissions.includes(r[0]))&&(!r[1]||r[1].includes(s.role));
}
export function exactObject(o,keys){if(!o||Array.isArray(o)||typeof o!=='object'||Object.keys(o).some(k=>!keys.includes(k)))throw new AgentError('参数包含不支持的字段');}
const text=(v,n)=>typeof v==='string'&&v.length>0&&v.length<=n;
export function dateRange(from,to){
 if(!/^\d{4}-\d{2}-\d{2}$/.test(from)||!/^\d{4}-\d{2}-\d{2}$/.test(to))throw new AgentError('课表日期格式不正确');
 const a=Date.parse(from),b=Date.parse(to);
 if(!Number.isFinite(a)||!Number.isFinite(b)||new Date(a).toISOString().slice(0,10)!==from||new Date(b).toISOString().slice(0,10)!==to||b<a||b-a>30*86400000)throw new AgentError('课表时间范围须在31天内');
}
export function validateAction(d,obs,s,artifacts){
 exactObject(d,['action','arguments','summary']);const a=d.arguments;const shape={navigate:['path'],observe:[],click:['ref'],fill:['ref','value'],select:['ref','value'],scroll:['direction'],back:[],export_schedule:['from','to'],open_artifact:['artifactId'],ask_user:['question'],finish:[]};
 if(!Object.hasOwn(shape,d.action))throw new AgentError('模型返回了不允许的电脑动作');
 exactObject(a,shape[d.action]);if(shape[d.action].some(k=>!Object.hasOwn(a,k)))throw new AgentError('电脑动作缺少参数');
 if(['click','fill','select'].includes(d.action)&&!obs.nodes.some(n=>n.ref===a.ref))throw new AgentError('页面元素引用已失效，请重新观察');
 if(['fill','select'].includes(d.action)&&(!text(a.value,2000)))throw new AgentError('填写内容须为1至2000字');
 if(d.action==='navigate'&&!allowedPath(a.path,s))throw new AgentError('当前角色无权打开该项目页面',403);
 if(d.action==='scroll'&&!['up','down'].includes(a.direction))throw new AgentError('滚动方向不正确');
 if(d.action==='export_schedule')dateRange(a.from,a.to);
 if(d.action==='open_artifact'&&!artifacts.some(x=>x.id===a.artifactId))throw new AgentError('只能打开本任务已生成的成果文件');
 if(d.action==='ask_user'&&!text(a.question,500))throw new AgentError('补充问题过长或为空');
 return {action:d.action,arguments:a};
}
export function redact(value,max=6000){return String(value??'').replace(/Bearer\s+[^\s"']+/gi,'Bearer [已隐藏]').replace(/eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/g,'[令牌已隐藏]').replace(/((?:password|refresh_token|access_token|api[_-]?key|密码|口令)\s*[:=：]\s*)[^\s,;，；]+/gi,'$1[已隐藏]').slice(0,max);}
