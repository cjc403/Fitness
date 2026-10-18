export const redactPage = (value, max=6000) => String(value??'').replace(/Bearer\s+[^\s"']+/gi,'Bearer [已隐藏]').replace(/eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/g,'[令牌已隐藏]').replace(/((?:password|refresh_token|access_token|api[_-]?key|密码|口令)\s*[:=：]\s*)[^\s,;，；]+/gi,'$1[已隐藏]').slice(0,max)
const exclude='[data-page-agent-exclude],.assistant,.header,.mobile-navigation,.mobile-more-backdrop,[hidden],[aria-hidden="true"]'
const controls='button,a[href],input,textarea,select,[role="button"],[role="menuitem"],[role="tab"],[role="checkbox"],[role="radio"],[role="combobox"],[role="option"],[role="slider"],.el-date-table td.available:not(.disabled):not(.prev-month):not(.next-month),.el-month-table td:not(.disabled),.el-year-table td:not(.disabled)'
const compact=value=>String(value??'').replace(/\s+/g,' ').trim()

export function createPageDriver({document,window,router,allowed,contextAdapter}) {
  let refs=new Map(),sequence=0,observedUrl='',observedScope,highlighted=null,lastGoal='',lastContext
  const trail=[]
  const stale=()=>Object.assign(Error('页面或操作对象已变化，重新观察后再操作'),{code:'STALE_PAGE'})
  const visible=e=>{const s=window.getComputedStyle(e),r=e.getBoundingClientRect();return e.isConnected&&!e.closest(exclude)&&s.visibility!=='hidden'&&s.display!=='none'&&r.width>0&&r.height>0}
  const type=e=>(e.getAttribute('type')||'').toLowerCase()
  const label=e=>compact(e.closest('.el-form-item')?.querySelector('.el-form-item__label')?.innerText||e.labels?.[0]?.innerText)
  const calendarCell=e=>e.tagName==='TD'&&!!e.closest('.el-date-table,.el-month-table,.el-year-table')
  const calendarName=e=>{
    if(!calendarCell(e))return ''
    const panel=e.closest('.el-date-range-picker__content,.el-date-picker,.el-picker-panel'),header=compact(panel?.querySelector('.el-date-picker__header,.el-date-range-picker__header')?.innerText),year=header.match(/\d{4}/)?.[0],day=Number(compact(e.innerText))
    if(e.closest('.el-year-table'))return '选择年份：'+compact(e.innerText)
    if(!year)return '选择日期：'+header+' '+compact(e.innerText)
    if(e.closest('.el-month-table'))return '选择月份：'+year+' '+compact(e.innerText)
    const chinese=header.match(/(?:^|\D)(1[0-2]|0?[1-9])\s*月/),english=['january','february','march','april','may','june','july','august','september','october','november','december'].findIndex(m=>header.toLowerCase().includes(m)),month=chinese?Number(chinese[1]):english+1
    return month&&day?'选择日期：'+year+'-'+String(month).padStart(2,'0')+'-'+String(day).padStart(2,'0'):'选择日期：'+header+' '+compact(e.innerText)
  }
  const rawName=e=>compact(calendarName(e)||e.getAttribute('aria-label')||(e.getAttribute('aria-labelledby')||'').split(/\s+/).map(id=>document.getElementById(id)?.innerText||'').join(' ')||e.labels?.[0]?.innerText||label(e)||e.innerText||e.getAttribute('placeholder')||e.getAttribute('title')||(['radio','checkbox'].includes(type(e))?e.closest('tr,[role="row"]')?.innerText:''))
  const safe=e=>visible(e)&&!['password','hidden','file'].includes(type(e))&&!/password|token|secret|api.?key/i.test([e.id,e.getAttribute('name'),e.getAttribute('autocomplete')].join(' '))&&!/退出登录|切换账号|修改密码/.test(rawName(e))
  const entity=e=>{
    if(calendarCell(e)||e.matches('[role="menuitem"],[role="tab"],[role="option"]'))return null
    const root=e.closest('[data-agent-entity],article,tr,[role="row"],li')
    if(!root||root.closest('[role="menu"],nav')||root.matches('[role="menuitem"],[role="option"]'))return null
    const title=compact(root.getAttribute('data-agent-entity')||root.querySelector('h1,h2,h3,h4,h5,[data-agent-title]')?.innerText||root.querySelector('td,[role="cell"]')?.innerText||root.querySelector('strong')?.innerText)
    return title?{root,title,text:compact(root.innerText).slice(0,240)}:null
  }
  const name=e=>{
    const base=rawName(e),owner=entity(e),field=label(e)
    const range=e.closest('.el-range-editor'),ends=range?[...range.querySelectorAll('input')]:[]
    if(field&&ends.length===2&&ends.includes(e)){const part=e.getAttribute('placeholder')||(ends.indexOf(e)===0?'开始':'结束')+(range.className.includes('timerange')?'时间':'日期');return `${field}：${part}`}
    if(field&&['radio','checkbox'].includes(type(e))&&field!==base)return `${field}：${base}`
    if(field===base&&e.getAttribute('placeholder')&&e.closest('.el-form-item')?.querySelectorAll('input:not([type="radio"]):not([type="checkbox"]):not([type="hidden"])').length>1)return `${field}：${e.getAttribute('placeholder')}`
    if(owner&&!base.includes(owner.title))return compact(`${base}｜${owner.title}（${owner.text.replace(owner.title,'').slice(0,65)}）`)
    if(field&&e.matches('button,[role="button"]')&&!base.includes(field))return compact(`${base}｜${field}`)
    return base
  }
  const zIndex=e=>{let z=0;for(let p=e;p&&p!==document.body;p=p.parentElement){const n=Number(window.getComputedStyle(p).zIndex);if(Number.isFinite(n))z=Math.max(z,n)}return z}
  const scope=()=>{
    const dialogs=[...document.querySelectorAll('[role="dialog"],dialog[open]')].filter(e=>visible(e)&&e.getAttribute('aria-modal')!=='false'&&!e.matches('.el-popper,[data-popper-placement]'))
    return dialogs.sort((a,b)=>zIndex(a)-zIndex(b)).at(-1)||null
  }
  const popups=root=>{
    const result=[]
    for(const e of (root||document).querySelectorAll('[aria-expanded="true"][aria-controls],[aria-expanded="true"][aria-owns]')){
      if(!safe(e))continue
      for(const id of compact(e.getAttribute('aria-controls')||e.getAttribute('aria-owns')).split(' ')){
        const popup=document.getElementById(id)
        if(popup&&visible(popup)&&!result.includes(popup))result.push(popup)
      }
    }
    return result
  }
  const inScope=(e,root,related)=>!root||root.contains(e)||related.some(p=>p.contains(e))
  const role=e=>e.getAttribute('role')||(calendarCell(e)?'button':e.tagName==='A'?'link':e.tagName==='SELECT'?'combobox':e.tagName==='BUTTON'?'button':['checkbox','radio'].includes(type(e))?type(e):type(e)==='number'?'spinbutton':'textbox')
  const state=e=>{
    const flags=[]
    if(e.disabled||e.getAttribute('aria-disabled')==='true')flags.push('禁用')
    if(e.getAttribute('aria-readonly')==='true'||e.readOnly&&!(role(e)==='combobox'&&e.closest('.el-select')))flags.push('只读')
    if(['checkbox','radio'].includes(type(e))||['checkbox','radio'].includes(role(e)))flags.push(e.checked||e.getAttribute('aria-checked')==='true'?'已选中':'未选中')
    if(e.getAttribute('aria-selected')!==null)flags.push(e.getAttribute('aria-selected')==='true'?'已选中':'未选中')
    let value
    if(['INPUT','TEXTAREA','SELECT'].includes(e.tagName))value=e.value
    if(role(e)==='combobox')value=e.closest('.el-select__wrapper')?.innerText||e.value||e.getAttribute('aria-valuetext')||'未选择'
    if(role(e)==='slider')value=e.getAttribute('aria-valuenow')
    const selectedId=role(e)==='combobox'?e.closest('[data-agent-value-id]')?.getAttribute('data-agent-value-id'):undefined
    return {value:value===undefined?undefined:compact(value),flags,...(selectedId?{selectedId}:{})}
  }
  const title=root=>root?rawName(root)||compact(root.querySelector('h1,h2,h3,.el-dialog__title')?.innerText)||'业务弹窗':compact(document.querySelector('main h1')?.innerText)||document.title
  const score=(e,root,related)=>{
    const owner=entity(e),base=rawName(e),r=e.getBoundingClientRect()
    const typed=e.closest('[data-agent-type][data-agent-id]'),typedLabel=compact(typed?.getAttribute('data-agent-label'))
    const typedMatch=typedLabel.length>1&&lastGoal.includes(typedLabel)?850:0
    const matches=owner?owner.text.split(/[\s，,；;：:（）()｜|]+/).filter(word=>word.length>1&&lastGoal.includes(word)).length:0
    const date=calendarCell(e)?base.match(/\d{4}-\d{2}-\d{2}/)?.[0]:null
    return typedMatch+(related.some(p=>p.contains(e))?1000:0)+(date&&lastGoal.includes(date)?900:0)+(owner&&owner.title.length>1&&lastGoal.includes(owner.title)?800:0)+matches*20+(root?.contains(e)?200:0)+(base.length>1&&lastGoal.includes(base)?80:0)+(r.bottom>0&&r.top<window.innerHeight?10:0)
  }
  const entries=()=>{
    const root=scope(),related=popups(root)
    const list=[...document.querySelectorAll(controls)].filter(e=>safe(e)&&rawName(e)&&inScope(e,root,related))
    // When a modal is open, unrelated teleported lists are not part of its interaction scope.
    const filtered=root?list.filter(e=>!e.closest('[role="listbox"]')||root.contains(e)||related.some(p=>p.contains(e))):list
    return {root,related,list:filtered.sort((a,b)=>score(b,root,related)-score(a,root,related))}
  }
  const checkUrl=url=>{const u=new URL(url,window.location.href);if(u.origin!==window.location.origin||!['http:','https:'].includes(u.protocol)||u.username||u.password||!allowed(u.pathname))throw Error('当前角色不能访问该页面或外部链接');return u}
  const clear=()=>{if(highlighted){highlighted.removeAttribute('data-page-agent-target');highlighted=null}}
  const mark=e=>{clear();highlighted=e;e.setAttribute('data-page-agent-target','true');e.scrollIntoView({block:'center',inline:'nearest',behavior:'instant'})}
  const fields=list=>list.map(e=>({e,name:name(e),...state(e)})).filter(s=>s.value!==undefined||s.flags.length)
  const line=s=>`${redactPage(s.name,90)}：${redactPage(s.value??'',100)}${s.selectedId?'（已选编号 #'+s.selectedId+'）':''}${s.flags.length?' ['+s.flags.join('、')+']':''}`
  const textOf=root=>{
    const clone=root.cloneNode(true)
    clone.querySelectorAll(exclude+',script,style,input,textarea,select').forEach(n=>n.remove())
    return compact(clone.innerText||clone.textContent||'')
  }
  const observe=(context={})=>{
    if(typeof context.goal==='string')lastGoal=context.goal
    checkUrl(window.location.href);clear();refs=new Map();observedUrl=window.location.href;sequence++
    const {root,related,list}=entries();observedScope=root
    if(context.optionHint)list.sort((a,b)=>Number(matchesOptionHint(b,context.optionHint))-Number(matchesOptionHint(a,context.optionHint)))
    // Long teleported calendars/options must leave room for parent fields and recovery.
    const protectedNodes=related.length?list.filter(e=>!related.some(p=>p.contains(e))&&(['INPUT','TEXTAREA','SELECT'].includes(e.tagName)||e.matches('button,[role="button"]')&&(root||/取消|关闭|保存|确定|确认/.test(rawName(e))))).slice(0,16):root?list.filter(e=>e.closest('[data-agent-field]')&&e.matches('input,textarea,select,[role="combobox"],[role="slider"]')||e.matches('button,[role="button"]')&&/^(?:取消|关闭|保存|确定|确认)/.test(rawName(e))).slice(0,32):[]
    const reserved=new Set(protectedNodes)
    const chosen=new Set([...protectedNodes,...list.filter(e=>!reserved.has(e)).slice(0,60-protectedNodes.length)])
    const selected=list.filter(e=>chosen.has(e)),nodes=[]
    for(const e of selected){const ref=`r${sequence}_${nodes.length}`,label=name(e),kind=type(e);refs.set(ref,{e,name:label,type:kind,scope:root,state:JSON.stringify(state(e))});nodes.push({ref,role:role(e),name:redactPage(label,120),...(kind?{type:kind}:{})})}
    const states=fields(selected).map(line).join('\n').slice(0,2600)
    const entities=[...new Map(list.map(e=>entity(e)).filter(Boolean).map(o=>[o.root,o])).values()]
    const index=entities.map(o=>redactPage(o.text,160)).join('\n').slice(0,1900)
    const roots=root?[root,...related]:[...document.querySelectorAll('main')].filter(visible)
    const body=(roots.length?roots:[document.body]).map(textOf).join('\n')
    const text=context.v2?`当前作用域：${root?'弹窗':'页面'} ${title(root)}\n业务正文：${body.slice(0,1800)}\n可操作控件：${selected.length}/${list.length}；值与对象见context，仅用本轮引用。`:`当前作用域：${root?'弹窗':'页面'} ${title(root)}\n当前控件状态（实际值）：\n${states}\n${root?'弹窗正文':'目标附近对象及行'}：\n${root?body.slice(0,2400):index}\n${root?'':'页面正文：\n'+body.slice(0,Math.max(0,5600-states.length-index.length))}\n可操作控件：${selected.length}/${list.length}；仅使用本轮引用，不以同名按钮顺序猜测对象。`
    lastContext=contextAdapter?.build({document,window,root,nodes,candidateCount:list.length,resolve:ref=>refs.get(ref)?.e,title:title(root)})
    return {url:window.location.href,title:redactPage(document.title,200),text:redactPage(text),revision:`page_${sequence}`,nodes,...(context.v2?{context:lastContext}:{})}
  }
  const feedback=()=>{
    const {root,list}=entries(),all=fields(list)
    const values=all.sort((a,b)=>(lastGoal.includes(b.name)?1:0)-(lastGoal.includes(a.name)?1:0)).map(s=>line({...s,value:s.value?.slice(0,45)})).join('；')
    const owners=[...new Map(list.map(e=>entity(e)).filter(Boolean).map(o=>[o.root,o])).values()]
    const nearby=owners.filter(o=>lastGoal.includes(o.title)).map(o=>redactPage(o.text,180)).join('；')
    return redactPage(`当前${root?'弹窗':'页面'}：${title(root)}；${values}${nearby?'；目标对象：'+nearby:''}`,480)
  }
  const fingerprint=()=>{const {root,list}=entries();return JSON.stringify([window.location.href,title(root),list.map(e=>[name(e),state(e),entity(e)?.text])])}
  const describe=(action,args)=>`${action} ${refs.get(args.ref)?.name||args.path||args.direction||args.artifactId||''}${args.value===undefined?'':' = '+redactPage(args.value,100)}`
  const navigate=async path=>{checkUrl(path);const before=window.location.pathname;await router.push(path);if(window.location.pathname===before&&path!==before)throw Error('当前页没有完成所需切换');if(before!==window.location.pathname)trail.push(before);return `当前标签页已切换到 ${window.location.pathname}`}
  const matchesOptionHint=(e,hint)=>{const host=e.closest('[data-agent-type][data-agent-id]');return role(e)==='option'&&host?.getAttribute('data-agent-type')===hint.type&&host.getAttribute('data-agent-id')===hint.id}
  const act=async(action,args,canAct=()=>true)=>{
    const interrupted=()=>Object.assign(Error('任务状态已变化，已阻止继续填写；恢复后将重新观察'),{code:'ACTION_INTERRUPTED'})
    if(!canAct())throw interrupted()
    checkUrl(window.location.href)
    if(observedUrl!==window.location.href||observedScope!==scope())throw stale()
    if(action==='use_skill'){
      if(args.kind==='fill_fields'){
        if(!Array.isArray(args.fields)||!args.fields.length||args.fields.length>8||new Set(args.fields.map(f=>f.field)).size!==args.fields.length)throw Error('表单技能须包含1至8个不同字段')
        const originalScope=scope(),originalUrl=window.location.href,results=[]
        for(const field of args.fields){
          if(!canAct())throw interrupted()
          if(scope()!==originalScope||window.location.href!==originalUrl)throw stale()
          observe({v2:true})
          results.push(await act('use_skill',{kind:'fill_field',field:field.field,value:field.value,...(args.id?{id:args.id}:{})},canAct))
          await Promise.resolve();await Promise.resolve()
        }
        return results.join('；')
      }
      const semantic=contextAdapter?.choose(lastContext,args)
      if(!semantic)throw Object.assign(Error('业务对象或字段不唯一，请重新观察并指定编号和门店'),{code:'STALE_PAGE'})
      if(semantic.action==='select_option'){
        const originalScope=scope(),originalUrl=window.location.href
        await act('click',{ref:semantic.arguments.ref},canAct)
        const deadline=Date.now()+5000
        let choices=[]
        while(Date.now()<deadline){
          await new Promise(resolve=>setTimeout(resolve,80))
          if(!canAct())throw interrupted()
          if(scope()!==originalScope||window.location.href!==originalUrl)throw stale()
          const {value,optionType}=semantic.arguments,byId=!!optionType&&/^\d+$/.test(value)
          const current=observe({v2:true,...(byId?{optionHint:{type:optionType,id:value}}:{})});choices=current.nodes.filter(n=>n.role==='option')
          const typed=(current.context?.entities||[]).filter(e=>e.type===optionType&&(byId?e.id===value:!current.context.truncated&&e.label===value))
          const typedRefs=new Set(typed.flatMap(e=>e.actions.filter(a=>a.action==='select').map(a=>a.ref)))
          const options=choices.filter(n=>typedRefs.has(n.ref)||!byId&&n.name===value)
          if(options.length===1)return act('click',{ref:options[0].ref},canAct)
          if(options.length>1)break
        }
        throw Object.assign(Error('未找到唯一可见选项：'+semantic.arguments.value+'；当前选项：'+choices.slice(0,8).map(n=>n.name).join('、')),{code:'STALE_PAGE'})
      }
      return act(semantic.action,semantic.arguments,canAct)
    }
    if(['navigate','back'].includes(action)&&scope())throw Object.assign(Error('当前有业务弹窗，请先核对或关闭它，再切换页面'),{code:'STALE_PAGE'})
    if(action==='navigate')return navigate(args.path)
    if(action==='back'){const path=trail.pop();if(!path)throw Error('没有本任务可返回的项目页面');checkUrl(path);await router.push(path);return `当前页已返回 ${path}`}
    if(action==='observe')return `已读取当前页 ${window.location.pathname}`
    if(action==='scroll'){
      if(!['up','down'].includes(args.direction))throw Error('滚动方向不正确')
      const root=scope(),main=root?.closest('.el-overlay-dialog')||root||document.querySelector('main')||window
      main.scrollBy({top:args.direction==='down'?600:-600,behavior:'instant'});return `已滚动当前${root?'弹窗':'页面'}${args.direction==='down'?'下方':'上方'}`
    }
    if(!['click','fill','select'].includes(action))throw Error('不支持的网页动作')
    const item=refs.get(args.ref),e=item?.e,root=scope()
    if(!e||!safe(e)||!inScope(e,root,popups(root))||name(e)!==item.name||type(e)!==item.type||JSON.stringify(state(e))!==item.state||e.disabled||e.getAttribute('aria-disabled')==='true'||e.getAttribute('aria-readonly')==='true')throw stale()
    if(action==='select'&&role(e)==='combobox'&&e.closest('.el-select')){
      const field=lastContext?.fields?.find(f=>f.ref===args.ref)
      if(!field?.writable)throw Object.assign(Error('当前选择控件不可修改，请重新观察'),{code:'CONTROL_NOT_WRITABLE'})
      return act('use_skill',{kind:'fill_field',field:field.key||field.name,value:args.value},canAct)
    }
    if(action==='click'){
      const link=e.closest('a[href]')
      if(link){checkUrl(link.href);if(link.hasAttribute('download')||(link.target&&link.target!=='_self'))throw Error('请在当前页打开页面，不能新建窗口或下载链接')}
      mark(e)
      if(['radio','checkbox'].includes(item.type))(e.labels?.[0]||e).click()
      else if(e.getAttribute('role')==='combobox')(e.closest('.el-select__wrapper')||e).click()
      else e.click()
    }else{
      if(typeof args.value!=='string'||args.value.length>2000)throw Error('填写内容超出范围')
      if(action==='fill'&&role(e)==='slider'){
        const target=Number(args.value),min=Number(e.getAttribute('aria-valuemin')),max=Number(e.getAttribute('aria-valuemax'))
        if(!args.value.trim()||!Number.isInteger(target)||!e.hasAttribute('aria-valuenow')||!e.hasAttribute('aria-valuemin')||!e.hasAttribute('aria-valuemax')||!Number.isFinite(min)||!Number.isFinite(max)||target<min||target>max)throw Error('评分必须在控件实际范围内，且为整数')
        mark(e);e.focus({preventScroll:true})
        for(let i=0;i<=20;i++){
          if(!canAct())throw interrupted()
          if(!safe(e)||scope()!==observedScope||window.location.href!==observedUrl||e.getAttribute('aria-disabled')==='true')throw stale()
          const current=Number(e.getAttribute('aria-valuenow'));if(current===target)return `已通过控件键盘操作设置${redactPage(item.name,80)}为${target}`
          if(i===20)break
          const key=target>current?'ArrowRight':'ArrowLeft';e.dispatchEvent(new window.KeyboardEvent('keydown',{key,code:key,bubbles:true,cancelable:true}));await Promise.resolve();await Promise.resolve()
          if(Number(e.getAttribute('aria-valuenow'))===current)throw Object.assign(Error('此评分控件未响应键盘操作，请重新观察'),{code:'CONTROL_NOT_WRITABLE'})
        }throw Object.assign(Error('评分控件在有限步内未达到目标，请重新观察'),{code:'CONTROL_NOT_WRITABLE'})
      }
      if(action==='select'&&e.tagName!=='SELECT')throw Object.assign(Error('请先点击自定义下拉框，再点击最新观察中的选项'),{code:'CONTROL_NOT_WRITABLE'})
      if(!['INPUT','TEXTAREA','SELECT'].includes(e.tagName)||e.readOnly)throw Object.assign(Error('此控件不可直接填写；自定义下拉请先点击展开，再按可见选项选择；其他只读控件不能修改'),{code:'CONTROL_NOT_WRITABLE'})
      mark(e)
      // Lazy Vue picker panels install their parsers when the field receives focus.
      // Yield for that render, then recheck task ownership and the observed field.
      e.focus({preventScroll:true});await Promise.resolve();await Promise.resolve()
      if(!canAct())throw interrupted()
      if(!safe(e)||scope()!==observedScope||name(e)!==item.name||JSON.stringify(state(e))!==item.state)throw stale()
      const proto=e.tagName==='INPUT'?window.HTMLInputElement.prototype:e.tagName==='TEXTAREA'?window.HTMLTextAreaElement.prototype:window.HTMLSelectElement.prototype
      Object.getOwnPropertyDescriptor(proto,'value').set.call(e,args.value)
      e.dispatchEvent(new window.Event('input',{bubbles:true}));e.dispatchEvent(new window.Event('change',{bubbles:true}));e.dispatchEvent(new window.Event('blur',{bubbles:true}))
    }
    return `${action==='click'?'已点击':action==='fill'?'已填写':'已选择'}当前页控件：${redactPage(item.name,120)}`
  }
  const ready=async(canContinue=()=>true)=>{
    const deadline=Date.now()+10000
    while(Date.now()<deadline){
      if(!canContinue())throw Object.assign(Error('任务状态已变化'),{code:'ACTION_INTERRUPTED'})
      const area=scope()||document.querySelector('main')||document.body
      if(![...area.querySelectorAll('.el-loading-mask,.el-button.is-loading,[aria-busy="true"]')].some(visible))return
      await new Promise(resolve=>setTimeout(resolve,80))
    }
    throw Object.assign(Error('当前业务区域仍在加载，重新观察后再操作'),{code:'STALE_PAGE'})
  }
  return {observe,act,clear,feedback,fingerprint,describe,ready,context:()=>lastContext}
}
