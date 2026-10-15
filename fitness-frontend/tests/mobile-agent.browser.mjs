// Optional browser QA. Uses an isolated browser context and mocked HTTP, never real accounts or business writes.
// FITCHAIN_PLAYWRIGHT_MODULE can point to an already installed Playwright entrypoint; no project dependency is added.
import assert from 'node:assert/strict'
import { mkdir, writeFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import { pathToFileURL } from 'node:url'
const modulePath = process.env.FITCHAIN_PLAYWRIGHT_MODULE || 'playwright'
const { chromium } = await import(modulePath.includes(':') ? pathToFileURL(modulePath).href : modulePath)
const base = process.env.FITCHAIN_QA_BASE || 'http://127.0.0.1:4178'
const out = resolve(process.env.FITCHAIN_QA_DIR || 'qa/mobile-agent-20261015')
await mkdir(out, { recursive: true })
const browser = await chromium.launch({ channel: 'msedge', headless: true })
const report = { kind: 'Mocked frontend browser QA; not backend integration or physical-device verification', base, cases: [], errors: [] }
const homes = { SUPER_ADMIN: '/dashboard', MANAGER: '/dashboard', RECEPTION: '/members', COACH: '/coach-workbench', SALES: '/advisor-workbench', MEMBER: '/member-home' }
const names = { SUPER_ADMIN: '总部管理员', MANAGER: '店长', RECEPTION: '前台', COACH: '教练', SALES: '会籍顾问', MEMBER: '会员' }
const common = ['ASSISTANT_USE','COURSE_READ','BOOKING_READ']
const permissions = {
  SUPER_ADMIN: [...common,'DASHBOARD_READ','MEMBER_READ','ACCOUNT_MANAGE','LEAD_READ','CASH_SHIFT_READ','TRAINING_PLAN_READ','PRODUCT_READ','VENUE_READ'],
  MANAGER: [...common,'DASHBOARD_READ','MEMBER_READ','LEAD_READ','CASH_SHIFT_READ','TRAINING_PLAN_READ','PRODUCT_READ','VENUE_READ'],
  RECEPTION: [...common,'MEMBER_READ','CASH_SHIFT_READ','PRODUCT_READ','MEMBER_WRITE'], COACH: [...common,'COACH_WORKBENCH','TRAINING_PLAN_READ'], SALES: [...common,'MEMBER_READ','LEAD_READ'], MEMBER: ['ASSISTANT_USE','MEMBER_SELF_SERVICE']
}
const field = (type, description, extra = {}) => ({ type, description, ...extra })
const tool = (name, label, write, properties = {}, required = []) => ({ name, label, description: `${label}（浏览器测试数据）`, write, parameters: { type: 'object', additionalProperties: false, properties, required } })
const courseTool = tool('courses','可预约课程',false)
const bookTool = tool('book_course','预约课程',true,{ scheduleId: field('integer','排课编号',{minimum:1}) },['scheduleId'])
const draftTool = tool('training_draft','保存训练草稿',true,{memberId:field('integer','会员编号',{minimum:1}),title:field('string','计划标题'),goal:field('string','训练目标',{enum:['GENERAL','FAT_LOSS','MUSCLE_GAIN','POSTURE','ENDURANCE']}),startDate:field('string','开始日期',{format:'date'}),endDate:field('string','结束日期',{format:'date'}),exercises:field('array','训练动作',{minItems:1,maxItems:30})},['memberId','title','goal','startDate','endDate','exercises'])
const trialTool = tool('book_trial','安排体验课',true,{leadId:field('integer','潜客编号',{minimum:1}),expectedVersion:field('integer','当前版本',{minimum:0}),hostId:field('integer','接待人编号',{minimum:1}),startsAt:field('string','开始时间',{format:'date-time'}),endsAt:field('string','结束时间',{format:'date-time'}),content:field('string','体验内容')},['leadId','expectedVersion','hostId','startsAt','endsAt','content'])
const envelope = data => ({ code: 0, message: 'success', data })
const emptySummary = { todaySchedules:0,upcomingSchedules:0,bookedStudents:0,attendedStudents:0,completedSessions:0,deliveredHours:0,serviceRevenue:0,estimatedCommission:0,myMembers:0,newMembersThisMonth:0,followUpsThisWeek:0,dueFollowUps:0,salesOrders:0,salesAmount:0 }
async function setup(role, width = 390) {
  const context = await browser.newContext({ viewport: { width, height: 844 }, isMobile: width < 761, hasTouch: width < 761, reducedMotion: 'reduce', serviceWorkers: 'block' })
  await context.addInitScript(({ role, permissions }) => { localStorage.setItem('token','frontend-mock-session-only'); localStorage.setItem('userInfo',JSON.stringify({id:800,role,permissions,username:'mock',realName:'前端验收',organizationName:'模拟验收门店',organizationId:1,venueId:1})) }, { role, permissions: permissions[role] })
  const page = await context.newPage(), calls = [], actions = []
  page.on('pageerror', error => report.errors.push(`${role}: ${error.stack || error.message}`))
  await context.route(base + '/api/**', async route => {
    const request = route.request(), path = new URL(request.url()).pathname.replace('/api',''), body = request.postDataJSON(), method = request.method()
    calls.push({ path, method, body })
    let data = []
    if (path === '/v1/agent/profile') data = {role,roleName:names[role],greeting:`你好，${names[role]}。此为前端模拟验收。`,suggestions:['查询课程'],tools:[courseTool,...(role==='MEMBER'?[bookTool]:role==='COACH'?[draftTool]:role==='SALES'?[trialTool]:[])],model:{configured:false,toolCalling:false,state:'LOCAL_TOOLS'}}
    else if (path === '/v1/agent/actions') data = [...actions]
    else if (path === '/v1/agent/query') data = {tool:'courses',label:'可预约课程',source:'模拟业务接口',asOf:'2026-10-15T10:00:00',truncated:false,data:[{scheduleId:12,courseName:'前端测试课程',coachName:'测试教练',startTime:'2026-10-16T10:00:00',remainingPlaces:8}]}
    else if (path === '/v1/agent/actions/preview') { data = {id:'browser-preview',tool:body.tool,title:'操作预览',summary:'仅点击确认执行后才会提交；此处为模拟回执。',parameters:body.arguments,status:'PENDING',createdAt:'2026-10-15T10:00:00',expiresAt:'2099-10-15T10:10:00'}; actions.unshift(data) }
    else if (path.endsWith('/confirm') || path.endsWith('/cancel')) { data = {...actions[0],status:path.endsWith('/confirm')?'SUCCEEDED':'CANCELLED',result:path.endsWith('/confirm')?{id:99,status:'BOOKED'}:undefined}; actions[0] = data }
    else if (path === '/v1/agent/chat') data = {answer:'已读取模拟课程。',suggestions:[],source:'本地工具',mode:'LOCAL_TOOLS',results:[],actions:[],traceId:'mock'}
    else if (path.includes('/reports/dashboard')) data = {totalMembers:12,activeMembers:12,pausedMembers:0,blacklistedMembers:0,venues:1,courses:2,todaySchedules:1,booked:3,attended:4,cancelled:0,attendanceRate:80,weeklyTrend:[],generatedAt:'2026-10-15T10:00:00'}
    else if (path.endsWith('/coach/workbench')) data = {summary:emptySummary,schedules:[]}
    else if (path.endsWith('/advisor/workbench')) data = {advisorName:'测试顾问',summary:emptySummary,members:[]}
    else if (path === '/v1/admin/members') data = {items:[{id:20,memberNo:'MOCK20',realName:'模拟会员',phone:'138****0000',status:'ACTIVE'}],total:1}
    else if (path === '/v1/member/profile') data = {realName:'模拟会员',memberNo:'MOCK20'}
    else if (path.endsWith('/body-measurements')) data = {memberId:20,recordCount:0,points:[],changes:[],records:[]}
    else if (path.endsWith('/access-code')) data = {qrPayload:'frontend-public-test-only',memberName:'模拟会员',memberNo:'MOCK20',ttlSeconds:60}
    else if (path.endsWith('/notifications/summary')) data = {unread:0}
    else if (path.endsWith('/recommendations')) data = {summary:'模拟推荐',source:'前端测试',recommendations:[]}
    else if (path.includes('/training/readiness')) data = {bookable:[],pendingSubmit:[],pendingConfirm:[]}
    else if (path.endsWith('/reviews/coach/summary') || path.includes('/reviews/coach')) data = {reviewCount:0,averageRating:null,distribution:{},recent:[]}
    await route.fulfill({status:200,contentType:'application/json',body:JSON.stringify(envelope(data))})
  })
  await page.goto(base + homes[role]); await page.waitForSelector('.mobile-navigation'); await page.waitForTimeout(200)
  return { context, page, calls }
}
async function checkFits(page, label) {
  const dimensions = await page.evaluate(() => ({width:innerWidth,body:document.body.scrollWidth,main:document.querySelector('.main')?.clientWidth,mainScroll:document.querySelector('.main')?.scrollWidth}))
  assert.ok(dimensions.body <= dimensions.width + 1, `${label}: body overflow ${JSON.stringify(dimensions)}`)
  assert.ok(dimensions.mainScroll <= dimensions.main + 1, `${label}: main overflow ${JSON.stringify(dimensions)}`)
  return dimensions
}
try {
  for (const role of Object.keys(homes)) {
    const r = await setup(role), { page } = r
    assert.equal(await page.locator('.mobile-navigation a').first().getAttribute('href'), homes[role])
    assert.equal(await page.locator('.aside').isVisible(), false)
    await checkFits(page, role)
    await page.screenshot({path:resolve(out,`${role.toLowerCase()}-mobile.png`)})
    if(role==='RECEPTION') {
      await page.getByRole('button',{name:'新增会员',exact:true}).click()
      const dialog=page.getByRole('dialog',{name:'新增会员'}); await dialog.waitFor()
      for(const width of [390,320]) { await page.setViewportSize({width,height:740}); const box=await dialog.boundingBox(); assert.ok(box.x>=0 && box.x+box.width<=width+1 && box.height<=740,`member dialog fits ${width}`) }
      await page.screenshot({path:resolve(out,'reception-member-dialog-320.png')})
      await dialog.getByRole('button',{name:'取消',exact:true}).click(); await page.setViewportSize({width:390,height:844})
    }
    await page.getByRole('button',{name:'更多',exact:true}).click()
    await page.getByRole('dialog',{name:'全部功能'}).waitFor({state:'visible'})
    if (['COACH','MEMBER','RECEPTION'].includes(role)) assert.equal(await page.locator('.mobile-menu-grid a[href="/leads"]').count(),0)
    assert.ok((await page.locator('.pwa-install').innerText()).includes('浏览器'))
    await page.getByRole('button',{name:'关闭更多菜单'}).click()
    await page.getByRole('button',{name:'打开业务助手',exact:true}).click()
    const panel = page.getByRole('dialog',{name:'FitChain 业务助手'}); await panel.waitFor({state:'visible'})
    const rect = await panel.boundingBox(); assert.ok(rect.x>=0 && rect.width<=391 && rect.height<=845)
    await page.keyboard.press('Shift+Tab'); assert.equal(await page.evaluate(()=>!!document.activeElement?.closest('.assistant-panel')),true)
    await page.getByRole('button',{name:'业务工具',exact:true}).click()
    await page.locator('.tool-field select').first().selectOption('courses')
    await page.getByRole('button',{name:'查询业务数据',exact:true}).click()
    await page.getByText('前端测试课程',{exact:true}).waitFor()
    if (['MEMBER','SALES','COACH'].includes(role)) {
      const toolName = role==='MEMBER'?'book_course':role==='SALES'?'book_trial':'training_draft'
      await page.locator('.tool-field select').first().selectOption(toolName)
      if (role==='MEMBER') await page.getByLabel('排课编号').fill('12')
      if (role==='SALES') { for(const [label,value] of [['潜客编号','2'],['当前版本','0'],['接待人编号','3'],['开始时间','2026-10-16T10:00'],['结束时间','2026-10-16T11:00'],['体验内容','体验力量训练']]) await page.getByLabel(label).fill(value) }
      if (role==='COACH') { await page.getByLabel('会员编号').fill('20'); await page.getByLabel('计划标题').fill('初学者训练'); await page.getByLabel('训练目标').selectOption('GENERAL'); await page.getByLabel('开始日期').fill('2026-10-16'); await page.getByLabel('结束日期').fill('2026-10-23'); await page.getByRole('button',{name:'添加训练动作'}).click(); await page.getByLabel('动作名称').fill('深蹲'); assert.equal(await page.locator('textarea').count(),0) }
      await page.screenshot({path:resolve(out,`${role.toLowerCase()}-tool-form.png`)})
      await page.getByRole('button',{name:'生成操作预览',exact:true}).click(); await page.getByRole('button',{name:'确认执行',exact:true}).waitFor()
      assert.equal(r.calls.filter(c=>c.path.endsWith('/confirm')).length,0)
      await page.screenshot({path:resolve(out,`${role.toLowerCase()}-preview.png`)})
      await r.context.setOffline(true); await page.getByText('当前离线，无法查询或提交操作。恢复网络后可重试，系统不会自动补发。').waitFor()
      assert.equal(await page.getByRole('button',{name:'确认执行',exact:true}).isDisabled(),true)
      await r.context.setOffline(false); await page.getByRole('button',{name:'确认执行',exact:true}).click(); await page.getByText('业务操作已成功，以下为服务器回执。').waitFor()
      const confirm = r.calls.find(c=>c.path.endsWith('/confirm')); assert.deepEqual(confirm.body,{})
      if(role==='SALES') assert.equal(r.calls.find(c=>c.path.endsWith('/preview')).body.arguments.startsAt,'2026-10-16T10:00:00')
      if(role==='COACH') assert.equal(r.calls.find(c=>c.path.endsWith('/preview')).body.arguments.exercises[0].name,'深蹲')
    }
    await page.getByRole('button',{name:'关闭业务助手'}).click()
    for(const width of [360,320]) { await page.setViewportSize({width,height:740}); await checkFits(page,`${role}-${width}`) }
    await page.setViewportSize({width:1366,height:900}); assert.equal(await page.locator('.aside').isVisible(),true); assert.equal(await page.locator('.mobile-navigation').isVisible(),false)
    await page.getByRole('button',{name:'打开业务助手',exact:true}).click(); await page.getByRole('dialog',{name:'FitChain 业务助手'}).waitFor()
    const desktopRect = await page.getByRole('dialog',{name:'FitChain 业务助手'}).boundingBox(); assert.ok(desktopRect.width<600 && desktopRect.x>0)
    await page.screenshot({path:resolve(out,`${role.toLowerCase()}-desktop.png`)})
    report.cases.push({role,home:homes[role],mobileWidths:[390,360,320],desktopWidth:1366,tool:role==='MEMBER'?'book_course':role==='SALES'?'book_trial':role==='COACH'?'training_draft':'courses',passed:true})
    await r.context.close()
  }
  assert.deepEqual(report.errors,[],'No runtime page errors')
} catch(error) { report.failure = String(error.stack || error); throw error }
finally { await writeFile(resolve(out,'browser-verification.json'),JSON.stringify(report,null,2),'utf8'); await browser.close(); console.log(JSON.stringify(report,null,2)) }
