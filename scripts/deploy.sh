#!/usr/bin/env bash
# 서버에서 실행되는 배포 스크립트 — GitHub Actions 의 deploy 잡이 SSH 로 부른다(deploy-entry.sh 경유).
#
# 호출 전에 entry 스크립트가 `git pull --ff-only` 를 끝내 두고, 이 스크립트에는 pull 전 커밋($1)을 넘긴다.
# (스크립트가 자기 자신을 pull 로 바꾸면 bash 가 실행 중인 파일을 읽다가 꼬일 수 있어 pull 은 밖에서 한다.)
#
# 하는 일:
#   1) 앱에 영향 없는 변경(문서·CI·이 스크립트)만이면 재빌드하지 않는다. FORCE=1 이면 무조건 배포.
#   2) 공고 수집이 진행 중이면 끝날 때까지 기다린다 — 재시작하면 수집이 끊긴다(DEPLOYMENT.md).
#   3) docker compose up -d --build → app 컨테이너가 healthy 가 될 때까지 기다린다.
#   4) 실패하면 앱 로그를 보여 주고 비정상 종료 — Actions 잡이 빨갛게 되어 알림이 온다.
#
# 롤백은 자동으로 하지 않는다. Flyway 마이그레이션은 되돌릴 수 없어, 코드만 이전 커밋으로 돌리면
# 새 스키마와 어긋날 수 있다. 실패 시 원인을 보고 수정 커밋을 push 하거나 서버에서 수동 복구한다.
set -euo pipefail
trap '' HUP   # Actions 러너와 SSH 가 끊겨도 배포가 중간에 죽지 않게

cd "$(dirname "$0")/.."
OLD="${1:-}"
NEW="$(git rev-parse HEAD)"
FORCE="${FORCE:-0}"
DC="sudo -n docker compose"

log() { echo "[deploy $(TZ=Asia/Seoul date +%H:%M:%S)] $*"; }

log "커밋 ${OLD:0:7} -> ${NEW:0:7}"

# 0) DB 일일 백업 크론 — 서버 설정을 손으로 하지 않고 이 저장소가 갖는다(멱등: 이미 있으면 그대로).
#    03:30 KST = 18:30 UTC (서버 시계는 UTC). 04:00 공고 수집 전에 끝난다. 재빌드 생략 배포에서도 돈다.
BACKUP_CRON="30 18 * * * $PWD/scripts/backup-db.sh >> $HOME/backups/backup.log 2>&1"
if ! crontab -l 2>/dev/null | grep -qF 'scripts/backup-db.sh'; then
    mkdir -p "$HOME/backups" && chmod 700 "$HOME/backups"
    (crontab -l 2>/dev/null || true; echo "$BACKUP_CRON") | crontab -
    log "DB 백업 크론 등록 (매일 03:30 KST)"
fi
chmod +x scripts/backup-db.sh 2>/dev/null || true
# 백업이 하나도 없으면(처음 등록) 바로 한 번 떠 둔다 — 이번 배포 로그에서 동작을 확인할 수 있게
if ! ls "$HOME"/backups/chungyak-*.sql.gz > /dev/null 2>&1; then
    log "첫 DB 백업 실행"
    scripts/backup-db.sh || log "첫 백업 실패 — 배포는 계속합니다(로그 확인 필요)"
fi

# 1) 앱에 영향 없는 변경만인가
if [ "$FORCE" != "1" ] && [ -n "$OLD" ] && [ "$OLD" != "$NEW" ]; then
    APP_CHANGES="$(git diff --name-only "$OLD" "$NEW" | grep -Ev '(^docs/|^\.github/|^scripts/|\.md$)' || true)"
    if [ -z "$APP_CHANGES" ]; then
        log "문서·CI·스크립트만 바뀌어 재배포를 생략합니다."
        exit 0
    fi
elif [ "$FORCE" != "1" ] && [ "$OLD" = "$NEW" ]; then
    log "새 커밋이 없습니다. 컨테이너가 떠 있는지만 확인합니다."
    $DC up -d < /dev/null
    exit 0
fi

# 2) 수집 진행 중이면 대기 (최대 30분)
sync_running() {
    # 수동 수집: 마지막으로 보인 것이 "요청 수신"이면 아직 안 끝난 것
    local last
    last="$($DC logs app --since 90m 2>/dev/null < /dev/null \
        | grep -E '수동 동기화 요청 수신|전체 동기화 완료|공고 수집 실패' | tail -1 || true)"
    case "$last" in *"수동 동기화 요청 수신"*) return 0 ;; esac
    # 정기 수집: 매일 04:00 KST 시작 — 04:00~04:50 은 수집 창으로 본다
    local hm
    hm="$(TZ=Asia/Seoul date +%H%M)"
    [ "$hm" -ge 0400 ] && [ "$hm" -lt 0450 ] && return 0
    return 1
}

waited=0
while sync_running; do
    if [ "$waited" -ge 1800 ]; then
        log "수집이 30분 넘게 끝나지 않아 배포를 보류합니다. 끝난 뒤 Actions 에서 Run workflow 로 다시 배포하세요."
        exit 1
    fi
    log "공고 수집 진행 중 — 30초 뒤 다시 확인 (${waited}s 대기)"
    sleep 30
    waited=$((waited + 30))
done

# 3) 빌드·기동
log "docker compose up -d --build"
$DC up -d --build < /dev/null

log "app 이 healthy 가 될 때까지 대기 (최대 4분)"
for i in $(seq 1 48); do
    cid="$($DC ps -q app < /dev/null)"
    status="$(sudo -n docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$cid" 2>/dev/null || echo unknown)"
    if [ "$status" = "healthy" ]; then
        log "배포 완료 — app healthy"
        sudo -n docker image prune -f > /dev/null 2>&1 || true   # 빌드마다 쌓이는 이전 이미지 정리(dangling 만)
        exit 0
    fi
    sleep 5
done

log "배포 실패 — app 이 4분 안에 healthy 가 되지 않았습니다. 최근 로그:"
$DC logs app --tail 60 < /dev/null 2>&1 | cut -c1-240
exit 1
