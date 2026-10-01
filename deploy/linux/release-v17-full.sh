#!/usr/bin/env bash
# V17 全量发布：把线上从 V8（2026-09-17 私教闭环）升到与本地一致的 V17。
#
# 与既有 release-*.sh 同一套路：
#   ① 停看门狗（避免它在切换过程中把服务拉起来）
#   ② 停应用 → 保存旧 jar → 备份数据库（项目自带备份脚本）
#   ③ 铺前端到新发布目录，原子切换 current 软链
#   ④ 换 jar → 启动（此时 Flyway 会执行 V9→V17）
#   ⑤ 校验：健康检查 + 数据库迁移版本 + 非 2xx 即回滚应用（**数据库不回滚**）
#
# 幂等：重复执行只会重新走一遍同样的步骤；已存在的备份目录不会被覆盖删除。
set -Eeuo pipefail

RELEASE=20261001-v17
UPLOAD=/root/fitchain-release-20261001-v17
BACKUP=/var/lib/fitchain/releases/$RELEASE
FRONT=/var/www/fitchain-releases/$RELEASE
LIVE_JAR=/opt/fitchain/fitness-backend.jar
SERVICE=fitchain
PG=/usr/pgsql-18/bin

log() { echo "[$(date +%H:%M:%S)] $*"; }

log "① 停看门狗"
systemctl stop fitchain-watchdog.timer 2>/dev/null || true
systemctl stop fitchain-watchdog.service 2>/dev/null || true

log "② 停应用 → 保存旧 jar → 备份数据库"
systemctl stop "$SERVICE"
install -d -m 0700 "$BACKUP"
cp -p "$LIVE_JAR" "$BACKUP/fitness-backend.jar"
# 项目自带的备份服务：先停应用再 dump，保证一致性
systemctl start fitchain-backup.service || log "   (备份服务返回非零，请检查 /var/log/fitchain)"
ls -1t /var/lib/fitchain/backups/*.dump 2>/dev/null | head -1 | sed 's/^/   最近备份: /' || true

# 失败时把旧应用放回去并启动（数据库不动）
rollback() {
  log "!! 校验未通过，回滚应用（数据库保持已迁移状态，不自动回滚）"
  install -m 0640 -o fitchain -g fitchain "$BACKUP/fitness-backend.jar" "$LIVE_JAR"
  systemctl start "$SERVICE"
  systemctl start fitchain-watchdog.timer 2>/dev/null || true
  log "   已回滚到旧版本并启动"
}
trap 'rollback' ERR

log "③ 铺前端并原子切换 current 软链"
install -d -m 0755 "$FRONT"
tar -xzf "$UPLOAD/frontend-dist.tar.gz" -C "$FRONT"
ln -sfn "$FRONT" /var/www/fitchain-next-link
mv -Tf /var/www/fitchain-next-link /var/www/fitchain-current

log "④ 换 jar 并启动（Flyway 将执行 V9→V17）"
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
[ "$VER" = "17" ] || { log "数据库版本不是 V17，实际 V$VER"; false; }

PUB=$(curl -s -o /dev/null -w '%{http_code}' -m 8 http://127.0.0.1/login || true)
echo "   本机访问登录页 HTTP: $PUB"

log "⑥ 恢复看门狗"
systemctl start fitchain-watchdog.timer 2>/dev/null || true
trap - ERR

log "发布会话完成：$RELEASE"
log "  旧版本备份: $BACKUP/fitness-backend.jar"
log "  前端目录:   $FRONT（current 已指向它）"
log "  回滚命令:   install -m 0640 -o fitchain -g fitchain $BACKUP/fitness-backend.jar $LIVE_JAR && systemctl start $SERVICE"
