import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, readFile, mkdir, symlink } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { ArtifactStore } from '../src/artifacts.mjs';
import { validateAction, allowedPath, ownerKey, requiresBusinessWrite } from '../src/policy.mjs';
import { TaskEngine } from '../src/engine.mjs';
import { UserData } from '../src/user-data.mjs';

test('private user identifiers become task-scoped references and resolve only at local fill',()=>{
 const data=new UserData(),other=new UserData();
 const protectedGoal=data.protect('手机号13900001234，邮箱person@example.test');
 assert.equal(protectedGoal.includes('13900001234'),false);assert.equal(protectedGoal.includes('person@example.test'),false);
 assert.equal(data.resolve('{{USER_DATA_1}}'),'13900001234');assert.throws(()=>other.resolve('{{USER_DATA_1}}'));
 assert.equal(data.protect('13900001234'),'{{USER_DATA_1}}');data.clear();assert.throws(()=>data.resolve('{{USER_DATA_1}}'));
});

const identity={userId:7,role:'COACH',venueId:2,sessionKey:'session-one',home:'/coach-workbench',permissions:['ASSISTANT_USE','COURSE_READ','COACH_WORKBENCH'],userInfo:{id:7,role:'COACH'}};
test('negated read-only instructions do not demand a write and mixed business/file goals retain the write guard',()=>{
 for(const goal of ['打开周课表，只查看，不新建、不修改、不提交任何业务','填写表单，不要保存','导出课表文件保存并打开'])assert.equal(requiresBusinessWrite(goal),false,goal);
 for(const goal of ['新建潜客','创建训练计划但不要发布','保存课程','新建会员并导出文件','帮我发一条消息'])assert.equal(requiresBusinessWrite(goal),true,goal);
});

test('a write slot is reserved before asynchronous verification; concurrent writes cannot overwrite a preview',async()=>{
 let verify;const engine=new TaskEngine({outputDirectory:tmpdir(),taskTimeoutMs:3000,waitTimeoutMs:3000},{backend:{session:()=>new Promise(r=>verify=r)}});engine.run=async()=>{};
 const view=engine.create('保存课程',identity,'fixture'),t=engine.get(view.id,identity);t.actionWindow=true;
 const first=engine.gateWrite(t,{method:'POST',path:'/api/v1/one',body:'{}',contentType:'application/json'});
 const rejected=assert.rejects(first);
 assert.equal(t.writePending,true);
 await assert.rejects(engine.gateWrite(t,{method:'POST',path:'/api/v1/two',body:'{}',contentType:'application/json'}));
 verify(identity);await new Promise(r=>setTimeout(r,0));assert.equal(t.view.pendingConfirmation.path,'/api/v1/one');
 engine.cancel(t);await rejected;await engine.close();
});
test('deadline while a confirmed write is in flight reports uncertainty and manual verification',async()=>{
 const engine=new TaskEngine({outputDirectory:tmpdir(),taskTimeoutMs:3000,waitTimeoutMs:3000},{backend:{}});engine.run=async()=>{};
 const view=engine.create('保存课程',identity,'fixture'),t=engine.get(view.id,identity);t.writePending=true;
 engine.fail(t,'执行时限');assert.match(engine.public(t).error,/人工核对.*不会自动重试/);await engine.close();
});
test('stale or invented element references cannot become arbitrary selectors',()=>{
  const obs={revision:'one',nodes:[{ref:'r1_0',role:'button',name:'课表'}]};
  assert.deepEqual(validateAction({action:'click',arguments:{ref:'r1_0'}},obs,identity,[]),{action:'click',arguments:{ref:'r1_0'}});
  for(const a of [{action:'click',arguments:{ref:'body'}},{action:'fill',arguments:{ref:'r1_0',value:'x',selector:'input'}},{action:'evaluate',arguments:{script:'x'}},{action:'navigate',arguments:{path:'https://evil.test'}},{action:'navigate',arguments:{path:'/accounts'}}]) assert.throws(()=>validateAction(a,obs,identity,[]));
  assert.equal(allowedPath('/schedule-week',identity),true);
  assert.equal(allowedPath('/member-home',identity),false);
  assert.equal(allowedPath('/schedule-week?next=https://evil.test',identity),false);
});
test('all six roles retain their own home and permission routes',()=>{
  for(const [role,home,permissions] of [['SUPER_ADMIN','/dashboard',['DASHBOARD_READ']],['MANAGER','/dashboard',['DASHBOARD_READ']],['RECEPTION','/dashboard',['DASHBOARD_READ']],['COACH','/coach-workbench',['COACH_WORKBENCH']],['SALES','/advisor-workbench',['MEMBER_READ']],['MEMBER','/member-home',['MEMBER_SELF_SERVICE']]]) assert.equal(allowedPath(home,{...identity,role,home,permissions}),true);
  assert.notEqual(ownerKey(identity),ownerKey({...identity,sessionKey:'session-two'}));
  assert.notEqual(ownerKey(identity),ownerKey({...identity,venueId:3}));
});
test('CSV is actual BOM file, formula escaped, unoverwriteable, and artifact path bound',async()=>{
  const dir=await mkdtemp(path.join(tmpdir(),'fitchain-artifact-'));
  const store=new ArtifactStore(dir,{openFile:async()=>{}});
  const artifact=await store.schedule('task-one',[{courseName:'=HYPERLINK("evil")',coachName:'教练',startTime:'2026-10-15T10:00:00',endTime:'2026-10-15T11:00:00'}],'2026-10-15','2026-10-15');
  const bytes=await readFile(artifact.path);
  assert.equal(bytes.subarray(0,3).toString('hex'),'efbbbf');
  assert.match(bytes.toString('utf8'),/'=HYPERLINK/);
  assert.equal(artifact.sha256.length,64);
  await assert.rejects(store.open('other-task',artifact.id));
  assert.equal(await store.open('task-one',artifact.id),'已请求 Windows 打开文件；尚未验证文件窗口');
  await assert.rejects(store.open('task-one',artifact.id,async()=>{throw new Error('cancelled before OS effect')}),/cancelled/);
  await assert.rejects(store.schedule('../escape',[],'2026-10-15','2026-10-15'));
  const linked=path.join(dir,'linked');
  const elsewhere=await mkdtemp(path.join(tmpdir(),'fitchain-elsewhere-'));
  await symlink(elsewhere,linked,process.platform==='win32'?'junction':'dir');
  await assert.rejects(new ArtifactStore(linked).schedule('safe',[],'2026-10-15','2026-10-15'));
});
