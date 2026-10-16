import http from 'node:http';
import { timingSafeEqual } from 'node:crypto';
import { TaskEngine } from './engine.mjs';
import { AgentError, exactObject } from './policy.mjs';
import { PageFiles } from './page-files.mjs';

export function normalizeConfig(config){
 const c={port:5188,host:'127.0.0.1',headless:false,maxSteps:30,taskTimeoutMs:600000,waitTimeoutMs:300000,decisionIntervalMs:8000,...config};
 if(c.host!=='127.0.0.1')throw new AgentError('电脑执行器只允许监听127.0.0.1');
 for(const key of ['frontendOrigin','backendOrigin']){const u=new URL(c[key]);if(!['http:','https:'].includes(u.protocol)||!['localhost','127.0.0.1','[::1]'].includes(u.hostname)||u.username||u.password||u.pathname!=='/'||u.search||u.hash)throw new AgentError('电脑执行器只接受已配置的本机项目源');c[key]=u.origin;}
 if(typeof c.bridgeToken!=='string'||!c.bridgeToken||!c.outputDirectory||!c.executablePath)throw new AgentError('电脑执行器配置不完整');
 if(!Number.isInteger(c.maxSteps)||c.maxSteps<1||c.maxSteps>30||c.taskTimeoutMs<1||c.taskTimeoutMs>600000||c.waitTimeoutMs<1||c.waitTimeoutMs>300000)throw new AgentError('执行边界配置不正确');
 if(!Number.isInteger(c.decisionIntervalMs)||c.decisionIntervalMs<0||c.decisionIntervalMs>60000)throw new AgentError('模型调用间隔配置不正确');
 return c;
}
async function bodyOf(req){let bytes=0,parts=[];for await(const p of req){bytes+=p.length;if(bytes>65536)throw new AgentError('请求内容过大',413);parts.push(p);}try{return JSON.parse(Buffer.concat(parts).toString('utf8')||'{}');}catch{throw new AgentError('请求必须为JSON对象');}}
const secretEqual=(a,b)=>typeof a==='string'&&Buffer.byteLength(a)===Buffer.byteLength(b)&&timingSafeEqual(Buffer.from(a),Buffer.from(b));
export function createServer(config,dependencies={}){
 const c=normalizeConfig(config),engine=new TaskEngine(c,dependencies),files=new PageFiles(engine.backend,engine.artifacts);
 const server=http.createServer(async(req,res)=>{
  const send=(status,data,message='成功')=>{res.writeHead(status,{'content-type':'application/json;charset=utf-8','cache-control':'no-store','x-content-type-options':'nosniff'});res.end(JSON.stringify({code:status<400?0:status,message,data}));};
  try{
   if(!['127.0.0.1','::1','::ffff:127.0.0.1'].includes(req.socket.remoteAddress))throw new AgentError('仅本机桌面可以使用电脑执行器',403);
   const host=req.headers.host||'';if(!/^(?:127\.0\.0\.1|localhost):\d+$/.test(host))throw new AgentError('请求Host不受支持',403);
   if(req.headers.origin&&req.headers.origin!==c.frontendOrigin)throw new AgentError('请求源不受支持',403);
   const u=new URL(req.url,'http://127.0.0.1');if(u.search||u.hash)throw new AgentError('接口不接受查询参数');
   if(u.pathname==='/v1/health'&&req.method==='GET')return send(200,{app:'fitchain-computer-runner',projectFingerprint:c.projectFingerprint||'',pid:process.pid,port:server.address()?.port||c.port});
   if(!secretEqual(req.headers['x-fitchain-bridge-token'],c.bridgeToken))throw new AgentError('桌面桥接验证失败，请使用本机项目页面',401);
   const authorization=req.headers.authorization;if(typeof authorization!=='string'||!/^Bearer [^\s]{1,10000}$/.test(authorization))throw new AgentError('请先登录',401);
   const token=authorization.slice(7),session=await engine.backend.session(token);
   if(req.method==='GET'&&u.pathname==='/v1/status')return send(200,{available:process.platform==='win32',platform:process.platform,scope:'仅当前FitChain项目受控浏览器和专属成果目录',outputDirectory:c.outputDirectory});
   if(req.method==='GET'&&u.pathname==='/v1/tasks')return send(200,engine.list(session));
   if(req.method==='POST'&&u.pathname==='/v1/tasks'){const body=await bodyOf(req);exactObject(body,['goal']);return send(200,engine.create(body.goal,session,token));}
   if(req.method==='POST'&&['/v1/page-files/export','/v1/page-files/open'].includes(u.pathname))return send(200,await files.handle(u.pathname.endsWith('/export')?'export':'open',await bodyOf(req),session,token,()=>!res.destroyed));
   const match=/^\/v1\/tasks\/([A-Za-z0-9-]{1,80})(?:\/(pause|resume|stop|confirm|input))?$/.exec(u.pathname);
   if(!match)throw new AgentError('接口不存在',404);
   const t=engine.get(match[1],session);
   if(req.method==='GET'&&!match[2])return send(200,engine.public(t));
   if(req.method==='POST'&&match[2]){const body=await bodyOf(req);exactObject(body,match[2]==='confirm'?['confirmationId']:match[2]==='input'?['message']:[]);return send(200,engine.control(t,match[2],body));}
   throw new AgentError('接口方法不受支持',405);
  }catch(e){send(e instanceof AgentError?e.status:500,null,e instanceof AgentError?e.message:'电脑执行器内部错误');}
 });
 server.closeAgent=()=>engine.close();return server;
}
