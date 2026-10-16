import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { mkdtemp, readFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { createServer } from '../src/server.mjs';

const session={userId:7,role:'COACH',venueId:2,sessionKey:'fixture-session',home:'/coach-workbench',permissions:['ASSISTANT_USE','COURSE_READ','COACH_WORKBENCH'],userInfo:{id:7,role:'COACH',permissions:['ASSISTANT_USE','COURSE_READ','COACH_WORKBENCH'],organizationId:2}};
async function listen(server){await new Promise(r=>server.listen(0,'127.0.0.1',r));return `http://127.0.0.1:${server.address().port}`;}
const delay=ms=>new Promise(r=>setTimeout(r,ms));
async function until(read,predicate){for(let i=0;i<160;i++){const v=await read();if(predicate(v))return v;await delay(50);}throw new Error('Timed out waiting for task state');}
async function fixture(t,decide,options={}){
 let writes=0,queries=0,opens=0,current={...session},seen=[],invalidPlans=options.invalidPlans||0;
 const backend=http.createServer(async(req,res)=>{
  let raw='';for await(const part of req)raw+=part;
  res.setHeader('content-type','application/json');
  if(req.headers.authorization!=='Bearer fixture-token'){res.statusCode=401;return res.end(JSON.stringify({code:401,message:'no'}));}
  let data;
  if(req.url==='/api/v1/agent/computer/session')data=current;
  else if(req.url==='/api/v1/agent/computer/next'){const body=JSON.parse(raw);seen.push(body);if(invalidPlans-->0)return res.end(JSON.stringify({code:1001,message:'invalid model plan'}));data=await decide(body);}
  else if(req.url==='/api/v1/agent/query'){queries++;assert.deepEqual(JSON.parse(raw),{tool:'courses',arguments:{from:'2026-10-15',to:'2026-10-15'}});data={tool:'courses',data:[{courseName:'=测试课',coachName:'教练',startTime:'2026-10-15T10:00:00',endTime:'2026-10-15T11:00:00'}]};}
  else if(req.url==='/api/v1/fixture/write'){writes++;if(options.writeStatus)res.statusCode=options.writeStatus;data={saved:!options.writeStatus};}
  else{res.statusCode=404;data=null;}
  res.end(JSON.stringify({code:0,data}));
 });
 const backendOrigin=await listen(backend);
 const frontend=http.createServer(async(req,res)=>{
  if(req.url.startsWith('/api/')){const target=await fetch(backendOrigin+req.url,{method:req.method,headers:{authorization:req.headers.authorization||'','content-type':'application/json'},body:['GET','HEAD'].includes(req.method)?undefined:await new Promise(resolve=>{let b='';req.on('data',d=>b+=d);req.on('end',()=>resolve(b));})});res.writeHead(target.status,{'content-type':'application/json'});res.end(await target.text());return;}
  res.setHeader('content-type','text/html;charset=utf-8');res.end(`<html><head><title>受控课程夹具</title></head><body><h1>我的课程</h1><label>课程名称<input aria-label="课程名称"></label><input type="password" value="private-password"><div hidden>hidden-secret</div><button onclick="fetch('/api/v1/fixture/write',{method:'POST',headers:{'content-type':'application/json',authorization:'Bearer '+localStorage.token},body:JSON.stringify({name:document.querySelector('input').value})}).then(r=>r.json()).then(()=>document.querySelector('h1').textContent='课程已保存')">保存课程</button><a href="${backendOrigin}/api/v1/fixture/write">绝对后台地址</a><button onclick="fetch('${backendOrigin}/api/v1/fixture/write',{method:'POST',body:'bypass'}).catch(()=>{})">绕过代理</button><a href="https://example.invalid/">外站</a><a href="mailto:private@example.test">外部应用</a><p>周课表</p></body></html>`);
 });
 const frontendOrigin=await listen(frontend);
 const outputDirectory=await mkdtemp(path.join(tmpdir(),'fitchain-browser-'));
 const runner=createServer({bridgeToken:'bridge-fixture',frontendOrigin,backendOrigin,outputDirectory,headless:true,decisionIntervalMs:0,executablePath:'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe',projectFingerprint:'fixture-fingerprint',maxSteps:12,waitTimeoutMs:5000,...options},{openFile:async()=>{opens++;}});
 const origin=await listen(runner);
 t.after(async()=>{await runner.closeAgent();await Promise.all([new Promise(r=>runner.close(r)),new Promise(r=>frontend.close(r)),new Promise(r=>backend.close(r))]);});
 const api=async(url,body,headers={})=>{const response=await fetch(origin+'/v1'+url,{method:body===undefined?'GET':'POST',headers:{authorization:'Bearer fixture-token','x-fitchain-bridge-token':'bridge-fixture','content-type':'application/json',...headers},body:body===undefined?undefined:JSON.stringify(body)});return {status:response.status,...await response.json()};};
 return {api,origin,seen,counts:()=>({writes,queries,opens}),changeSession:s=>current={...current,...s}};
}
test('loopback API verifies bridge and current session on every private request',async t=>{
 const f=await fixture(t,()=>({action:'ask_user',arguments:{question:'请选择日期'}}));
 const health=await (await fetch(f.origin+'/v1/health')).json();assert.equal(health.data.app,'fitchain-computer-runner');assert.equal(JSON.stringify(health).includes('bridge-fixture'),false);
 assert.equal((await f.api('/status',undefined,{'x-fitchain-bridge-token':'wrong'})).status,401);
 const created=(await f.api('/tasks',{goal:'查看课程'})).data;
 const waiting=await until(()=>f.api('/tasks/'+created.id),r=>r.data?.status==='WAITING_INPUT');
 assert.match(waiting.data.screenshot,/^data:image\/jpeg;base64,/);
 assert.equal(f.seen[0].observation.text.includes('hidden-secret'),false);
 assert.equal(JSON.stringify(f.seen[0]).includes('private-password'),false);
 f.changeSession({venueId:9});assert.equal((await f.api('/tasks/'+created.id)).status,403);
 f.changeSession({venueId:2});assert.equal((await f.api('/tasks/'+created.id+'/stop',{})).data.status,'CANCELLED');
});
test('actual browser fill and click freeze a business request until one human confirmation',async t=>{
 let n=0;
 const f=await fixture(t,body=>{
  const byName=name=>body.observation.nodes.find(x=>x.name===name)?.ref;
  const actions=[{action:'fill',arguments:{ref:byName('课程名称'),value:'真实输入'}},{action:'click',arguments:{ref:byName('保存课程')}},{action:'export_schedule',arguments:{from:'2026-10-15',to:'2026-10-15'}},{action:'open_artifact',arguments:{artifactId:body.artifacts[0]?.id}},{action:'finish',arguments:{}}];return actions[n++];
 });
 const task=(await f.api('/tasks',{goal:'填写课程、保存并生成打开课表'})).data;
 const waiting=await until(()=>f.api('/tasks/'+task.id),r=>r.data?.status==='WAITING_CONFIRMATION');
 assert.equal(f.counts().writes,0);assert.match(JSON.stringify(waiting.data.pendingConfirmation.fields),/真实输入/);
 const id=waiting.data.pendingConfirmation.id;
 assert.equal((await f.api('/tasks/'+task.id+'/confirm',{confirmationId:id})).status,200);
 const duplicate=await f.api('/tasks/'+task.id+'/confirm',{confirmationId:id});assert.equal(duplicate.status,409);
 const done=await until(()=>f.api('/tasks/'+task.id),r=>['SUCCEEDED','FAILED'].includes(r.data?.status));
 assert.equal(done.data.status,'SUCCEEDED',done.data.error);assert.deepEqual(f.counts(),{writes:1,queries:1,opens:1});
 assert.match((await readFile(done.data.artifacts[0].path)).toString('utf8'),/'=测试课/);
 assert.match(f.seen[1].history[0].summary,/课程名称.*真实输入/);
 assert.match(done.data.steps.find(x=>x.action==='open_artifact').detail,/尚未验证/);
 assert.equal(JSON.stringify(f.seen).includes('data:image'),false);
});
test('pause stops further decisions and stop aborts the pending request without a write',async t=>{
 const f=await fixture(t,body=>({action:'click',arguments:{ref:body.observation.nodes.find(x=>x.name==='保存课程').ref}}));
 const task=(await f.api('/tasks',{goal:'保存课程'})).data;
 await until(()=>f.api('/tasks/'+task.id),r=>r.data?.status==='WAITING_CONFIRMATION');
 assert.equal((await f.api('/tasks/'+task.id+'/pause',{})).data.status,'PAUSED');
 await delay(120);assert.equal(f.seen.length,1);
 assert.equal((await f.api('/tasks/'+task.id+'/stop',{})).data.status,'CANCELLED');assert.equal(f.counts().writes,0);
});
test('absolute backend API bypass is blocked by browser route before reaching business server',async t=>{
 let n=0;const f=await fixture(t,body=>n++===0?{action:'click',arguments:{ref:body.observation.nodes.find(x=>x.name==='绕过代理').ref}}:{action:'finish',arguments:{}});
 const task=(await f.api('/tasks',{goal:'测试安全边界'})).data;
 const done=await until(()=>f.api('/tasks/'+task.id),r=>['SUCCEEDED','FAILED'].includes(r.data?.status));
 assert.equal(f.counts().writes,0);assert.equal(done.data.status,'FAILED');
});
test('a model finish without an executed effect cannot report task success',async t=>{
 const f=await fixture(t,()=>({action:'finish',arguments:{}}));
 const task=(await f.api('/tasks',{goal:'生成课表文件'})).data;
 const done=await until(()=>f.api('/tasks/'+task.id),r=>['SUCCEEDED','FAILED'].includes(r.data?.status));
 assert.equal(done.data.status,'FAILED');assert.equal(done.data.artifacts.length,0);
});
test('generating a file alone cannot satisfy an explicit request to open it',async t=>{
 let n=0;const f=await fixture(t,()=>n++===0?{action:'export_schedule',arguments:{from:'2026-10-15',to:'2026-10-15'}}:{action:'finish',arguments:{}});
 const task=(await f.api('/tasks',{goal:'生成课表文件并打开'})).data;
 const done=await until(()=>f.api('/tasks/'+task.id),r=>['SUCCEEDED','FAILED'].includes(r.data?.status));
 assert.equal(done.data.status,'FAILED');assert.match(done.data.error,/尚未请求系统打开/);assert.equal(f.counts().opens,0);
});
test('repeated identical export and open decisions do not create duplicate files or windows',async t=>{
 let n=0;const f=await fixture(t,body=>n++<2?{action:'export_schedule',arguments:{from:'2026-10-15',to:'2026-10-15'}}:n<5?{action:'open_artifact',arguments:{artifactId:body.artifacts[0].id}}:{action:'finish',arguments:{}});
 const task=(await f.api('/tasks',{goal:'生成课表文件并打开'})).data;
 const done=await until(()=>f.api('/tasks/'+task.id),r=>['SUCCEEDED','FAILED'].includes(r.data?.status));
 assert.equal(done.data.status,'SUCCEEDED',done.data.error);assert.equal(done.data.artifacts.length,1);assert.deepEqual(f.counts(),{writes:0,queries:1,opens:1});
});
test('model uses a private data reference and the confirmed browser form receives the actual local value',async t=>{
 let n=0;const f=await fixture(t,body=>n++===0?{action:'fill',arguments:{ref:body.observation.nodes.find(x=>x.name==='课程名称').ref,value:'{{USER_DATA_1}}'}}:n===2?{action:'click',arguments:{ref:body.observation.nodes.find(x=>x.name==='保存课程').ref}}:{action:'finish',arguments:{}});
 const task=(await f.api('/tasks',{goal:'填写手机13900001234并保存课程'})).data;
 const pending=await until(()=>f.api('/tasks/'+task.id),r=>r.data?.status==='WAITING_CONFIRMATION');
 assert.equal(f.seen[0].goal.includes('13900001234'),false);assert.match(f.seen[0].goal,/USER_DATA_1/);
 assert.equal(pending.data.pendingConfirmation.fields[0].value,'13900001234');assert.equal(f.counts().writes,0);
 await f.api('/tasks/'+task.id+'/confirm',{confirmationId:pending.data.pendingConfirmation.id});
 const done=await until(()=>f.api('/tasks/'+task.id),r=>['SUCCEEDED','FAILED'].includes(r.data?.status));assert.equal(done.data.status,'SUCCEEDED',done.data.error);assert.equal(f.counts().writes,1);
});
test('resume restores input wait, supplied input reaches exactly the next decision, one machine task only',async t=>{
 let n=0;const f=await fixture(t,body=>n++===0?{action:'ask_user',arguments:{question:'请选择日期'}}:body.input==='2026-10-15'?{action:'navigate',arguments:{path:'/schedule-week'}}:{action:'finish',arguments:{}});
 const task=(await f.api('/tasks',{goal:'打开课表'})).data;
 await until(()=>f.api('/tasks/'+task.id),r=>r.data?.status==='WAITING_INPUT');
 assert.equal((await f.api('/tasks',{goal:'第二任务'})).status,409);
 assert.equal((await f.api('/tasks/'+task.id+'/pause',{})).data.status,'PAUSED');
 assert.equal((await f.api('/tasks/'+task.id+'/resume',{})).data.status,'WAITING_INPUT');
 assert.equal((await f.api('/tasks/'+task.id+'/input',{message:'2026-10-15'})).status,200);
 const done=await until(()=>f.api('/tasks/'+task.id),r=>['SUCCEEDED','FAILED'].includes(r.data?.status));
 assert.equal(done.data.status,'SUCCEEDED',done.data.error);assert.equal(f.seen[1].input,'2026-10-15');assert.equal(f.seen[2].input,undefined);
});
test('a revoked login session never releases a frozen write',async t=>{
 const f=await fixture(t,body=>({action:'click',arguments:{ref:body.observation.nodes.find(x=>x.name==='保存课程').ref}}));
 const task=(await f.api('/tasks',{goal:'保存课程'})).data;
 const pending=await until(()=>f.api('/tasks/'+task.id),r=>r.data?.status==='WAITING_CONFIRMATION');
 f.changeSession({sessionKey:'revoked-and-new-session'});
 assert.equal((await f.api('/tasks/'+task.id+'/confirm',{confirmationId:pending.data.pendingConfirmation.id})).status,403);
 assert.equal(f.counts().writes,0);
});
test('session change stops a waiting task even without another step or confirmation',async t=>{
 const f=await fixture(t,()=>({action:'ask_user',arguments:{question:'请选择日期'}}));
 const task=(await f.api('/tasks',{goal:'打开课表'})).data;
 await until(()=>f.api('/tasks/'+task.id),r=>r.data?.status==='WAITING_INPUT');
 f.changeSession({sessionKey:'replacement-session'});await delay(2400);f.changeSession({sessionKey:'fixture-session'});
 const done=(await f.api('/tasks/'+task.id)).data;assert.equal(done.status,'FAILED');assert.match(done.error,/会话已变化/);assert.equal(f.counts().writes,0);
});
test('each of the six roles actually opens its own browser homepage',async t=>{
 const f=await fixture(t,()=>({action:'ask_user',arguments:{question:'请补充日期'}}));
 for(const [role,home,permissions] of [['SUPER_ADMIN','/dashboard',['DASHBOARD_READ']],['MANAGER','/dashboard',['DASHBOARD_READ']],['RECEPTION','/dashboard',['DASHBOARD_READ']],['COACH','/coach-workbench',['COACH_WORKBENCH']],['SALES','/advisor-workbench',['MEMBER_READ']],['MEMBER','/member-home',['MEMBER_SELF_SERVICE']]]){
  f.changeSession({role,home,permissions:['ASSISTANT_USE',...permissions],userInfo:{...session.userInfo,role,permissions:['ASSISTANT_USE',...permissions]}});
  const task=(await f.api('/tasks',{goal:'查看我的首页'})).data;
  const ready=await until(()=>f.api('/tasks/'+task.id),r=>r.data?.status==='WAITING_INPUT');
  assert.equal(new URL(f.seen.at(-1).observation.url).pathname,home);
  assert.match(ready.data.screenshot,/^data:image\/jpeg;base64,/);
  assert.equal((await f.api('/tasks/'+task.id+'/stop',{})).data.status,'CANCELLED');
 }
});
test('confirmation expiration aborts the frozen request and never sends a write',async t=>{
 const f=await fixture(t,body=>({action:'click',arguments:{ref:body.observation.nodes.find(x=>x.name==='保存课程').ref}}),{waitTimeoutMs:200});
 const task=(await f.api('/tasks',{goal:'保存课程'})).data;
 const pending=await until(()=>f.api('/tasks/'+task.id),r=>r.data?.status==='WAITING_CONFIRMATION');
 const done=await until(()=>f.api('/tasks/'+task.id),r=>r.data?.status==='FAILED');
 assert.match(done.data.error,/超时/);assert.equal(f.counts().writes,0);
 assert.equal((await f.api('/tasks/'+task.id+'/confirm',{confirmationId:pending.data.pendingConfirmation.id})).status,409);
});
test('an executed fill alone cannot pretend that a requested business save succeeded',async t=>{
 let n=0;const f=await fixture(t,body=>n++===0?{action:'fill',arguments:{ref:body.observation.nodes.find(x=>x.name==='课程名称').ref,value:'未保存'}}:{action:'finish',arguments:{}});
 const task=(await f.api('/tasks',{goal:'填写并保存课程'})).data;
 const done=await until(()=>f.api('/tasks/'+task.id),r=>['SUCCEEDED','FAILED'].includes(r.data?.status));
 assert.equal(done.data.status,'FAILED');assert.equal(f.counts().writes,0);assert.match(done.data.error,/业务回执/);
});
test('a failed confirmed business write is reported once and never retried',async t=>{
 const f=await fixture(t,body=>({action:'click',arguments:{ref:body.observation.nodes.find(x=>x.name==='保存课程').ref}}),{writeStatus:409});
 const task=(await f.api('/tasks',{goal:'保存课程'})).data;
 const pending=await until(()=>f.api('/tasks/'+task.id),r=>r.data?.status==='WAITING_CONFIRMATION');
 await f.api('/tasks/'+task.id+'/confirm',{confirmationId:pending.data.pendingConfirmation.id});
 const done=await until(()=>f.api('/tasks/'+task.id),r=>r.data?.status==='FAILED');
 assert.equal(f.counts().writes,1);assert.equal(f.seen.length,1);assert.match(done.data.error,/拒绝/);
});
test('step budget stops a model that keeps requesting more observations',async t=>{
 const f=await fixture(t,()=>({action:'observe',arguments:{}}),{maxSteps:3});
 const task=(await f.api('/tasks',{goal:'查看课程'})).data;
 const done=await until(()=>f.api('/tasks/'+task.id),r=>r.data?.status==='FAILED');
 assert.equal(f.seen.length,3);assert.equal(done.data.steps.length,3);assert.match(done.data.error,/上限/);
});
test('a rejected plan is observed again without replaying any business action and retry is bounded',async t=>{
 let n=0;const f=await fixture(t,()=>n++===0?{action:'navigate',arguments:{path:'/schedule-week'}}:{action:'finish',arguments:{}},{invalidPlans:1});
 const task=(await f.api('/tasks',{goal:'打开课表'})).data;
 const done=await until(()=>f.api('/tasks/'+task.id),r=>['SUCCEEDED','FAILED'].includes(r.data?.status));
 assert.equal(done.data.status,'SUCCEEDED',done.data.error);assert.equal(done.data.steps[0].status,'FAILED');assert.equal(f.counts().writes,0);assert.equal(f.seen.length,3);
});
test('three invalid planning responses stop without any browser write',async t=>{
 const f=await fixture(t,()=>{throw new Error('Must reject before planning');},{invalidPlans:3});
 const task=(await f.api('/tasks',{goal:'保存课程'})).data;
 const done=await until(()=>f.api('/tasks/'+task.id),r=>r.data?.status==='FAILED');
 assert.equal(f.seen.length,3);assert.equal(done.data.steps.length,2);assert.equal(f.counts().writes,0);
});
test('external application links are rejected before the click can invoke an OS handler',async t=>{
 const f=await fixture(t,body=>({action:'click',arguments:{ref:body.observation.nodes.find(x=>x.name==='外部应用').ref}}));
 const task=(await f.api('/tasks',{goal:'打开外部链接'})).data;
 const done=await until(()=>f.api('/tasks/'+task.id),r=>r.data?.status==='FAILED');
 assert.match(done.data.error,/链接/);
});
