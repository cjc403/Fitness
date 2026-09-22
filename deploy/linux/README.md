# FitChain Linux deployment assets

These files are the reproducible server-side configuration used for the Alibaba Cloud deployment.

- `fitchain.service`: Java 17 backend managed by systemd with restart-on-failure and a bounded heap.
- `fitchain-nginx.conf`: Nginx serves the Vue SPA and reverse-proxies `/api` to the private backend.
- `fitchain-backup.*`: verified PostgreSQL custom-format backup every day at about 02:00, retained for 14 days.
- `fitchain-healthcheck.sh`: verifies the local backend actuator and public web entry point.
- `fitchain-smoke-test.sh`: performs an encrypted-server-side administrator login test without printing credentials or tokens.
- `fitchain-watchdog.*`: checks the stack every five minutes and restarts it when health checks fail.
- `install.sh`: idempotent first-deployment script used after artifacts are uploaded.

Secrets are intentionally excluded. The server keeps them in `/etc/fitchain/fitchain.env` with mode `0600`.

## Current Alibaba Cloud deployment

- Public entry: `http://101.37.18.88/login`
- Backend: `127.0.0.1:8080` (Nginx proxy only)
- PostgreSQL 18: `127.0.0.1:5432` (server-local only)
- Management health endpoint: `127.0.0.1:9091` (server-local only)
- Application service: `systemctl status fitchain`
- Database service: `systemctl status postgresql-18`
- Reverse proxy: `systemctl status nginx`
- Full smoke test: `sudo /usr/local/sbin/fitchain-smoke-test`
- Manual backup: `sudo systemctl start fitchain-backup.service`
- Application logs: `journalctl -u fitchain -f` and `/var/log/fitchain/fitness-backend.log`

## 2026-09-17 私教闭环发布

`release-private-training.sh` 是本次固定版本发布脚本，要求制品 SHA256 与预发布验收标记完全一致，先保存数据库及旧应用，再切换前端目录和后端 JAR。失败只回滚应用，不自动覆盖正在使用的数据库；新增 V8 结构保留。

预发布使用独立数据库 `fitchain_stage_pt_20260917` 与本机端口 18080，业务写验收脚本禁止生产数据库及 8080 端口。生产只读验收不会买课包、签课、退款或改排课。

真实登录验收使用 `login-five-roles.py`，密码仅通过标准输入临时传递，不写入脚本。已重置账号须先完成本人改密：验收会明确识别此保护状态，而不是把它当作权限配置错误。`reset-member-login.py` 仅用于本次用户明确授权的 `member` 密码重置，不能纳入日常启动或自动发布。

注意：旧 smoke test 使用环境文件里的初始化管理员密码。用户修改实际密码后，旧脚本可能报登录失败，并不意味着服务宕机；本次发布改用真实当前密码登录验收和无业务写入的接口检查。不要为让旧脚本变绿而重置已正常的账号。
