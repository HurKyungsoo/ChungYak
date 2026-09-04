#!/usr/bin/env bash
# SessionStart: 원격 master 를 조용히 fetch 하고, 로컬이 뒤처졌으면 경고를 컨텍스트에 넣는다.
# 여러 컴퓨터/세션에서 작업하므로 오래된 base 위에 쌓는 사고를 방지한다.
cd "${CLAUDE_PROJECT_DIR:-.}" 2>/dev/null || exit 0
git rev-parse --git-dir >/dev/null 2>&1 || exit 0

git fetch origin -q 2>/dev/null || exit 0

behind=$(git rev-list --count HEAD..origin/master 2>/dev/null || echo 0)
ahead=$(git rev-list --count origin/master..HEAD 2>/dev/null || echo 0)

msg=""
[ "${behind:-0}" -gt 0 ] && msg="⚠️ origin/master가 ${behind}커밋 앞섰습니다 — 작업 전 'git pull --rebase' 필요. "
[ "${ahead:-0}" -gt 0 ] && msg="${msg}로컬이 origin/master보다 ${ahead}커밋 앞섰습니다(미푸시). "

[ -n "$msg" ] && printf '%s\n' "$msg"
exit 0
