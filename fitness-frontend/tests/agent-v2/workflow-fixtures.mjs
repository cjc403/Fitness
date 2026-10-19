import assert from 'node:assert/strict'
import {readFile} from 'node:fs/promises'
import {spawnSync} from 'node:child_process'
import {fixtureApi,assertV2Target,day} from './fixtures.mjs'
export const roleAccounts={SUPER_ADMIN:['admin','/dashboard'],MANAGER:['manager','/dashboard'],RECEPTION:['reception','/members'],COACH:['coach','/coach-workbench'],SALES:['advisor','/advisor-workbench'],MEMBER:['member','/member-home']}
let sequence=0
const identifier=()=>Date.now().toString(36)+'_'+(++sequence)
const key=()=> 'qa_'+identifier()
const sameNumber=(a,b)=>assert.equal(Number(a),Number(b))
const replace=(s,vars)=>s.replace(/\{(\w+)\}/g,(_m,k)=>{assert.notEqual(vars[k],undefined,'Missing frozen fixture variable: '+k);return String(vars[k])})
const timestamp=d=>[d.getFullYear(),String(d.getMonth()+1).padStart(2,'0'),String(d.getDate()).padStart(2,'0')].join('-')+'T'+[d.getHours(),d.getMinutes(),d.getSeconds()].map(x=>String(x).padStart(2,'0')).join(':')
async function shiftFixture(table,id,start,end){
 await assertV2Target();assert.ok(['course_schedule','lead_trial'].includes(table));assert.ok(Number.isSafeInteger(id)&&id>0)
 for(const text of [start,end])assert.match(text,/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}$/)
 const password=(await readFile(new URL('../../../../.agent-v2-20261017/qa-db-password.txt',import.meta.url),'utf8')).trim(),fields=table==='course_schedule'?['scheduled_start_time','scheduled_end_time']:['starts_at','ends_at']
 const sql=`BEGIN; DO $guard$ BEGIN IF (SELECT id FROM public.codex_agent_v2_isolation_marker) IS DISTINCT FROM 'agent_v2_20261017' THEN RAISE EXCEPTION 'wrong fixture database'; END IF; END $guard$; UPDATE public.${table} SET ${fields[0]}='${start}'::timestamp,${fields[1]}='${end}'::timestamp WHERE id=${id}; COMMIT;`
 const r=spawnSync('E:/PostgreSQL/bin/psql.exe',['-w','-h','127.0.0.1','-p','55457','-U','agent_v2','-d','agent_v2_browser','-v','ON_ERROR_STOP=1','-At'],{input:sql,env:{...process.env,PGPASSWORD:password},encoding:'utf8',windowsHide:true})
 assert.equal(r.status,0,'Exclusive fixture time shift failed')
}
async function freeSchedule(label,venueId){
 const admin=await fixtureApi('admin'),course=await admin('/admin/courses','POST',{name:label,type:'REGULAR',durationMinutes:45,maxCapacity:20,fee:0,intensity:'LOW',venueId,description:'Isolated Agent V2 workflow fixture'})
 const schedule=await admin('/admin/schedules','POST',{courseId:course.id,venueId,coachName:'独占库验收教练',scheduledStartTime:day(2)+'T14:00:00',scheduledEndTime:day(2)+'T14:45:00',maxStudents:20})
 return {course,schedule,admin}
}
async function freeTrialTimes(advisor){
 await assertV2Target();assert.ok(Number.isSafeInteger(advisor.id)&&Number.isSafeInteger(advisor.venueId))
 const password=(await readFile(new URL('../../../../.agent-v2-20261017/qa-db-password.txt',import.meta.url),'utf8')).trim()
 const sql=`BEGIN; DO $guard$ BEGIN IF (SELECT id FROM public.codex_agent_v2_isolation_marker) IS DISTINCT FROM 'agent_v2_20261017' THEN RAISE EXCEPTION 'wrong fixture database'; END IF; END $guard$; SELECT starts_at,ends_at FROM public.lead_trial WHERE host_id=${advisor.id} AND venue_id=${advisor.venueId} AND status='BOOKED'; COMMIT;`
 const result=spawnSync('E:/PostgreSQL/bin/psql.exe',['-w','-h','127.0.0.1','-p','55457','-U','agent_v2','-d','agent_v2_browser','-v','ON_ERROR_STOP=1','-At'],{input:sql,env:{...process.env,PGPASSWORD:password},encoding:'utf8',windowsHide:true});assert.equal(result.status,0,'Exclusive trial precondition check failed')
 const occupied=result.stdout.split(/\r?\n/).filter(s=>s.includes('|')).map(s=>s.split('|').map(t=>new Date(t.replace(' ','T')).getTime()))
 for(let offset=2;offset<9;offset++)for(let minutes=9*60;minutes+30<=21*60;minutes+=45){const start=new Date(day(offset)+'T00:00:00');start.setMinutes(minutes);const end=new Date(start.getTime()+1800000);if(!occupied.some(([a,b])=>a<end.getTime()&&start.getTime()<b))return {trialStart:timestamp(start).replace('T',' ').slice(0,16),trialEnd:timestamp(end).replace('T',' ').slice(0,16)}}
 throw Error('No free advisor interval in the bounded exclusive fixture window')
}
async function planFixture(memberFeedback=false,publish=false){
 const coach=await fixtureApi('coach'),students=(await coach('/training-plans/students?page=1&size=20')).records
 const own=memberFeedback?await(await fixtureApi('member'))('/member/profile'):null,target=memberFeedback?students.find(s=>s.id===own.id):students[0];assert.ok(target,'Fixture needs an authorized active student')
 const existing=(await coach('/training-plans/workbench?status=ACTIVE&page=1&size=20&keyword='+encodeURIComponent(target.realName))).plans.records.filter(p=>p.memberId===target.id&&p.status==='ACTIVE')
 const admin=await fixtureApi('admin');for(const p of existing)await admin('/training-plans/'+p.id+'/archive','POST',{expectedVersion:p.version,idempotencyKey:key(),reason:'独占验收夹具建立独立训练周期'})
 const title='训练验收'+identifier();let detail=await coach('/training-plans','POST',{memberId:target.id,title,goal:'ENDURANCE',startDate:day(),endDate:day(7),exercises:[{name:'深蹲',mode:'STRENGTH',sets:3,reps:10,weightKg:0}],idempotencyKey:key()})
 if(publish||memberFeedback)detail=await coach('/training-plans/'+detail.plan.id+'/publish','POST',{expectedVersion:detail.plan.version,idempotencyKey:key()})
 if(memberFeedback)detail=await coach('/training-plans/'+detail.plan.id+'/sessions','POST',{expectedVersion:detail.plan.version,idempotencyKey:key(),performedOn:day(),durationMinutes:30,exercises:detail.exercises.map(e=>({exerciseId:e.id,status:'DONE',sets:3,reps:10,weightKg:0}))})
 return {coach,student:target,detail,read:()=>coach('/training-plans/'+detail.plan.id),title}
}
export async function prepareWriteFixture(c){
 await assertV2Target();assert.equal(c.kind,'write');const [user,home]=roleAccounts[c.role],api=await fixtureApi(user),session=await api('/agent/computer/session'),suffix=identifier(),slot=Number(c.id.slice(-2))-7
 const vars={fee:99,capacity:21,note:'隔离验收记录'+suffix,message:'隔离会话验收'+suffix,leadName:'潜客验收'+suffix,phone:'139'+String(Date.now()+sequence).slice(-8),planTitle:'训练草稿验收'+suffix,startDate:day(),endDate:day(7),performedOn:day(),trialStart:day(2)+' 10:00',trialEnd:day(2)+' 10:30',trialContent:'基础运动能力体验'}
 const message=(c.role==='SUPER_ADMIN'||c.role==='MANAGER')?slot===1:['RECEPTION','COACH','MEMBER'].includes(c.role)&&slot===0
 let path,method='POST',fields={},read,check
 if(message){
  const contacts=await api('/messages/contacts'),contact=contacts.staff.find(s=>s.name==='系统管理员')||contacts.staff[0];assert.ok(contact);vars.contactName=contact.name;fields={recipientUserId:contact.userId,content:vars.message};path='/api/v1/messages/send';read=async()=> (await api('/messages/thread/'+contact.userId)).messages.filter(m=>m.mine&&m.content===vars.message);check=(a,b)=>assert.equal(a.length,b.length+1)
 }else if(['SUPER_ADMIN','MANAGER'].includes(c.role)&&slot===0){
  const admin=await fixtureApi('admin'),course=await admin('/admin/courses','POST',{name:'课程验收'+suffix,type:'REGULAR',durationMinutes:45,maxCapacity:20,fee:88,intensity:'LOW',venueId:session.venueId});vars.courseName=course.name;path='/api/v1/admin/courses/'+course.id;method='PUT';const field=c.role==='SUPER_ADMIN'?'fee':'maxCapacity',value=c.role==='SUPER_ADMIN'?99:21;fields={[field]:value};read=async()=> (await api('/admin/courses')).find(x=>x.id===course.id);check=(a,b)=>{sameNumber(a[field],value);for(const k of ['id','name','type','durationMinutes','maxCapacity','fee','intensity','venueId'])if(k!==field)assert.equal(a[k],b[k],k)}
 }else if(c.role==='SALES'&&slot===0){
  fields={name:vars.leadName,phone:vars.phone,source:'WALK_IN',intent:'HIGH'};path='/api/v1/leads';read=async()=> (await api('/leads/workbench?stage=ALL&filter=ALL&page=1&size=20&keyword='+encodeURIComponent(vars.leadName))).leads.records;check=(a,b)=>{assert.equal(a.length,b.length+1);assert.equal(a[0].name,vars.leadName);assert.equal(a[0].phone,vars.phone);assert.equal(a[0].intent,'HIGH')}
 }else if((['SUPER_ADMIN','MANAGER'].includes(c.role)&&slot===2)){
  const rows=(await api('/admin/advisor/retention?filter=ALL')).members,target=rows[0];assert.ok(target);vars.memberName=target.realName;path='/api/v1/admin/members/'+target.memberId+'/follow-ups';fields={content:vars.note};read=()=>api('/admin/members/'+target.memberId+'/follow-ups');check=(a,b)=>{assert.equal(a.length,b.length+1);assert.ok(a.some(f=>f.content===vars.note&&f.memberId===target.memberId))}
 }else if(c.role==='COACH'&&slot===1){
  const student=(await api('/training-plans/students?page=1&size=20')).records[0];assert.ok(student);vars.memberName=student.realName;path='/api/v1/training-plans';fields={memberId:student.id,title:vars.planTitle,goal:'ENDURANCE',startDate:vars.startDate,endDate:vars.endDate,'exercises.0.name':'深蹲','exercises.0.sets':3,'exercises.0.reps':10,'exercises.0.weightKg':0};read=async()=>{const records=(await api('/training-plans/workbench?status=ALL&page=1&size=20&keyword='+encodeURIComponent(vars.planTitle))).plans.records;return Promise.all(records.map(p=>api('/training-plans/'+p.id)))};check=(a,b)=>{assert.equal(a.length,b.length+1);assert.equal(a[0].plan.memberId,student.id);assert.equal(a[0].plan.status,'DRAFT');assert.equal(a[0].plan.goal,'ENDURANCE');assert.equal(a[0].exercises.length,1);for(const [k,v]of Object.entries({name:'深蹲',sets:3,reps:10,weightKg:0}))assert.equal(a[0].exercises[0][k],v)}
 }else if(c.role==='COACH'&&(slot===2||slot===3)||c.role==='MEMBER'&&slot===3){
  const f=await planFixture(c.role==='MEMBER',slot===3);vars.planId=f.detail.plan.id;vars.planTitle=f.title;path='/api/v1/'+(c.role==='MEMBER'?'member/':'')+'training-plans/'+vars.planId;read=f.read
  if(c.role==='MEMBER'){vars.sessionId=f.detail.sessions.records[0].id;path+='/sessions/'+vars.sessionId+'/feedback';fields={rating:5,content:vars.note};check=a=>{const s=a.sessions.records.find(s=>s.id===vars.sessionId);assert.equal(s.feedbackRating,5);assert.equal(s.feedbackContent,vars.note)}}
  else if(slot===2){path+='/publish';fields={expectedVersion:f.detail.plan.version};check=a=>assert.equal(a.plan.status,'ACTIVE')}
  else{path+='/sessions';fields={performedOn:vars.performedOn,durationMinutes:30,'exercises.0.sets':3,'exercises.0.reps':10};check=(a,b)=>{assert.equal(a.sessions.total,b.sessions.total+1);const s=a.sessions.records.find(x=>!b.sessions.records.some(y=>y.id===x.id));assert.ok(s);assert.equal(s.performedOn,vars.performedOn);assert.equal(s.durationMinutes,30);assert.equal(s.exercises[0].sets,3);assert.equal(s.exercises[0].reps,10)}}
 }else if(c.role==='SALES'&&slot>=1||c.role==='SUPER_ADMIN'&&slot===3){
  const options=await api('/leads/options'),advisor=options.advisors.find(a=>a.id===session.userId)||options.advisors[0];assert.ok(advisor);vars.advisorName=advisor.name
  if(!(c.role==='SALES'&&slot===1))Object.assign(vars,await freeTrialTimes(advisor))
  let detail=await api('/leads','POST',{name:vars.leadName,phone:vars.phone,source:'WALK_IN',intent:'HIGH',ownerId:advisor.id,venueId:advisor.venueId,idempotencyKey:key()});const id=detail.lead.id;read=()=>api('/leads/'+id)
  if(c.role==='SALES'&&slot===1){path='/api/v1/leads/'+id+'/follow-ups';fields={method:'CALL',content:vars.note,outcome:'INTERESTED'};check=(a,b)=>{assert.equal(a.events.length,b.events.length+1);assert.ok(a.events.some(x=>x.type==='FOLLOW_UP'&&x.content.endsWith(vars.note)&&x.outcome==='INTERESTED'))}}
  else if(c.role==='SALES'&&slot===3){detail=await api('/leads/'+id+'/trials','POST',{expectedVersion:detail.lead.version,hostId:advisor.id,startsAt:vars.trialStart.replace(' ','T')+':00',endsAt:vars.trialEnd.replace(' ','T')+':00',content:vars.trialContent,idempotencyKey:key()});const trial=detail.trials.find(t=>t.status==='BOOKED');assert.ok(trial);vars.trialId=trial.id;const now=new Date();await shiftFixture('lead_trial',trial.id,timestamp(new Date(now-3600000)),timestamp(new Date(now-1800000)));path='/api/v1/leads/'+id+'/trials/'+trial.id+'/result';fields={status:'NO_SHOW',note:vars.note};check=a=>{const saved=a.trials.find(t=>t.id===trial.id);assert.equal(saved.status,'NO_SHOW');assert.equal(saved.resultNote,vars.note)}}
  else{path='/api/v1/leads/'+id+'/trials';fields={hostId:advisor.id,startsAt:vars.trialStart.replace(' ','T')+':00',endsAt:vars.trialEnd.replace(' ','T')+':00',content:vars.trialContent};check=(a,b)=>{assert.equal(a.trials.length,b.trials.length+1);const t=a.trials.find(t=>!b.trials.some(x=>x.id===t.id));assert.ok(t);assert.equal(t.hostId,advisor.id);assert.equal(t.content,vars.trialContent)}}
 }else{
  assert.ok(['RECEPTION','MANAGER','MEMBER'].includes(c.role),'No registered write fixture '+c.id)
  const own=await(await fixtureApi('member'))('/member/profile'),f=await freeSchedule('预约验收'+suffix,own.venueId);vars.memberName=own.realName;vars.courseName=f.course.name;vars.scheduleTime=day(2)+' 14:00';read=async()=> (await api(c.role==='MEMBER'?'/member/bookings':'/admin/bookings')).filter(b=>(b.scheduleId??b.schedule?.id)===f.schedule.id)
  const create=c.role==='RECEPTION'&&slot===1||c.role==='MEMBER'&&slot===1
  if(create){path='/api/v1/'+(c.role==='MEMBER'?'member':'admin')+'/bookings';fields={scheduleId:f.schedule.id,...(c.role==='MEMBER'?{}:{memberId:own.id})};check=(a,b)=>{assert.equal(a.length,b.length+1);assert.equal(a[0].status,'BOOKED')}}
  else{
   const booking=await f.admin('/admin/bookings','POST',{memberId:own.id,scheduleId:f.schedule.id});vars.bookingId=booking.id;path='/api/v1/'+(c.role==='MEMBER'?'member':'admin')+'/bookings/'+booking.id
   if(c.role==='RECEPTION'&&slot===2){const now=new Date();await shiftFixture('course_schedule',f.schedule.id,timestamp(new Date(now-300000)),timestamp(new Date(now.getTime()+2400000)));path+='/check-in';check=a=>assert.equal(a.find(b=>b.id===booking.id).status,'ATTENDED')}
   else{path+='/cancel';method='PUT';check=a=>assert.equal(a.find(b=>b.id===booking.id).status,'CANCELLED')}
  }
 }
 return {user,home,goal:replace(c.goal,vars),path,method,fields,read,check,variables:vars}
}
