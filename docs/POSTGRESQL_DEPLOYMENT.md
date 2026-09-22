# PostgreSQL 生产部署与运维指南

本指南面向 Windows 环境。项目统一使用本机 PostgreSQL 18（默认端口 `5433`），应用端口为 `8080`，仅本机可访问的监控端口为 `9091`。

## 1. 一次性创建数据库

在项目根目录打开 PowerShell：

```powershell
.\scripts\setup-postgres.ps1
```

脚本会安全提示输入 PostgreSQL `postgres` 管理员密码和新的 `fitness_user` 密码，不会把密码写入项目。它会幂等创建或校正：

- 数据库：`fitness_db`
- 应用账号：`fitness_user`
- 数据库所有者和 `public` schema 权限

## 2. 启动生产环境

日常使用可直接双击项目根目录的 `start-production.bat`，它会同时启动 PostgreSQL 生产后端、前端并打开浏览器。

正式空库启动：

```powershell
.\scripts\start-production.ps1
```

脚本依次提示数据库密码、至少 32 位的 JWT 密钥和系统 `admin` 登录密码，然后构建并启动后端。Flyway 会自动执行 `V1` 至最新版本，Hibernate 只校验结构，不会自行修改生产表。

已有五类账号、课程演示时只需补齐业务样例数据，可执行一次：

```powershell
.\scripts\start-production.ps1 -SeedSampleData
```

该模式不会创建或修改账号密码，会幂等补充场馆、会员、课程、排课以及预约/签到/取消记录。写入完成后请停止进程，并用普通生产命令重新启动。`-SeedDemoData` 仅用于可随时清空的全新本地演示库，其统一密码为 `password`，不可用于正式库或公网环境。

## 3. 健康检查与指标

生产环境将管理接口绑定在 `127.0.0.1:9091`：

- 健康状态：`http://127.0.0.1:9091/actuator/health`
- 存活探针：`http://127.0.0.1:9091/actuator/health/liveness`
- 就绪探针：`http://127.0.0.1:9091/actuator/health/readiness`
- Prometheus：`http://127.0.0.1:9091/actuator/prometheus`

一次性检查应用、数据库和最近备份：

```powershell
.\scripts\check-health.ps1
```

持续观察：

```powershell
.\scripts\check-health.ps1 -Watch -IntervalSeconds 30
```

业务指标包括会员总数、门店总数、待参加预约、已签到人次和今日排课数。生产日志写入项目根目录的 `logs/fitness-backend.log`，单文件 20 MB，保留 30 个历史周期且总量不超过 1 GB。

Prometheus 指标名分别为 `fitness_members`、`fitness_venues`、`fitness_bookings_booked`、`fitness_bookings_attended` 和 `fitness_schedules_today`。

## 4. 备份

手动创建压缩备份：

```powershell
.\scripts\backup-postgres.ps1
```

备份默认保存到项目的 `backups` 目录，生成 PostgreSQL 自定义格式 `.dump` 和 SHA-256 校验文件，自动清理 14 天以前的旧备份。

无人值守每日备份前，需要把数据库密码设置为当前用户环境变量，然后注册计划任务：

```powershell
[Environment]::SetEnvironmentVariable('DB_PASSWORD', '你的数据库密码', 'User')
.\scripts\register-backup-task.ps1 -DailyAt '02:00'
```

如果不希望密码保存在用户环境变量中，可不注册计划任务，仅使用交互式手动备份。

## 5. 恢复

恢复到空数据库：

```powershell
.\scripts\restore-postgres.ps1 -BackupFile .\backups\fitness_db-时间.dump
```

覆盖已有对象时使用 `-Clean`。脚本会要求输入 `RESTORE` 二次确认：

```powershell
.\scripts\restore-postgres.ps1 -BackupFile .\backups\fitness_db-时间.dump -Clean
```

恢复前应停止后端，并先保存一份当前数据库备份。

## 6. 非交互式服务器部署

服务器或 CI 环境应通过环境变量传入秘密，参考根目录 `.env.production.example`。不要把真实 `.env`、密码或 JWT 密钥提交到仓库。

必需变量：`DB_PASSWORD`、`JWT_SECRET`、`APP_ADMIN_PASSWORD`。可选变量包括 `DB_URL`、`DB_USERNAME`、`DB_POOL_MAX`、`MANAGEMENT_ADDRESS`、`MANAGEMENT_PORT` 和 `LOG_FILE`。

## 7. 已完成的部署验收

项目已在本机 PostgreSQL 18.6 正式实例完成 Flyway `V1`–`V6` 全量迁移、五角色账号闭环、纯生产模式启动、健康与 Prometheus 指标、管理员安全初始化、压缩备份和 SHA-256 校验。正式环境使用 `5433` 端口，并已注册每日 `02:00` 自动备份任务。
