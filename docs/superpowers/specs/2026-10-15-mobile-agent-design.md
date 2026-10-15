# 手机 H5/PWA 与六角色 Agent 设计

用户于2026-10-15批准方案并要求逐步开发。执行沿用“常规方案直接按推荐执行”，不重复申请方案批准；默认本地开发，不部署。首版需交付手机六角色入口与核心操作、真实只读业务工具、会员预约/取消、顾问体验预约、教练训练草稿的确认执行。

## 界面

保持同一Vue应用和原路由。窄屏使用顶栏、角色底部导航与更多菜单，主要页面触控表单、可滚动表格和对话框均在屏幕内；桌面不替换原布局。助手全屏对话，分为会话、业务工具、执行记录，按角色显示提示与工具。PWA manifest及仅缓存公开静态壳的service worker；不缓存API、凭证、用户资料或登录HTML。断网禁止写入并说明，安装提示准确体现环境支持，不声称局域网HTTP满足安装条件。

## API（均沿用Result包装与ASSISTANT_USE权限）

公共前缀 `/api/v1/agent`。

- GET `/profile` → `{role,roleName,greeting,suggestions:string[],tools:Tool[],model:{configured,model,toolCalling,state}}`。
- POST `/chat` body `{message,history?:[{role,content}]}` → `{answer,suggestions,source,mode,results:ToolResult[],actions:Action[],traceId}`。
- POST `/query` body `{tool,arguments:{...}}` → ToolResult（仅只读工具）。
- POST `/actions/preview` body `{tool,arguments:{...}}` → Action（不执行写入）。
- GET `/actions` → Action[]（当前用户当前登录会话最近30条）。
- POST `/actions/{id}/confirm` body `{}` → Action，参数不允许在确认时修改。
- POST `/actions/{id}/cancel` body `{}` → Action。

`Tool={name,label,description,write,parameters}`，parameters为JSON schema object，属性带中文description，additionalProperties=false。
`ToolResult={tool,label,data,source,asOf,truncated}`。返回安全DTO/投影，不返回JPA实体；列表有上限并标明截断。模型上下文进一步脱敏和限长。
`Action={id,tool,title,summary,parameters,status,createdAt,expiresAt,result?,error?}`，status为PENDING/SUCCEEDED/CANCELLED/EXPIRED/FAILED；result为真实业务结果。

首批只读工具：`courses`、`bookings`、`training_plans`、`students`、`retention`、`leads`、`cash_summary`、`analytics`、`profile`。注册表按后端角色/权限及现有服务范围过滤，当前身份与门店永远由认证上下文确定。只读工具可有keyword/date/filter/venueId等明确定义的参数。
首批写工具：`book_course`（scheduleId，员工另需memberId，可选entitlementId），`cancel_booking`（bookingId），`book_trial`（leadId,expectedVersion,hostId,startsAt,endsAt,content），`training_draft`（memberId,title,goal,startDate,endDate,note,exercises）。工具schema仅含业务输入，幂等键由服务器生成。

## 执行与权限

新增V23 agent_action表。预览UUID绑定user、role、venue、登录sid（legacy绑定令牌hash和版本），有效10分钟，保存参数快照和可读摘要。确认时重取权限、门店/归属及版本；行锁保证并发重复确认至多执行一次。确认过的原动作返回原回执；过期、取消、身份变化均拒绝执行。写入通过现有业务Service，业务写与成功审计同事务；失败回滚后独立记录FAILED，不能只写成功文案。取消、预览和查询均不修改旧业务。

模型有工具白名单：仅返回工具选择，服务器解析并验证；写工具只生成预览，不自动确认。模型不能执行任意SQL、命令、网络请求或改变角色。每轮最多3次模型往返、合计6次工具调用，历史与响应有大小上限，超时进入明确的本地工具模式。模型不可用也可使用角色工具页，但不伪装模型正常。保留旧assistant接口兼容。

退款/付款/签课/结算不加入首批自动执行工具。教练只能保存草稿，不自动发布、签到或扣课；顾问记录体验安排，不伪造沟通和到店事实。

## 验证

红绿测试覆盖六角色工具可见性与服务端拒绝、跨用户/跨门店/跨登录确认、参数篡改、重复确认并发、过期取消、业务失败回滚、模型多步/坏JSON/未知工具/超限/断网、PWA敏感缓存隔离。隔离库HTTP验证三条真实业务写入；手机和桌面浏览器覆盖角色导航、工具表单、确认回执、登录切换及离线提示。应用实际本地前备份数据库/旧JAR并核对历史业务表及迁移字节，不向真实业务库写入测试数据。
