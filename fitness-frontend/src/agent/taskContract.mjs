const all=['SUPER_ADMIN','MANAGER','RECEPTION','COACH','SALES','MEMBER']
const catalog=[
  ['navigation','当前页导航',all],['messages.send','站内消息',all],
  ['course.read','课程与排课查询',['SUPER_ADMIN','MANAGER','RECEPTION','COACH','MEMBER']],
  ['course.update','课程编辑',['SUPER_ADMIN','MANAGER']],['schedule.edit','排课',['SUPER_ADMIN','MANAGER']],
  ['booking.manage','预约与接待',['SUPER_ADMIN','MANAGER','RECEPTION','MEMBER']],
  ['member.manage','会员管理',['SUPER_ADMIN','MANAGER','RECEPTION']],
  ['lead.trial','潜客与体验',['SUPER_ADMIN','MANAGER','SALES']],
  ['retention.followup','续费回访',['SUPER_ADMIN','MANAGER','SALES']],
  ['training.manage','训练计划',['COACH']],['training.supervise','训练监督',['SUPER_ADMIN','MANAGER']],
  ['training.self','我的训练',['MEMBER']],['cash.read','交班辅助',['SUPER_ADMIN','MANAGER','RECEPTION']],
  ['analytics.read','经营分析',['SUPER_ADMIN','MANAGER']]
]
export const roleSkills=role=>catalog.filter(c=>c[2].includes(role)).map(([id,label])=>({id,label}))
const destinations=[[/训练计划|我的训练/,'/training-plans','我的训练'],[/站内消息|发起会话/,'/chat','消息'],[/周课表|本周课表/,'/schedule-week','课程'],[/课程与排课|课程库/,'/courses','课程'],[/会员列表|会员管理/,'/members',''],[/潜客|体验课/,'/leads',''],[/续费|回访/,'/retention',''],[/交班|日结/,'/cash-shifts',''],[/经营分析/,'/analytics',''],[/经营看板|经营概况|总部总览/,'/dashboard','']]
const norm=s=>String(s??'').replace(/\s+/g,'').replace(/[「」“”"']/g,'')
const mutation=/新建|新增|创建|修改|编辑|改为|改成|改到|设为|设成|调整为|调整到|更新|删除|提交|保存|发送|发布|退款|开卡|帮我预约|取消预约|办理签到/
export function createGoalContract(goal,role){
  const requirements=[],prohibited=[]
  const add=r=>{if(!requirements.some(x=>JSON.stringify({...x,id:undefined})===JSON.stringify(r)))requirements.push({id:`g${requirements.length+1}`,...r})}
  const clauses=String(goal).split(/[，,。；;]|然后|再(?=告诉|查看|查询|发送|修改|安排|创建|打开)/).map(s=>s.trim()).filter(Boolean)
  for(const clause of clauses){
    const negative=/不要|不必|无需|不(?:提交|保存|新建|新增|修改|发送|发布|删除|扣课|签到)|保持不变/.test(clause)
    if(negative){if(/发送/.test(clause))prohibited.push('messages.send');if(/保存|提交/.test(clause))prohibited.push('SUBMIT');if(/容量/.test(clause))prohibited.push('course.maxCapacity');continue}
    const target=clause.replace(/^.*?(?:把|将)/,'').replace(/(?:的)?(?:价格|费用|售价|容量).*$/,'').replace(/^(?:只|帮我|请)+/,'').trim()
    const fee=clause.match(/(?:价格|费用|售价).*?(?:改为|改成|改到|设为|设成|调整为|调整到|调整成|修改为)\s*[¥￥]?\s*(\d+(?:\.\d{1,2})?)/)
    const capacity=clause.match(/容量.*?(?:改为|改成|改到|设为|设成|调整为|调整到|调整成)\s*(\d+)/)
    if(fee)add({kind:'MUTATE',entityType:'course',field:'fee',expected:fee[1],targetLabel:target})
    if(capacity)add({kind:'MUTATE',entityType:'course',field:'maxCapacity',expected:capacity[1],targetLabel:target})
    if(mutation.test(clause)&&!fee&&!capacity&&(!/^(?:打开|查看|浏览)/.test(clause)||/发送|修改|创建|新增|提交|保存|发布|删除|帮我预约|取消预约|办理签到/.test(clause))){
      const entityType=/消息|发送|发给/.test(clause)?'message':/体验/.test(clause)?'trial':/潜客/.test(clause)?'lead':/训练|计划|动作|反馈/.test(clause)?'training':/预约|签到/.test(clause)?'booking':/回访|续费/.test(clause)?'retention':/会员/.test(clause)?'member':/排课/.test(clause)?'schedule':'unknown'
      add({kind:'MUTATE',entityType,label:clause})
    }
    if(/打开|进入|切换到|点击.*(?:页|表)/.test(clause)&&!fee&&!capacity){
      const destination=destinations.find(([re])=>re.test(clause))
      if(destination)add({kind:'NAVIGATE',path:role==='MEMBER'?'/member-home':destination[1],...(role==='MEMBER'&&destination[2]?{tab:destination[2]}:{})})
    }
    if(/查询|统计|告诉我|有哪些|多少|几节|几次|查看(?!.*(?:页面|页签))/.test(clause)&&!mutation.test(clause)){
      add({kind:'QUERY',topic:/排课|课程|几节/.test(clause)?'courses':/预约/.test(clause)?'bookings':/训练/.test(clause)?'training_plans':/学员/.test(clause)?'students':/潜客|体验/.test(clause)?'leads':/回访|续费/.test(clause)?'retention':/交班|日结/.test(clause)?'cash_summary':/经营|收入|营业/.test(clause)?'analytics':'page',label:clause})
    }
  }
  if(!requirements.some(r=>r.kind==='MUTATE')&&prohibited.some(p=>p==='messages.send'))prohibited.push('SUBMIT')
  if(!requirements.length)add({kind:'INSPECT',label:String(goal)})
  return {goal:String(goal),role,requirements,prohibited:[...new Set(prohibited)]}
}
export function verifyGoalContract(contract,evidence=[]){
  const outcomes=contract.requirements.map(r=>{
    const hit=evidence.find(e=>{
      if(e.verified!==true||e.kind!==r.kind)return false
      if(r.kind==='NAVIGATE')return e.path===r.path&&(!r.tab||e.selectedTabs?.some(t=>norm(t).includes(norm(r.tab))))
      if(r.kind==='QUERY')return e.topic===r.topic&&e.asOf&&e.truncated!==true
      if(r.kind==='MUTATE')return !!e.receiptId&&r.entityType===e.entityType&&(!r.entityId||String(e.entityId)===String(r.entityId))&&(!r.field||(r.field===e.field&&Number(r.expected)===Number(e.expected)))&&(!r.targetLabel||norm(e.targetLabel).includes(norm(r.targetLabel)))
      return e.explicitInspection===true
    })
    return {id:r.id,status:hit?'SATISFIED':'UNSATISFIED',label:r.label||[r.path,r.entityType,r.field,r.expected].filter(Boolean).join(' '),...(hit?{evidence:hit}:{})}
  })
  const satisfied=outcomes.filter(x=>x.status==='SATISFIED').length
  return {complete:outcomes.length>0&&satisfied===outcomes.length,partial:satisfied>0&&satisfied<outcomes.length,outcomes}
}
export function classifyFailure(error={}){
  const code=String(error.failureCode||error.code||'')
  if(code==='WRITE_UNKNOWN')return {strategy:'VERIFY_ONLY',retryable:false,label:'提交结果待核对，禁止自动重发'}
  if(['1002','1003','2602','FORBIDDEN','PASSWORD_CHANGE_REQUIRED'].includes(code))return {strategy:'EXPLAIN',retryable:false,label:'当前身份或权限不允许此操作'}
  if(['STALE_PAGE','ACTION_INTERRUPTED','MODEL_PROTOCOL','1001'].includes(code))return {strategy:'REOBSERVE',retryable:true,label:'重新观察当前对象和字段'}
  if(['RATE_LIMITED','CIRCUIT_OPEN','MODEL_TIMEOUT','MODEL_BUSY','PROVIDER_UNAVAILABLE'].includes(code))return {strategy:'WAIT',retryable:true,label:'等待模型服务恢复后重新规划'}
  return {strategy:'EXPLAIN',retryable:false,label:error.message||'当前步骤未完成，请核对实际结果'}
}
