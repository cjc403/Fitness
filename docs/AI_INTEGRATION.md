# DeepSeek 智能客服与课程推荐

2026-10-16：六角色共用的默认模型已统一为 **DeepSeek-V4.1-Flash**，官方 API 标识为 `deepseek-flash`。这是当前网页 Agent、业务问答和课程推荐共同使用的配置；显式环境变量仍可覆盖。官方旧名 `deepseek-v4-flash` 目前也路由到 V4.1 Flash。本项目当前 Agent 输入仍是文字、DOM 与工具反馈；此次仅切换默认模型，未把网页截图输入链路接入产品。独立合成图片的视觉接口验证不等于项目已启用截图理解。

依据：[首次调用 API](https://api-docs.deepseek.com/zh-cn/)、[图像理解](https://api-docs.deepseek.com/zh-cn/guides/vision/)。

## 功能说明

系统采用“确定性业务规则 + 大语言模型解释”的组合方案：

- 智能客服会携带当前角色、本门店未来 7 天排课摘要和最近 6 轮对话调用 DeepSeek。
- 所有手机号、身份证号和邮箱在发送前自动脱敏。
- DeepSeek 未配置、超时、报错或触发熔断时，客服自动切换到本地知识库，不影响核心业务。
- 课程推荐先由本地算法根据训练目标、历史偏好、时间、容量和新鲜度计算分数，再由 DeepSeek 解释推荐结果。大模型不参与权限判断，也不能修改业务数据。

## 安全配置 API Key

在项目根目录打开 PowerShell，运行：

```powershell
.\scripts\configure-deepseek.ps1
```

在隐藏输入提示中粘贴 DeepSeek API Key 并按回车。脚本仅将密钥保存到当前 Windows 用户环境变量 `DEEPSEEK_API_KEY`，不会写入源码或 `.env` 文件。随后重启生产后端：

```powershell
.\scripts\start-production.ps1
```

默认模型为 `deepseek-flash`（DeepSeek-V4.1-Flash）。如需指定其他已开通模型：

```powershell
.\scripts\configure-deepseek.ps1 -Model deepseek-v4-pro
```

## 配置项

| 环境变量 | 默认值 | 作用 |
| --- | --- | --- |
| `AI_ENABLED` | `true` | 是否允许调用大模型 |
| `DEEPSEEK_BASE_URL` | `https://api.deepseek.com` | DeepSeek API 地址 |
| `DEEPSEEK_API_KEY` | 空 | API Key，空时自动使用本地知识库 |
| `DEEPSEEK_MODEL` | `deepseek-flash` | DeepSeek-V4.1-Flash 的官方 API 名称 |
| `AI_MAX_TOKENS` | `700` | 单次回答最大输出 token |
| `AI_REQUESTS_PER_MINUTE` | `10` | 每用户每分钟调用上限 |
| `AI_CIRCUIT_FAILURE_THRESHOLD` | `3` | 连续失败后开启熔断的阈值 |
| `AI_CIRCUIT_OPEN_SECONDS` | `60` | 熔断持续秒数 |

## 接口与监控

- `GET /api/v1/admin/assistant/status`：查看是否已配置模型。
- `POST /api/v1/admin/assistant/chat`：智能客服，支持最多 6 条历史消息。
- `POST /api/v1/member/recommendations`：会员课程推荐。
- Prometheus 指标 `fitness_ai_requests_total{outcome=...}`：统计成功、失败与降级次数。

API Key 不会返回给前端，也不会写入日志。生产环境建议定期轮换密钥，并在 DeepSeek 控制台设置合理的余额与限额。
