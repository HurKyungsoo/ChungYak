#!/usr/bin/env bash
# MariaDB 일일 백업 (ROADMAP D7) — 서버 cron 이 매일 부른다. 크론 등록은 deploy.sh 가 한다(멱등).
#
#   ~/backups/chungyak-YYYYMMDD-HHMM.sql.gz   를 만들고 14일 지난 것은 지운다.
#
# 알림 구독(이메일·소득·자산 조건)이 DB 에만 있어 볼륨이 날아가면 되살릴 길이 없다.
# 보관 14일은 개인정보 처리방침 5절("백업본은 최대 14일 보관")과 맞춘다 — 바꾸면 처리방침도 고칠 것.
# 서버 밖 사본은 Lightsail 스냅샷(콘솔 자동 스냅샷)으로 둔다 — DEPLOYMENT.md "백업".
set -euo pipefail

cd "$(dirname "$0")/.."
DIR="${BACKUP_DIR:-$HOME/backups}"
KEEP_DAYS="${BACKUP_KEEP_DAYS:-14}"
mkdir -p "$DIR"
chmod 700 "$DIR"

STAMP="$(TZ=Asia/Seoul date +%Y%m%d-%H%M)"
OUT="$DIR/chungyak-$STAMP.sql.gz"
TMP="$OUT.part"

# 비밀번호는 컨테이너 안의 환경변수로만 쓴다(명령줄·이 스크립트에 남기지 않는다).
# --single-transaction: InnoDB 를 잠그지 않고 일관된 스냅샷으로 덤프 — 앱이 돌고 있어도 된다.
sudo -n docker compose exec -T db sh -c \
    'exec mariadb-dump -uroot -p"$MARIADB_ROOT_PASSWORD" --single-transaction --routines --default-character-set=utf8mb4 "$MARIADB_DATABASE"' \
    < /dev/null | gzip -9 > "$TMP"

# 덤프가 중간에 끊기면 마지막 줄 "-- Dump completed" 가 없다 — 깨진 파일을 정상 백업처럼 남기지 않는다.
if ! gzip -dc "$TMP" | tail -n 1 | grep -q 'Dump completed'; then
    rm -f "$TMP"
    echo "[backup] 덤프가 완료되지 않았습니다" >&2
    exit 1
fi
mv "$TMP" "$OUT"
chmod 600 "$OUT"

find "$DIR" -maxdepth 1 -name 'chungyak-*.sql.gz' -mtime +"$KEEP_DAYS" -delete
echo "[backup] $(basename "$OUT") $(du -h "$OUT" | cut -f1) · 보관 $(ls "$DIR"/chungyak-*.sql.gz | wc -l)개"
