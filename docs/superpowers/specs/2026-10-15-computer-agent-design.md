# FitChain 任务型电脑 Agent 首版

用户明确要求接近缩小版 Codex 的电脑操作能力，并批准开始开发。沿用常规方案直接执行、本地开发、保留业务数据的约定；原周标签不动，不部署或推送。首版以实际控制浏览器、任务反馈循环、本地成果文件为交付，不把几个固定业务接口称为通用电脑控制。

用户补充强调六端都要好用：总部/店长/前台/教练/会籍顾问/会员均为首版正式验收角色，不以教练页面作为通用首页。每角色使用自己的首页、允许路由、推荐任务和数据范围；共同执行引擎不能赋予共同业务权限。验收覆盖各角色真实登录/至少一条实际浏览器任务、角色提示、越权拒绝及任务会话隔离。

## 可验收的任务

1. 用户在原助手会话选择“电脑执行”，说“打开周课表，查看本周课程”，受控 Edge 窗口真实打开、点击、读取，过程在任务面板显示。
2. “整理我本周的课程，生成课表文件保存并打开”：自主观察/操作页面，按权限查询课程，生成真实 UTF-8 CSV 到桌面 FitChain任务成果 文件夹，提供文件名/大小/SHA-256，并请求 Windows 打开文件。
3. 表单操作使用真实点击/填写；发生业务写请求时先暂停网络提交，展示不可换参的请求预览；用户明确确认才放行该次请求。保留后台角色、门店、版本等原校验。
4. 任务可暂停、继续、停止；资料不全时询问并接受补充。失败显示真实原因，写入结果不确定时不自动重试或伪报成功。

首版不是任意桌面软件/任意终端执行器。通用 Windows 控件和视觉模型后续接入；目前使用真实网页元素观察，不宣称截图已具备视觉理解。受控窗口使用独立临时浏览器上下文，不接管用户私人浏览器资料。

## 架构与边界

- Spring 增加只读的 computer/session 与 computer/next，复用 AgentIdentity、AgentModelClient 和权限。API密钥留在现有后台保护配置，执行器没有模型密钥。
- 新 `fitness-agent-runner`，Node24+、playwright-core1.64.1、现有 Edge；127.0.0.1:5188。执行器验证后台当前身份，每步重验角色/门店/登录会话；一台电脑同一时刻一个活动任务。令牌仅在内存，任务重启不自动恢复。
- 前端 `/local-agent` 开发代理只允许本机回环访问，校验 Host/Origin，注入本机随机桥接凭证；该凭证保存在用户LocalAppData，不在仓库/前端/日志。手机LAN不自动获得电脑控制权。
- 执行器仅允许已配置的 FitChain 前端源与后端源；阻止外站导航和网络，拒绝任意JS、CSS选择器、Shell或模型指定文件路径。模型只能选择当前观察的元素引用。
- 文件只能新建在配置的专属成果目录，不能覆盖旧文件或读取任意电脑文件；防路径穿越/链接及CSV公式注入。打开文件只接受本任务已生成的成果ID；Windows进程启动成功仅报告“已请求打开”，不据此声称已看见文件窗口。
- 浏览器内非GET/HEAD请求全部由执行器审查，固定协议读取例外需明确列出；后台写入仍由原服务鉴权。确认放行必须与当前待处理请求ID、用户、角色、门店、会话一致；重复确认不重复发送。停止会中止待放行请求并关停本任务上下文。
- 页面文字均不可信，不能授权、指定电脑路径或提升角色。观察不采集密码值、存储、Cookie、隐藏DOM；对正文/元素文字限制长度并脱敏。截图只在内存用于用户看进度，不持久化或直接发给模型。

## 后台接口（Result 包装）

`GET /api/v1/agent/computer/session` → `{userId,role,venueId,sessionKey,home,permissions,userInfo}`。身份字段服务器生成；home按六角色现有首页；userInfo为受控窗口登录所需的安全字段，不能包括密码或令牌。

`POST /api/v1/agent/computer/next` body `{goal,observation,history,artifacts,input?}`。

- goal<=2000字；observation `{url,title,text,revision,nodes:[{ref,role,name,type?}]}`，正文<=6000字、节点<=60、节点名<=120。
- history<=12 `{action,summary,ok}`，每条summary<=500；artifacts<=10 `{id,name}`；input可选补充文本<=2000。
- 模型只返回一个动作：`{action,arguments,summary}`。summary由服务器按动作生成；模型散文不作为执行成功依据。无真实模型结果则返回明确的UNAVAILABLE，不降级为虚构动作。
- 白名单：`navigate {path}`（仅角色可访问的项目路径）、`observe {}`、`click {ref}`、`fill {ref,value}`、`select {ref,value}`、`scroll {direction:up|down}`、`back {}`、`export_schedule {from,to}`、`open_artifact {artifactId}`、`ask_user {question}`、`finish {}`。
- click/fill/select必须引用本轮观察节点；navigate按角色权限校验；export_schedule日期范围<=31天；artifactId必须来自提供的真实成果列表；嵌套参数拒绝未知字段。session无效、模型伪造参数、越权路径均拒绝。

## 本地执行器 HTTP（Result 包装）

前端前缀 `/local-agent/v1`，执行器实际 `/v1`。除公开且不含任务数据的健康端点外，都要求桌面桥接凭证与Bearer，再向后台session验证身份。

- GET `/status` → `{available,platform,scope,outputDirectory}`。
- GET `/tasks` → 当前身份会话最近任务数组。
- POST `/tasks` `{goal}` → Task，后台循环异步启动。
- GET `/tasks/:id` → Task。
- POST `/tasks/:id/pause|resume|stop` `{}` → Task。
- POST `/tasks/:id/confirm` `{confirmationId}` → Task，仅放行当时冻结的一个业务请求。
- POST `/tasks/:id/input` `{message}` → Task，补充后继续。
- Task `{id,goal,status,createdAt,updatedAt,steps,artifacts,pendingConfirmation?,question?,error?,screenshot?}`。
- 状态 RUNNING/PAUSED/WAITING_CONFIRMATION/WAITING_INPUT/SUCCEEDED/FAILED/CANCELLED。steps `{index,action,label,status,detail?}`；artifacts `{id,name,bytes,sha256,path?}`；pendingConfirmation `{id,method,path,summary,fields:[{label,value}]}`。
- screenshot为当前受控页面的data:image/jpeg;base64，内存最近一张；无截图也可执行。
- 最多30步、总运行10分钟、同动作最多2次受控只读纠错；暂停/确认等待有独立有效期。结果由执行器依据实际反馈生成。

## 验证与交付

TDD验证模型动作白名单/角色/session、实际浏览器元素与写请求确认、停止/暂停/过期/跨账号、文件隔离与重复确认。真实业务测试写只用显式标记的PG隔离库；浏览器测试必须绑定已验证前端代理，阻止绝对API地址绕过。正常本地只读任务可生成非敏感课表成果；不发测试消息、不改真实业务。

运行完整前端与执行器测试、构建；后台在新鲜TEMP副本clean package。本地启用先备份旧JAR/库并校验既有迁移字节、数据数量/密码哈希。维护交接§0.17/§10、AGENTS及指南，保存本地阶段提交。物理手机和任意Windows应用不冒称完成。
