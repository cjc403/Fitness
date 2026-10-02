#!/usr/bin/env bash
# V17 全量发布：把线上从 V8（2026-09-17 私教闭环）升到与本地一致的 V17。
#
# 步骤：
#   ⓪ 校验上传制品 sha256
#   ① 停看门狗（避免它在切换过程中把服务拉起来）
#   ② 停应用 → 存旧 jar + 记下旧前端软链 → 备份数据库（**备份失败即中止**）
#   ③ 铺前端到新发布目录，原子切换 current 软链
#   ④ 换 jar → 启动（此时 Flyway 执行 V9→V17）
#   ⑤ 校验：健康检查 + 数据库迁移版本 + 登录页 200；任一不过 → **回滚应用与前端**（数据库不回滚）
#
# 相对首版（2026-10-01）的改进 —— 由 Codex 复核指出，2026-10-02 WorkBuddy 修正：
#   - 备份失败不再"记条日志继续跑"，而是立刻中止并恢复应用
#   - 备份后校验确实产生了**新鲜**的 dump（默认 10 分钟内），避免"无备份就迁移"
#   - 回滚同时恢复**前端软链**，不再只回滚后端
#   - 发布前校验制品 sha256
#   - 兼容前端压缩包内含 dist/ 的打包方式（自动拍平）
set -Eeuo pipefail

RELEASE=20261001-v17
TARGET_VERSION=17                 # 本脚本要求迁移到的版本；换成别的版本发布时同步改这里
UPLOAD=/root/fitchain-release-20261001-v17
BACKUP=/var/lib/fitchain/releases/$RELEASE
FRONT=/var/www/fitchain-releases/$RELEASE
CURRENT=/var/www/fitchain-current
LIVE_JAR=/opt/fitchain/fitness-backend.jar
SERVICE=fitchain
PG=/usr/pgsql-18/bin
BACKUP_FRESH_MINUTES=10

log() { echo "[$(date +%H:%M:%S)] $*"; }

# 回滚：恢复旧 jar 与旧前端软链并启动（数据库保持已迁移状态，**不自动回滚库**）
rollback() {
  log "!! 校验未通过，回滚应用与前端（数据库保持已迁移状态，不自动回滚）"
  if [ -f "$BACKUP/fitness-backend.jar" ]; then
    install -m 0640 -o fitchain -g fitchain "$BACKUP/fitness-backend.jar" "$LIVE_JAR" || true
  fi
  if [ -n "${OLD_FRONT:-}" ] && [ -d "${OLD_FRONT:-}" ]; then
    ln -sfn "$OLD_FRONT" /var/www/fitchain-next-link
    mv -Tf /var/www/fitchain-next-link "$CURRENT"
    log "   前端已回滚到 $OLD_FRONT"
  fi
  systemctl start "$SERVICE" || true
  systemctl start fitchain-watchdog.timer 2>/dev/null || true
  log "   已回滚并启动"
}

# 早期失败（还没换制品）时也要把服务恢复起来
abort_early() {
  log "!! $1 —— 中止发布，并把应用恢复起来"
  systemctl start "$SERVICE" || true
  systemctl start fitchain-watchdog.timer 2>/dev/null || true
  exit 1
}

log "⓪ 校验上传制品的 sha256"
if [ -f "$UPLOAD/SHA256SUMS" ]; then
  ( cd "$UPLOAD" && sha256sum -c SHA256SUMS ) || abort_early "制品校验和不匹配"
else
  log "   (未找到 SHA256SUMS，跳过校验)"
fi

log "① 停看门狗"
systemctl stop fitchain-watchdog.timer 2>/dev/null || true
systemctl stop fitchain-watchdog.service 2>/dev/null || true

log "② 停应用 → 存旧 jar + 记旧前端 → 备份数据库"
OLD_FRONT="$(readlink -f "$CURRENT" 2>/dev/null || true)"
[ -n "$OLD_FRONT" ] && log "   当前前端目录: $OLD_FRONT"
systemctl stop "$SERVICE"
install -d -m 0700 "$BACKUP"
cp -p "$LIVE_JAR" "$BACKUP/fitness-backend.jar"

# 备份失败必须中止（首版只记日志继续，存在"没有备份就执行迁移"的风险）
systemctl start fitchain-backup.service || abort_early "数据库备份服务返回非零，请查 journalctl -u fitchain-backup"

# 校验确实产生了新鲜备份
NEWEST_DUMP="$(ls -1t /var/lib/fitchain/backups/*.dump 2>/dev/null | head -1 || true)"
[ -n "$NEWEST_DUMP" ] || abort_early "没有找到任何数据库备份文件"
if [ -n "$(find "$NEWEST_DUMP" -mmin +$BACKUP_FRESH_MINUTES 2>/dev/null)" ]; then
  abort_early "最新备份 $NEWEST_DUMP 超过 $BACKUP_FRESH_MINUTES 分钟，疑似未真正生成"
fi
log "   备份就绪: $NEWEST_DUMP"

trap 'rollback' ERR

log "③ 铺前端并原子切换 current 软链"
install -d -m 0755 "$FRONT"
tar -xzf "$UPLOAD/frontend-dist.tar.gz" -C "$FRONT"
if [ -d "$FRONT/dist" ] && [ ! -f "$FRONT/index.html" ]; then
  mv "$FRONT/dist"/* "$FRONT"/ && rmdir "$FRONT/dist"
  log "   (已拍平压缩包内的 dist 目录)"
fi
ln -sfn "$FRONT" /var/www/fitchain-next-link
mv -Tf /var/www/fitchain-next-link "$CURRENT"

log "④ 换 jar 并启动（Flyway 将执行 V9→V$TARGET_VERSION）"
install -m 0640 -o fitchain -g fitchain "$UPLOAD/fitness-backend.jar" "$LIVE_JAR"
systemctl start "$SERVICE"

log "⑤ 校验"
for i in $(seq 1 60); do
  if curl -sf -m 2 http://127.0.0.1:9091/actuator/health >/dev/null 2>&1; then break; fi
  sleep 3
done
HEALTH=$(curl -s -m 5 http://127.0.0.1:9091/actuator/health || true)
echo "   健康检查: $HEALTH"
echo "$HEALTH" | grep -q '"status":"UP"' || { log "健康检查未通过"; false; }

DB=$(sudo -u postgres psql -lqt | awk -F'|' '{print $1}' | tr -d ' ' | grep -E '^fitness_db$' | head -1)
VER=$(sudo -u postgres psql -tAc "select version from flyway_schema_history where success order by installed_rank desc limit 1" -d "$DB")
echo "   数据库迁移版本: V$VER"
[ "$VER" = "$TARGET_VERSION" ] || { log "数据库版本不是 V$TARGET_VERSION，实际 V$VER"; false; }

LOGIN=$(curl -s -o /dev/null -w '%{http_code}' -m 8 http://127.0.0.1/login || true)
echo "   本机访问登录页 HTTP: $LOGIN"
[ "$LOGIN" = "200" ] || { log "登录页返回 $LOGIN"; false; }

log "⑥ 恢复看门狗"
systemctl start fitchain-watchdog.timer 2>/dev/null || true
trap - ERR

log "发布会话完成：$RELEASE（目标版本 V$TARGET_VERSION）"
log "  旧版本备份: $BACKUP/fitness-backend.jar"
log "  旧前端目录: ${OLD_FRONT:-（未记录）}"
log "  前端目录:   $FRONT（current 已指向它）"
log "  回滚命令:   install -m 0640 -o fitchain -g fitchain $BACKUP/fitness-backend.jar $LIVE_JAR && systemctl start $SERVICE"
