const compact=s=>String(s??'').replace(/\s+/g,' ').trim()
const privateText=s=>String(s??'').replace(/(?<!\d)\d{17}[\dXx](?!\d)|(?<!\d)1[3-9]\d{9}(?!\d)|[\w.+-]+@[\w.-]+\.[A-Za-z]{2,}/g,'[资料已隐藏]').replace(/Bearer\s+\S+|sk-[A-Za-z0-9]{15,}/gi,'[凭据已隐藏]')
export function buildPageContext({document,window,root,nodes,resolve,title,candidateCount=nodes.length}){
  const visible=e=>{if(!e||e.closest('[data-page-agent-exclude],[aria-hidden="true"],[hidden]'))return false;const r=e.getBoundingClientRect(),s=window.getComputedStyle(e);return r.width>0&&r.height>0&&s.display!=='none'&&s.visibility!=='hidden'}
  const area=root||document.querySelector('main')||document.body,entities=new Map(),fields=[]
  const scopeHost=root?.querySelector('form[data-agent-type][data-agent-id],[data-agent-scope][data-agent-type][data-agent-id]')||root?.closest('[data-agent-type][data-agent-id]')
  for(const node of nodes){
    const e=resolve(node.ref);if(!e)continue
    const host=e.closest('[data-agent-type][data-agent-id]'),field=e.closest('[data-agent-field]')
    if(host){
      const type=host.getAttribute('data-agent-type'),id=host.getAttribute('data-agent-id'),venueId=host.getAttribute('data-agent-venue')||'',key=JSON.stringify([type,id,venueId])
      if(!entities.has(key))entities.set(key,{type,id,venueId,label:privateText(compact(host.getAttribute('data-agent-label')||host.querySelector('h1,h2,h3,h4,strong')?.textContent)),actions:[]})
      const actionOwner=e.closest('[data-agent-action]'),action=actionOwner&&host.contains(actionOwner)?actionOwner.getAttribute('data-agent-action'):null
      if(action)entities.get(key).actions.push({action,ref:node.ref,label:node.name})
    }
    const select=e.matches('[role="combobox"]')&&e.closest('.el-select')
    const slider=e.matches('[role="slider"]')
    if(select||slider||['INPUT','TEXTAREA','SELECT'].includes(e.tagName))fields.push({ref:node.ref,key:field?.getAttribute('data-agent-field')||'',name:node.name,value:privateText(slider?e.getAttribute('aria-valuenow'):select?compact(select.querySelector('.el-select__selected-item:not(.is-transparent):not(.is-hidden):not(.el-select__input-wrapper)')?.textContent):e.value),controlType:slider?'slider':select?'combobox':e.type||e.tagName.toLowerCase(),checked:e.checked===true,writable:slider?e.getAttribute('aria-disabled')!=='true'&&e.getAttribute('aria-readonly')!=='true':select?!select.classList.contains('is-disabled')&&!e.disabled:!e.disabled&&!e.readOnly,entityType:host?.getAttribute('data-agent-type')||scopeHost?.getAttribute('data-agent-type')||'',entityId:host?.getAttribute('data-agent-id')||scopeHost?.getAttribute('data-agent-id')||''})
    if(select&&fields.at(-1)?.ref===node.ref){
      const f=fields.at(-1)
      if(field?.getAttribute('data-agent-option-type'))f.optionType=field.getAttribute('data-agent-option-type')
      const selected=field?.getAttribute('data-agent-value-id');if(selected&&/^\d+$/.test(selected))f.selectedId=selected
      if(e.getAttribute('aria-readonly')==='true')f.writable=false
    }
  }
  const errors=[...area.querySelectorAll('.el-form-item__error,[role="alert"],.el-alert--error')].filter(visible).map(e=>privateText(compact(e.textContent))).filter(Boolean).slice(0,8)
  const selectedTabs=[...document.querySelectorAll('[role="tab"][aria-selected="true"],.el-tabs__item.is-active')].filter(visible).map(e=>privateText(compact(e.textContent))).slice(0,10)
  const loading=[...area.querySelectorAll('.el-loading-mask,.el-button.is-loading,[aria-busy="true"]')].some(visible)
  const result={path:window.location.pathname,scope:{type:root?'dialog':'page',nativeConfirmation:!!(root?.matches('.el-message-box')||root?.querySelector('.el-message-box')),title:privateText(title),...(scopeHost?{entityType:scopeHost.getAttribute('data-agent-type'),entityId:scopeHost.getAttribute('data-agent-id'),venueId:scopeHost.getAttribute('data-agent-venue')||''}:{})},entities:[...entities.values()].slice(0,60),fields,errors,selectedTabs,loading,truncated:candidateCount>nodes.length||entities.size>60}
  return result
}
export function summarizePageContext(context={}){
  return privateText(JSON.stringify({scope:context.scope,fields:context.fields?.map(({name,key,value})=>({name,key,value})),errors:context.errors,range:context.truncated?'已截断，须缩小范围':'当前范围'})).slice(0,2200)
}
export function chooseSemanticAction(context={},args={}){
  if(args.kind==='entity_action'){
    const matches=(context.entities||[]).filter(e=>e.type===args.entityType&&(!args.id||String(e.id)===String(args.id))&&(!args.venueId||String(e.venueId)===String(args.venueId))&&(!args.label||e.label===args.label))
    if(matches.length!==1)return null
    const actions=matches[0].actions.filter(a=>a.action===args.action)
    return actions.length===1?{action:'click',arguments:{ref:actions[0].ref}}:null
  }
  if(args.kind==='fill_field'){
    const aliases={'价格':'fee','容量':'maxCapacity','课程名称':'name','备注':'note','训练目标':'goal','跟进结果':'outcome'}
    const productPrice=args.field==='价格'&&!(context.fields||[]).some(f=>f.key==='fee')&&(context.fields||[]).some(f=>f.key==='price'&&f.entityType==='product')
    const roomCapacity=['容量','可容纳'].includes(args.field)&&!(context.fields||[]).some(f=>f.key==='maxCapacity')&&(context.fields||[]).some(f=>f.key==='capacity'&&f.entityType==='room')
    const key=productPrice?'price':roomCapacity?'capacity':aliases[args.field]||args.field
    const requestedValue=key==='goal'?({'综合体能':'体能','体能训练':'体能','减脂塑形':'减脂','增肌训练':'增肌','综合健身':'综合训练'}[String(args.value)]||String(args.value)):key==='outcome'?({'愿意体验':'有意向','愿意进一步了解':'有意向'}[String(args.value)]||String(args.value)):String(args.value)
    let fields=(context.fields||[]).filter(f=>(f.key===key||f.name===args.field)&&f.writable&&(!args.id||String(f.entityId)===String(args.id)))
    if(fields.some(f=>f.controlType==='radio')){
      fields=fields.filter(f=>f.controlType==='radio'&&(f.value===String(args.value)||f.name===String(args.value)))
      if(fields.length!==1)return null
      return fields[0].checked?{action:'observe',arguments:{},alreadySatisfied:true}:{action:'click',arguments:{ref:fields[0].ref}}
    }
    if(fields.length!==1)return null
    if(fields[0].entityType==='product'&&fields[0].key==='price'&&[fields[0].value,args.value].every(v=>/^\d+(?:\.\d+)?$/.test(String(v)))&&Number.isFinite(Number(args.value))&&Number(fields[0].value)===Number(args.value))return {action:'observe',arguments:{},alreadySatisfied:true}
    if(fields[0].optionType&&fields[0].selectedId===String(args.value))return {action:'observe',arguments:{},alreadySatisfied:true}
    if(String(fields[0].value)===String(args.value)||String(fields[0].value)===requestedValue)return {action:'observe',arguments:{},alreadySatisfied:true}
    if(fields[0].controlType==='combobox')return {action:'select_option',arguments:{ref:fields[0].ref,value:requestedValue,...(fields[0].optionType?{optionType:fields[0].optionType}:{})}}
    return {action:'fill',arguments:{ref:fields[0].ref,value:String(args.value)}}
  }
  return null
}
