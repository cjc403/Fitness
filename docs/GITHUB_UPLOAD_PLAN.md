# 上传到 GitHub 的准备方案（待确认后执行）

状态：**仅调查与方案，尚未推送、尚未修改任何文件。**
账号：`CJC403`｜目标仓库：`CJC403/健身房`（**已存在，且是私有仓**）

## 一、可行性结论：可以推

| 检查项 | 结果 |
| --- | --- |
| 仓库体积 | 已跟踪 **309 个文件 / 1.38 MB**；无 >200KB 文件；无 >100MB 文件（GitHub 上限） |
| 本地 `.git` | 0.6 MB（此前误把 `.superpowers/` 暂存导致膨胀到 199.9MB，已 `gc` 清理） |
| `.gitignore` | 已排除 `target/`、`node_modules/`、`logs/`、`backups/`、`.tools/`、`.superpowers/`、`*.rpm`、`*.tar.gz` |
| 生产配置 | `application-prod.yml` 用 `${DB_PASSWORD}` 无默认值 ✅ 干净 |
| 远端现状 | 已有 1 条提交，内容只有一行 README「健身房」 |
| 本地 README | 213 行完整项目说明（简介/已完成功能/技术栈/结构/快速开始/演示账号/验证/API 规范），**明显优于远端那条** |
| **GitHub 凭据** | **本机一个都没有**：`credential.helper` 空、无 `~/.git-credentials`、Windows 凭据管理器 0 条、Git Credential Manager 未安装 |

## 二、推之前必须处理：一处真实数据库口令

`docs/开发指导手册.md` 中存在：

```sql
CREATE USER fitness_user WITH PASSWORD '<你的数据库口令>';
```

这**就是** `fitness_user` 的口令（本地 5433 正式库用的正是这个用户）。仓库虽然是私有的，
但只要将来改为公开、或把仓库分享出去，就等于泄露。

**建议改法**：替换为占位符，例如

```sql
CREATE USER fitness_user WITH PASSWORD '<见 DB_PASSWORD 环境变量>';
```

> `.superpowers/` 目录里有同样内容，但它已被 `.gitignore` 排除，不会进入仓库 ✅

**严重性较低的第二处**：`application-dev.yml` 里 dev 库口令的默认值
（数据源是 `localhost:5432/fitness_db_dev`、用户 `dev_user`，**不是**你的主库 `fitness_db`）。
可以不改，也可以一并参数化成 `${DB_PASSWORD:}`。

## 三、建议顺带做的两件事

### 1. README 刷新

现有 README 停在「私教闭环」阶段，缺这些已上线能力：
V9 精确价值账与退款、会员自助购买/退款、私教消课自检、商品删除/下架、排课与教练账号绑定、
站内通知、门禁一次性二维码。建议补上并加一节"测试与验证"（后端 85 个测试怎么跑）。

### 2. 加 GitHub Actions（CI）

- 后端：`actions/setup-java@v4`（temurin 17）+ `mvn -B clean package` —— 测试用内存 H2，**不需要数据库**，CI 可跑通。
- 前端：`actions/setup-node@v4` + `npm ci && npm run build` + `node --test`。
- 仓库里没有 `mvnw`，所以用 runner 自带的 Maven 即可（不必补 wrapper）。
- 价值：以后"忘了跑测试"也会被拦下来。

### 3. 可选：仓库改名为 `fitchain`

中文仓库名在 URL 与命令行里需要转义。改名字在 GitHub 设置里点一下即可，URL 会变。

## 四、远端那条「初始提交」怎么处理（二选一）

远端当前历史：`初始提交（只有一行 README）`
本地当前历史：`fc31b2a 基线提交` → `02b5d4b 第二轮修复`

**方案 A（推荐）：接在它后面，不丢任何东西**

```bash
git remote add origin https://github.com/CJC403/健身房.git
git fetch origin
git rebase origin/main        # 冲突只在 README.md：采用本地版本
# 若报冲突：git checkout <本地提交> -- README.md && git add README.md && git rebase --continue
git push -u origin main
```

结果：历史 = 远端初始提交 → 基线 → 修复；两个 README 取本地那份。

**方案 B：覆盖远端**

```bash
git remote add origin https://github.com/CJC403/健身房.git
git push -u origin main --force
```

结果：历史从本地基线开始，**远端那条一行 README 的提交消失**（不可逆）。

## 五、最后一步的凭据问题（两种做法）

本机没有任何已保存的 GitHub 凭据，所以推送这一步必须有凭据参与。

**做法 1（更安全，推荐）**：我做完清理、README、CI、远程配置、本地提交，然后给你一条 `git push` 命令，
**你在自己的终端执行**。凭据完全不经过我，也不会进入对话记录。

**做法 2（我全程代劳）**：你提供一个**细粒度 PAT**，权限只勾该仓库的 `Contents: Read and write`。
注意：令牌会留在对话记录里，**用完请立刻到 GitHub 吊销**。我不会把它写进任何文件或日志，
也不会存进 `.git/config`（只用一次性凭据，不落盘）。

## 六、需要你确认的清单

| # | 事项 | 我的建议 |
| --- | --- | --- |
| 1 | 远端初始提交：接在后面 / 覆盖 | **接在后面**（不丢东西） |
| 2 | 口令清理：只改文档 / 两处都改 / 不改 | **只改文档那处** |
| 3 | README 刷新 | **做** |
| 4 | 加 CI | **做** |
| 5 | 仓库改名 `fitchain` | 可选，你定 |
| 6 | 最后一步：你执行 push / 给我 PAT | **你执行** |

一句话回"按你的建议来"即可，当然也可以逐条改。
