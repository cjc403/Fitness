import assert from 'node:assert/strict'
import {fixtureApi,seedFreeSchedule,day} from './fixtures.mjs'
export const queryRows=v=>Array.isArray(v)?v:v?.records||[]
const ids=(v,key='id')=>queryRows(v).map(x=>x[key]??x.id).sort((a,b)=>a-b)
function dates(goal){
 if(!/本周|下周/.test(goal))return {from:day(),to:day(7)}
 const now=new Date(),dow=now.getDay()||7;now.setDate(now.getDate()-dow+1+(goal.includes('下周')?7:0))
 const local=d=>[d.getFullYear(),String(d.getMonth()+1).padStart(2,'0'),String(d.getDate()).padStart(2,'0')].join('-'),from=local(now);now.setDate(now.getDate()+6);return {from,to:local(now)}
}
export async function bindQuery(c){
 let goal=c.goal;const suffix=Date.now().toString(36)
 if(goal.includes('{leadName}')){const api=await fixtureApi('advisor'),name='查询潜客验收'+suffix;await api('/leads','POST',{name,phone:'139'+Date.now().toString().slice(-8),source:'WALK_IN',intent:'HIGH',idempotencyKey:'query_'+suffix});goal=goal.replaceAll('{leadName}',name)}
 if(goal.includes('{courseName}')){const name='查询课程验收'+suffix;await seedFreeSchedule(name,2);goal=goal.replaceAll('{courseName}',name)}
 return {...c,goal}
}
async function allPages(api,path,key){
 const out=[];for(let page=1;page<=12;page++){const v=await api(path+(path.includes('?')?'&':'?')+'page='+page+'&size=20'),container=key?v[key]:v;out.push(...queryRows(container));if(out.length>=Number(container.total??out.length))return out}
 throw Error('Original API oracle needs more than 12 pages; fixture must be bounded')
}
export async function checkQuery(api,c,task){
 const goal=c.goal,topic=goal.includes('身份')?'profile':goal.includes('接待顾问')?'lead_options':goal.includes('课程')?'courses':goal.includes('预约')?'bookings':goal.includes('训练')?'training_plans':goal.includes('学员')?'students':goal.includes('潜客')?'leads':goal.includes('回访')?'retention':goal.includes('交班')?'cash_summary':'members'
 const facts=(task.facts||[]).filter(f=>f.tool===topic);assert.ok(facts.length,'Expected real query: '+topic);let fact=facts.at(-1)
 assert.ok(Date.now()-new Date(fact.asOf).getTime()<300000,'Query evidence must be fresh')
 const key={training_plans:'plans',leads:'leads',cash_summary:'shifts',retention:'members',lead_options:'advisors'}[topic]
 let actualRows
 if(fact.pagination){
  const latestFirst=facts.findLastIndex(f=>f.pagination?.page===1),pages=facts.slice(Math.max(0,latestFirst));const byPage=new Map(pages.map(f=>[f.pagination?.page,f]))
  assert.equal(byPage.size,Math.max(1,Math.ceil(fact.pagination.total/fact.pagination.size)))
  actualRows=[...byPage.values()].sort((a,b)=>a.pagination.page-b.pagination.page).flatMap(f=>{assert.equal(f.pagination.contentTruncated,false);return queryRows(key?f.data[key]:f.data)})
 }else{assert.equal(fact.truncated,false);actualRows=queryRows(key?fact.data[key]:fact.data)}
 const keyword=goal.match(/(?:姓名为|名称为|关键词[为是]?|叫做)[‘“"「]?([^的，,。；;‘’“”"「」]+)/)?.[1]?.trim()||'',encoded=encodeURIComponent(keyword)
 let expected,actual=ids(actualRows)
 if(topic==='profile'){assert.equal(fact.data.role,c.role);return {topic,role:c.role}}
 if(topic==='members'){expected=ids(await allPages(api,'/admin/members?keyword='+encoded));if(goal.includes('本店')){const s=await api('/agent/computer/session');expected=ids((await allPages(api,'/admin/members?keyword='+encoded)).filter(x=>x.venueId===s.venueId))}}
 if(topic==='students')expected=ids(await allPages(api,'/training-plans/students?keyword='+encoded))
 if(topic==='training_plans')expected=ids(await allPages(api,(c.role==='MEMBER'?'/member/training-plans':'/training-plans/workbench')+'?status=ALL&keyword='+encoded,'plans'))
 if(topic==='leads')expected=ids(await allPages(api,'/leads/workbench?stage=ALL&filter=ALL&keyword='+encoded,'leads'))
 if(topic==='bookings')expected=ids(await api(c.role==='MEMBER'?'/member/bookings':'/admin/bookings'))
 if(topic==='courses'){
  const {from,to}=dates(goal),member=c.role==='MEMBER',v=await api(member?'/member/schedules':'/admin/schedules?from='+from+'&to='+to),s=await api('/agent/computer/session')
  expected=ids(queryRows(v).filter(x=>{const date=(x.startTime||x.scheduledStartTime||'').slice(0,10);return date>=from&&date<=to&&(c.role!=='COACH'||x.coachId===s.userId)&&(!keyword||x.courseName?.includes(keyword)||x.course?.name?.includes(keyword))}),member?'scheduleId':'id');actual=ids(actualRows,'scheduleId')
 }
 if(topic==='retention'){expected=ids((await api('/admin/advisor/retention?filter=ALL')).members,'memberId');actual=ids(actualRows,'memberId')}
 if(topic==='lead_options'){const advisors=(await api('/leads/options')).advisors;expected=ids(advisors);actual=ids(actualRows);for(const row of actualRows)assert.equal(row.venueId,advisors.find(a=>a.id===row.id)?.venueId,'Advisor venue differs from original authorized API')}
 if(topic==='cash_summary')expected=ids(await allPages(api,'/cash-shifts/workbench?day='+day()+'&status=ALL','shifts'))
 assert.ok(expected&&actual,'Missing independent original API oracle: '+topic);assert.deepEqual(actual,expected,'Business result differs from original scoped API');return {topic,expectedIds:expected,actualIds:actual}
}
