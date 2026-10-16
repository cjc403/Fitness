import { chromium } from 'playwright-core';
import { randomUUID } from 'node:crypto';
import { AgentError, allowedPath, redact } from './policy.mjs';

export class ControlledBrowser{
 constructor(config,hooks){this.config=config;this.hooks=hooks;this.refs=new Map();this.violation=null;this.revision='';this.closed=false;}
 async start(session,token){
  this.session=session;this.token=token;
  this.browser=await chromium.launch({executablePath:this.config.executablePath,headless:this.config.headless,args:['--disable-extensions','--disable-features=Translate']});
  this.context=await this.browser.newContext({viewport:{width:1360,height:900},locale:'zh-CN',timezoneId:'Asia/Shanghai',acceptDownloads:false,serviceWorkers:'block'});
  await this.context.addInitScript(({origin,token,userInfo})=>{if(location.origin===origin){localStorage.clear();sessionStorage.clear();localStorage.setItem('token',token);localStorage.setItem('userInfo',JSON.stringify(userInfo));}},{origin:this.config.frontendOrigin,token,userInfo:session.userInfo});
  await this.context.route('**/*',route=>this.route(route));
  await this.context.routeWebSocket('**/*',ws=>ws.close());
  this.page=await this.context.newPage();this.page.setDefaultTimeout(7000);this.page.setDefaultNavigationTimeout(15000);
  this.context.on('page',p=>{if(p!==this.page)p.close().catch(()=>{});});
  this.page.on('dialog',d=>d.dismiss().catch(()=>{}));this.page.on('download',d=>d.cancel().catch(()=>{}));
  await this.page.goto(this.config.frontendOrigin+session.home,{waitUntil:'domcontentloaded'});
  await this.page.waitForLoadState('networkidle',{timeout:4000}).catch(()=>{});
 }
 async route(route){
  const req=route.request();let url;try{url=new URL(req.url());}catch{return route.abort('blockedbyclient');}
  const deny=message=>{this.violation=message;return route.abort('blockedbyclient').catch(()=>{});};
  if(url.origin!==this.config.frontendOrigin||!['http:','https:'].includes(url.protocol)||url.username||url.password)return deny('已阻止项目范围以外的网络请求');
  if(url.pathname.startsWith('/local-agent')||url.pathname.startsWith('/api/v1/auth')||url.pathname.startsWith('/api/v1/agent/computer'))return deny('受控浏览器禁止访问控制桥接或身份接口');
  if(req.isNavigationRequest()&&req.frame()===this.page?.mainFrame()&&!allowedPath(url.pathname,this.session))return deny('已阻止当前角色不可访问的页面');
  if(req.resourceType()==='document'&&req.frame().parentFrame())return deny('首版不允许嵌入子页面');
  try{
   if(url.pathname.startsWith('/api/'))await this.hooks.verify();
   const headers={...req.headers()};if(url.pathname.startsWith('/api/'))headers.authorization=`Bearer ${this.token}`;
   if(!['GET','HEAD','OPTIONS'].includes(req.method())){
    if(!url.pathname.startsWith('/api/v1/'))return deny('已阻止非业务接口写请求');
    const body=req.postData()||'';if(Buffer.byteLength(body)>65536)return deny('业务请求过大，已停止发送');
    // The existing query endpoint is a server-authorized, read-only tool protocol.
    let readonly=false;try{const parsed=JSON.parse(body);readonly=url.pathname==='/api/v1/agent/query'&&req.method()==='POST'&&Object.keys(parsed).every(k=>['tool','arguments'].includes(k))&&['profile','courses','bookings','members','training_plans','students','leads','lead_options','retention','cash_summary','analytics'].includes(parsed.tool)&&parsed.arguments&&typeof parsed.arguments==='object'&&!Array.isArray(parsed.arguments);}catch{}
    // This existing member endpoint only ranks readable courses; it does not book them.
    try{const p=JSON.parse(body);if(url.pathname==='/api/v1/member/recommendations'&&req.method()==='POST'&&this.session.role==='MEMBER'&&Object.keys(p).every(k=>['goal','preferredTime'].includes(k))&&typeof p.goal==='string'&&p.goal.length<=100&&['ANY','MORNING','AFTERNOON','EVENING'].includes(p.preferredTime))readonly=true;}catch{}
    if(!readonly){
     await this.hooks.write({method:req.method(),path:url.pathname+url.search,body,contentType:headers['content-type']||''});
     await this.hooks.awaitRunnable();await this.hooks.verify();await this.hooks.awaitRunnable();
     try{
      const response=await route.fetch({headers,method:req.method(),postData:body,maxRedirects:0,maxRetries:0,timeout:15000});
      const raw=await response.text();let result;try{result=JSON.parse(raw);}catch{}
      const ok=response.ok()&&result&&typeof result==='object'&&result.code===0;
      await route.fulfill({response});this.hooks.writeResult(ok,ok?'业务接口返回成功回执':'业务接口拒绝了已确认请求');
     }catch{this.hooks.writeResult(false,'已确认写请求的结果不确定；必须人工核对，不会自动重试');await route.abort('failed').catch(()=>{});}
     return;
    }
   }
   await this.hooks.awaitRunnable();
   await route.continue({headers});
  }catch{if(!this.closed)this.violation='网络请求未通过当前任务身份或确认检查，已中止发送';await route.abort('blockedbyclient').catch(()=>{});}
 }
 async observe(){
  this.assertSafe();for(const h of this.refs.values())await h.handle.dispose().catch(()=>{});this.refs.clear();this.revision=randomUUID();this.observationIndex=(this.observationIndex||0)+1;
  const candidates=await this.page.locator('button,a[href],input:not([type="password"]):not([type="hidden"]),textarea,select,[role="button"],[role="tab"],[role="checkbox"],[role="radio"],[role="combobox"],[role="option"]').elementHandles();const nodes=[],states=[],visible=[];
  for(const h of candidates){
   const d=await h.evaluate(e=>{const s=getComputedStyle(e),r=e.getBoundingClientRect();if(!e.isConnected||s.visibility==='hidden'||s.display==='none'||r.width<1||r.height<1||e.closest('[hidden],[aria-hidden="true"]'))return null;const type=(e.getAttribute('type')||'').toLowerCase();if(['password','hidden','file'].includes(type))return null;const name=(e.getAttribute('aria-label')||e.labels?.[0]?.innerText||e.innerText||e.getAttribute('placeholder')||e.getAttribute('title')||'').replace(/\s+/g,' ').trim();const role=e.getAttribute('role')||(e.tagName==='A'?'link':e.tagName==='SELECT'?'combobox':e.tagName==='BUTTON'?'button':type==='checkbox'?'checkbox':'textbox');const state=['radio','checkbox'].includes(type)?(e.checked?'已选中':'未选中'):e.hasAttribute('aria-selected')?(e.getAttribute('aria-selected')==='true'?'已选中':'未选中'):role==='combobox'?(e.closest('.el-select__wrapper')?.innerText||e.value||'未选择'):(role==='textbox'?e.value:undefined);return {role,name,type,state};}).catch(()=>null);
   if(!d||!d.name){await h.dispose().catch(()=>{});continue;}
   const priority=await h.evaluate(e=>e.getAttribute('role')==='option'?3:e.closest('[role="dialog"],[role="listbox"]')?2:0);
   visible.push({h,d,priority});
  }
  visible.sort((a,b)=>b.priority-a.priority);
  for(const {h,d} of visible){
   if(nodes.length>=60){await h.dispose().catch(()=>{});continue;}
   const ref=`r${this.observationIndex}_${nodes.length}`;nodes.push({ref,role:d.role,name:redact(d.name,120),...(d.type?{type:d.type}:{})});this.refs.set(ref,{handle:h,name:d.name,type:d.type});if(d.state!==undefined&&states.length<25)states.push(`${redact(d.name,60)}：${redact(d.state,120)}`);
  }
  const text=await this.page.locator('body').innerText({timeout:5000});const observation={url:this.page.url(),title:redact(await this.page.title(),200),text:redact('当前控件状态：\n'+states.join('\n')+'\n页面正文：\n'+text,6000),revision:this.revision,nodes};
  let screenshot;try{screenshot='data:image/jpeg;base64,'+(await this.page.screenshot({type:'jpeg',quality:55,timeout:5000})).toString('base64');}catch{}
  this.assertSafe();return {observation,screenshot};
 }
 assertSafe(){if(this.closed||!this.page||this.page.isClosed())throw new AgentError('受控浏览器已经关闭');const u=new URL(this.page.url());if(u.origin!==this.config.frontendOrigin||!allowedPath(u.pathname,this.session))throw new AgentError('当前浏览器已离开允许页面');if(this.violation)throw new AgentError(this.violation);}
 async act(action,args){
  this.assertSafe();const before=this.page.url();let targetName;
  switch(action){
   case 'navigate':await this.page.goto(this.config.frontendOrigin+args.path,{waitUntil:'domcontentloaded'});break;
   case 'back':await this.page.goBack({waitUntil:'domcontentloaded'});break;
   case 'scroll':await this.page.mouse.wheel(0,args.direction==='down'?650:-650);break;
   case 'observe':break;
   case 'click':case 'fill':case 'select':{
    const item=this.refs.get(args.ref);if(!item||!await item.handle.isVisible()||!await item.handle.isEnabled())throw new AgentError('页面元素已失效，请重新观察');
    const current=await item.handle.evaluate(e=>({connected:e.isConnected,type:(e.getAttribute('type')||'').toLowerCase(),name:(e.getAttribute('aria-label')||e.labels?.[0]?.innerText||e.innerText||e.getAttribute('placeholder')||e.getAttribute('title')||'').replace(/\s+/g,' ').trim()}));
    if(!current.connected||current.name!==item.name||current.type!==item.type||['password','hidden','file'].includes(current.type))throw new AgentError('页面元素已变化，请重新观察');
    targetName=redact(item.name,120);
    if(action==='click'){
     const href=await item.handle.evaluate(e=>e.closest('a[href]')?.href||null);
     if(href){const target=new URL(href,before);if(target.origin!==this.config.frontendOrigin||!['http:','https:'].includes(target.protocol)||!allowedPath(target.pathname,this.session))throw new AgentError('已阻止外部应用或不允许的页面链接');}
    }
    if(action==='click'&&['radio','checkbox'].includes(item.type)){
     // Styled Element Plus inputs are covered by their associated label.
     const label=await item.handle.evaluateHandle(e=>e.labels?.[0]||e);
     try{await label.asElement().click();}finally{await label.dispose();}
    }else if(action==='click'&&await item.handle.getAttribute('role')==='combobox'){
     const wrapper=await item.handle.evaluateHandle(e=>e.closest('.el-select__wrapper')||e);
     try{await wrapper.asElement().click();}finally{await wrapper.dispose();}
    }else if(action==='click')await item.handle.click();else if(action==='fill')await item.handle.fill(args.value);else{
     if(await item.handle.evaluate(e=>e.tagName)!=='SELECT')throw new AgentError('这是自定义控件，请用click打开下拉框，再从最新观察中点击选项；单选按钮也使用click');
     await item.handle.selectOption(args.value);
    }break;
   }
   default:throw new AgentError('浏览器动作不受支持');
  }
  await this.page.waitForTimeout(200);this.assertSafe();return {before,after:this.page.url(),targetName};
 }
 async close(){this.closed=true;await this.context?.close().catch(()=>{});await this.browser?.close().catch(()=>{});}
}
