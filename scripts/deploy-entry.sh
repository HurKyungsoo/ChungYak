#!/usr/bin/env bash
# 서버의 ~/deploy-entry.sh 로 설치되는 SSH 진입점 — 배포 키로 접속하면 이 스크립트만 실행된다.
#
# ~/.ssh/authorized_keys 에서 배포 키를 이렇게 제한해 둔다:
#   restrict,command="/home/ubuntu/deploy-entry.sh" ssh-ed25519 AAAA... github-actions-chungyak-deploy
# → 이 키로는 셸을 열 수도, 다른 명령을 실행할 수도 없다(키가 유출돼도 "배포"만 가능).
#
# 이 파일은 저장소 밖(홈 디렉터리)에 복사해 두고 쓴다. 저장소 안 deploy.sh 를 직접 호출하면
# `git pull` 이 실행 중인 스크립트를 바꿔 버릴 수 있어서다. 이 파일을 고치면 서버에서 다시 복사:
#   cp ~/ChungYak/scripts/deploy-entry.sh ~/deploy-entry.sh && chmod 700 ~/deploy-entry.sh
set -euo pipefail

cd /home/ubuntu/ChungYak
OLD="$(git rev-parse HEAD)"
git pull --ff-only -q < /dev/null

# 워크플로가 `ssh ... force` 로 부르면(수동 실행) 변경이 없어도 재빌드한다.
# SSH 가 원래 명령을 SSH_ORIGINAL_COMMAND 로 넘겨 준다 — 허용하는 값은 "force" 하나뿐.
if [ "${SSH_ORIGINAL_COMMAND:-}" = "force" ]; then
    export FORCE=1
fi

exec bash scripts/deploy.sh "$OLD"
