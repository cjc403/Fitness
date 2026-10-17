import test from 'node:test'
import assert from 'node:assert/strict'
import { createGoalContract, verifyGoalContract, roleSkills, classifyFailure } from '../../src/agent/taskContract.mjs'

test('a price mutation plus a read remains two independently required outcomes',()=>{
  const c=createGoalContract('把滨江店流瑜伽价格改成99，再告诉我本周排了几节课','SUPER_ADMIN')
  assert.equal(c.requirements.filter(r=>r.kind==='MUTATE').length,1)
  assert.equal(c.requirements.filter(r=>r.kind==='QUERY').length,1)
  const result=verifyGoalContract(c,[{kind:'MUTATE',verified:true,entityType:'course',field:'fee',expected:'99',targetLabel:'滨江店流瑜伽',receiptId:'receipt1'}])
  assert.equal(result.complete,false);assert.equal(result.partial,true)
})
test('wrong amount or unrelated message receipt cannot satisfy a course price goal',()=>{
  const c=createGoalContract('只把流瑜伽价格改为99，容量保持不变','SUPER_ADMIN')
  for(const evidence of [{kind:'MUTATE',verified:true,entityType:'course',field:'fee',expected:'109',receiptId:'x'},{kind:'MUTATE',verified:true,entityType:'message',field:'content',expected:'99',receiptId:'x'}]){
    assert.equal(verifyGoalContract(c,[evidence]).complete,false)
  }
})
test('draft-only requests forbid automatic saving and sending',()=>{
  const c=createGoalContract('打开站内消息，填好你好，先不要发送','COACH')
  assert.ok(c.prohibited.includes('SUBMIT'));assert.equal(c.requirements.some(r=>r.kind==='MUTATE'),false)
})
test('six roles have distinct skills and a coach never receives course pricing',()=>{
  const roles=['SUPER_ADMIN','MANAGER','RECEPTION','COACH','SALES','MEMBER']
  for(const role of roles)assert.ok(roleSkills(role).length>=3)
  assert.ok(!roleSkills('COACH').some(s=>s.id==='course.update'))
  assert.ok(!roleSkills('MEMBER').some(s=>s.id==='lead.trial'))
  assert.ok(roleSkills('SALES').some(s=>s.id==='lead.trial'))
})
test('uncertain writes and permission failures are never blindly retried',()=>{
  assert.equal(classifyFailure({code:'WRITE_UNKNOWN'}).strategy,'VERIFY_ONLY')
  assert.equal(classifyFailure({code:1003}).retryable,false)
  assert.equal(classifyFailure({code:'STALE_PAGE'}).strategy,'REOBSERVE')
  assert.equal(classifyFailure({code:'RATE_LIMITED'}).strategy,'WAIT')
})
test('navigation evidence must match the expected destination',()=>{
  const c=createGoalContract('打开本网页的训练计划','SUPER_ADMIN')
  assert.equal(verifyGoalContract(c,[{kind:'NAVIGATE',verified:true,path:'/dashboard'}]).complete,false)
  assert.equal(verifyGoalContract(c,[{kind:'NAVIGATE',verified:true,path:'/training-plans'}]).complete,true)
})
test('navigation wording cannot hide a send and explicit do-not-publish remains a constraint',()=>{
  assert.ok(createGoalContract('打开站内消息并给cjc2发送你好','SUPER_ADMIN').requirements.some(r=>r.kind==='MUTATE'&&r.entityType==='message'))
  assert.equal(createGoalContract('创建训练草稿，不发布，不签到','COACH').requirements.filter(r=>r.kind==='MUTATE').length,1)
})
