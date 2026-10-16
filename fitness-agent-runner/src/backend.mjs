import { AgentError, allowedPath } from './policy.mjs';

export class Backend{
 constructor(origin){const u=new URL(origin);if(!['http:','https:'].includes(u.protocol)||u.username||u.password||u.pathname!=='/')throw new AgentError('后台源配置不正确');this.origin=u.origin;}
 async request(token,path,body,signal){
  let response;try{response=await fetch(this.origin+path,{method:body===undefined?'GET':'POST',headers:{authorization:`Bearer ${token}`,'content-type':'application/json'},body:body===undefined?undefined:JSON.stringify(body),signal:signal?AbortSignal.any([signal,AbortSignal.timeout(30000)]):AbortSignal.timeout(30000),redirect:'error'});}catch{throw new AgentError('后台连接失败或调用超时，未确认业务结果',503,'UNAVAILABLE');}
  let result;try{result=await response.json();}catch{throw new AgentError('后台返回格式异常',502);}
  if(!response.ok||result.code!==0){
   if(path==='/api/v1/agent/computer/next'&&result.code===1001)throw new AgentError('模型规划参数未通过校验，请依据当前页面重新规划',422,'INVALID_DECISION');
   const status=response.status===401||[1002,1100,1101].includes(result.code)?401:response.status===403||result.code===1003?403:502;
   throw new AgentError(status===401?'登录会话已失效':status===403?'当前身份无权执行此操作':result.code===1007?'模型暂不可用，请稍后重试':result.code===2600?'智能服务调用过于频繁，请稍后重试':'后台拒绝了当前请求',status);
  }
  return result.data;
 }
 async session(token,signal){
  const s=await this.request(token,'/api/v1/agent/computer/session',undefined,signal);
  if(!s||!s.userId||!['SUPER_ADMIN','MANAGER','RECEPTION','COACH','SALES','MEMBER'].includes(s.role)||typeof s.sessionKey!=='string'||!s.sessionKey||!Array.isArray(s.permissions)||!s.permissions.includes('ASSISTANT_USE')||!allowedPath(s.home,s)||!s.userInfo||s.userInfo.mustChangePassword)throw new AgentError('后台电脑执行身份不完整或必须先修改密码',403);
  return s;
 }
 next(token,body,signal){return this.request(token,'/api/v1/agent/computer/next',body,signal);}
 async schedule(token,from,to,signal){const r=await this.request(token,'/api/v1/agent/query',{tool:'courses',arguments:{from,to}},signal);if(r?.truncated)throw new AgentError('课表数据已截断，请缩小日期范围');if(!Array.isArray(r?.data))throw new AgentError('课表数据格式不正确');return r.data;}
}
