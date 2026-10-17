import assert from 'node:assert/strict'
import {createHash} from 'node:crypto'
import {fileURLToPath,pathToFileURL} from 'node:url'
export const base='http://127.0.0.1:4177'
export async function assertV2Target(){
 const v=await fetch(base+'/__fitchain/dev-health').then(r=>r.json()),root=fileURLToPath(new URL('../../',import.meta.url)).replace(/[\\/]$/,'')
 assert.equal(v.workspace,createHash('sha256').update(root.replace(/\\/g,'/').toLowerCase()).digest('hex'));assert.equal(v.port,4177);assert.equal(v.apiOrigin,'http://127.0.0.1:18080')
 assert.equal((await fetch('http://127.0.0.1:18091/actuator/info').then(r=>r.json())).app.qaDatabase,'agent_v2_browser')
}
export async function createBrowser(){await assertV2Target();const {chromium}=await import(pathToFileURL(process.env.FITCHAIN_PLAYWRIGHT_MODULE).href);return chromium.launch({channel:'msedge',headless:true,args:['--no-proxy-server']})}
export async function fixtureApi(user){
 await assertV2Target();const login=await fetch(base+'/api/v1/auth/login',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({username:user,password:'password'})}).then(r=>r.json());assert.equal(login.code,0,login.message);const token=login.data.accessToken
 return async(path,method='GET',body)=>{assert.ok(path.startsWith('/')&&!path.includes('://')&&!path.includes('..'));const r=await fetch(base+'/api/v1'+path,{method,headers:{authorization:'Bearer '+token,'content-type':'application/json'},...(body===undefined?{}:{body:JSON.stringify(body)})});const v=await r.json();assert.equal(v.code,0,v.message);return v.data}
}
export async function loginPage(browser,user,home,report){
 const context=await browser.newContext({viewport:{width:1536,height:1000}});await context.route('**/*',route=>{if(new URL(route.request().url()).origin!==base){report.blocked.push(new URL(route.request().url()).origin);return route.abort()}return route.continue()})
 const page=await context.newPage();page.on('pageerror',e=>report.errors.push(e.message));await page.goto(base+'/login');await page.getByPlaceholder('请输入用户名').fill(user);await page.getByPlaceholder('请输入密码').fill('password');await page.getByRole('button',{name:'登录',exact:true}).click();await page.waitForURL(base+home);await page.bringToFront();await page.getByRole('button',{name:'打开业务助手',exact:true}).click();const panel=page.getByRole('dialog',{name:'FitChain 业务助手'});return {context,page,panel}
}
export async function call(page,method,...args){return page.evaluate(async({method,args})=>{const url=performance.getEntriesByType('resource').map(e=>e.name).find(u=>u.includes('/src/agent/currentPageAgent.ts?t='))||'/src/agent/currentPageAgent.ts';return(await(await import(url))[method](...args)).data},{method,args})}
export async function startTask(page,panel,goal){
 await panel.getByRole('button',{name:'会话',exact:true}).click();await page.getByLabel('业务问题',{exact:true}).fill(goal);await panel.getByRole('button',{name:'发送',exact:true}).click()
 for(let i=0;i<60;i++){const t=(await call(page,'listComputerTasks'))[0];if(t&&t.goal===goal)return t;await page.waitForTimeout(250)}throw Error('Task creation did not complete')
}
export async function waitTask(page,id,previous){
 let progress;for(let i=0;i<600;i++){const t=await call(page,'getComputerTask',id);const key=t.status+':'+t.steps.length;if(key!==progress){console.log(id+' '+key);progress=key}if(['SUCCEEDED','FAILED','PARTIAL','WAITING_INPUT','WAITING_CONFIRMATION','PAUSED'].includes(t.status)&&!(t.status==='WAITING_CONFIRMATION'&&t.pendingConfirmation?.id===previous))return t;await page.waitForTimeout(600)}throw Error('Task did not settle')
}
export const day=(offset=0)=>{const now=new Date();now.setDate(now.getDate()+offset);return [now.getFullYear(),String(now.getMonth()+1).padStart(2,'0'),String(now.getDate()).padStart(2,'0')].join('-')}
export async function seedFreeSchedule(label,offset=2){
 const admin=await fixtureApi('admin'),course=await admin('/admin/courses','POST',{name:label,type:'REGULAR',durationMinutes:45,maxCapacity:20,fee:0,intensity:'LOW',venueId:1,description:'Agent V2 isolated acceptance fixture'})
 const schedule=await admin('/admin/schedules','POST',{courseId:course.id,venueId:1,coachName:'隔离验收教练',scheduledStartTime:day(offset)+'T14:00:00',scheduledEndTime:day(offset)+'T14:45:00',maxStudents:20})
 return {admin,course,schedule}
}
