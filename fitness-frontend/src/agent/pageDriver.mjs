export const redactPage = (value, max=6000) => String(value??'').replace(/Bearer\s+[^\s"']+/gi,'Bearer [已隐藏]').replace(/eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/g,'[令牌已隐藏]').replace(/((?:password|refresh_token|access_token|api[_-]?key|密码|口令)\s*[:=：]\s*)[^\s,;，；]+/gi,'$1[已隐藏]').slice(0,max)
const exclude='[data-page-agent-exclude],.assistant,.header,.mobile-navigation,.mobile-more-backdrop,[hidden],[aria-hidden="true"]'
const controls='button,a[href],input,textarea,select,[role="button"],[role="menuitem"],[role="tab"],[role="checkbox"],[role="radio"],[role="combobox"],[role="option"]'
export function createPageDriver({document,window,router,allowed}) {
  let refs=new Map(),sequence=0,observedUrl='',highlighted=null
  const trail=[]
  const stale=()=>Object.assign(Error('页面已变化，重新观察后再操作'),{code:'STALE_PAGE'})
  const visible=e=>{const s=window.getComputedStyle(e),r=e.getBoundingClientRect();return e.isConnected&&!e.closest(exclude)&&s.visibility!=='hidden'&&s.display!=='none'&&r.width>0&&r.height>0}
  const name=e=>(e.getAttribute('aria-label')||e.labels?.[0]?.innerText||e.closest('.el-form-item')?.querySelector('.el-form-item__label')?.innerText||e.innerText||e.getAttribute('placeholder')||e.getAttribute('title')||'').replace(/\s+/g,' ').trim()
  const type=e=>(e.getAttribute('type')||'').toLowerCase()
  const safe=e=>visible(e)&&!['password','hidden','file'].includes(type(e))&&!/password|token|secret|api.?key/i.test([e.id,e.getAttribute('name'),e.getAttribute('autocomplete')].join(' '))&&!/退出登录|切换账号|修改密码/.test(name(e))
  const checkUrl=url=>{const u=new URL(url,window.location.href);if(u.origin!==window.location.origin||!['http:','https:'].includes(u.protocol)||u.username||u.password||!allowed(u.pathname))throw Error('当前角色不能访问该页面或外部链接');return u}
  const clear=()=>{if(highlighted){highlighted.removeAttribute('data-page-agent-target');highlighted=null}}
  const mark=e=>{clear();highlighted=e;e.setAttribute('data-page-agent-target','true');e.scrollIntoView({block:'center',inline:'nearest',behavior:'instant'})}
  const observe=()=>{
    checkUrl(window.location.href);clear();refs=new Map();observedUrl=window.location.href;sequence++
    const candidates=[...document.querySelectorAll(controls)].filter(e=>safe(e)&&name(e)).sort((a,b)=>(b.getAttribute('role')==='option'?3:b.closest('[role="dialog"],[role="listbox"]')?2:0)-(a.getAttribute('role')==='option'?3:a.closest('[role="dialog"],[role="listbox"]')?2:0))
    const nodes=[],states=[]
    for(const e of candidates.slice(0,60)){
      const ref=`r${sequence}_${nodes.length}`,label=name(e),kind=type(e),role=e.getAttribute('role')||(e.tagName==='A'?'link':e.tagName==='SELECT'?'combobox':e.tagName==='BUTTON'?'button':['checkbox','radio'].includes(kind)?kind:'textbox')
      refs.set(ref,{e,name:label,type:kind});nodes.push({ref,role,name:redactPage(label,120),...(kind?{type:kind}:{})})
      const state=['checkbox','radio'].includes(kind)?(e.checked?'已选中':'未选中'):role==='combobox'?(e.closest('.el-select__wrapper')?.innerText||e.value||'未选择'):role==='textbox'?e.value:undefined
      if(state!==undefined&&states.length<25)states.push(`${redactPage(label,60)}：${redactPage(state,120)}`)
    }
    const roots=[...document.querySelectorAll('main,[role="dialog"]')].filter(e=>!e.closest(exclude)&&visible(e))
    const text=(roots.length?roots:[document.body]).map(e=>{const clone=e.cloneNode(true);clone.querySelectorAll(exclude+',script,style,input[type="password"]').forEach(n=>n.remove());return clone.innerText||clone.textContent||''}).join('\n')
    return {url:window.location.href,title:redactPage(document.title,200),text:redactPage('当前控件状态：\n'+states.join('\n')+'\n页面正文：\n'+text),revision:`page_${sequence}`,nodes}
  }
  const navigate=async path=>{checkUrl(path);const before=window.location.pathname;await router.push(path);if(window.location.pathname===before&&path!==before)throw Error('当前页没有完成所需切换');if(before!==window.location.pathname)trail.push(before);return `当前标签页已切换到 ${window.location.pathname}`}
  const act=async(action,args)=>{
    checkUrl(window.location.href)
    if(observedUrl!==window.location.href)throw stale()
    if(action==='navigate')return navigate(args.path)
    if(action==='back'){const path=trail.pop();if(!path)throw Error('没有本任务可返回的项目页面');checkUrl(path);await router.push(path);return `当前页已返回 ${path}`}
    if(action==='observe')return `已读取当前页 ${window.location.pathname}`
    if(action==='scroll'){if(!['up','down'].includes(args.direction))throw Error('滚动方向不正确');const main=document.querySelector('main');(main||window).scrollBy({top:args.direction==='down'?600:-600,behavior:'instant'});return `已滚动当前页${args.direction==='down'?'下方':'上方'}`}
    if(!['click','fill','select'].includes(action))throw Error('不支持的网页动作')
    const item=refs.get(args.ref),e=item?.e
    if(!e||!safe(e)||name(e)!==item.name||type(e)!==item.type||e.disabled||e.getAttribute('aria-disabled')==='true')throw stale()
    if(action==='click') {
      const link=e.closest('a[href]')
      if(link){checkUrl(link.href);if(link.hasAttribute('download')||(link.target&&link.target!=='_self'))throw Error('请在当前页打开页面，不能新建窗口或下载链接')}
      mark(e)
      if(['radio','checkbox'].includes(item.type))(e.labels?.[0]||e).click()
      else if(e.getAttribute('role')==='combobox')(e.closest('.el-select__wrapper')||e).click()
      else e.click()
    } else {
      if(typeof args.value!=='string'||args.value.length>2000)throw Error('填写内容超出范围')
      if(action==='select'&&e.tagName!=='SELECT')throw Error('请先点击自定义下拉框，再点击最新观察中的选项')
      if(!['INPUT','TEXTAREA','SELECT'].includes(e.tagName)||e.readOnly)throw Error('此控件不可填写')
      mark(e)
      const proto=e.tagName==='INPUT'?window.HTMLInputElement.prototype:e.tagName==='TEXTAREA'?window.HTMLTextAreaElement.prototype:window.HTMLSelectElement.prototype
      Object.getOwnPropertyDescriptor(proto,'value').set.call(e,args.value)
      e.dispatchEvent(new window.Event('input',{bubbles:true}));e.dispatchEvent(new window.Event('change',{bubbles:true}));e.dispatchEvent(new window.Event('blur',{bubbles:true}))
    }
    return `${action==='click'?'已点击':action==='fill'?'已填写':'已选择'}当前页控件：${redactPage(item.name,120)}`
  }
  return {observe,act,clear}
}
