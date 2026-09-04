#!/usr/bin/env bash
# 상태줄: 브랜치 · 미커밋(●) · 원격 대비 앞(↑)/뒤(↓) · 모델
# git fetch 는 하지 않는다(자주 호출됨). SessionStart 훅이 fetch 를 담당.
input=$(cat)

_field() {  # $1 = json key. jq 없어도 동작하도록 grep/sed 폴백.
  if command -v jq >/dev/null 2>&1; then
    printf '%s' "$input" | jq -r --arg k "$1" 'getpath($k | split(".")) // empty' 2>/dev/null
  else
    printf '%s' "$input" | grep -o "\"${1##*.}\"[[:space:]]*:[[:space:]]*\"[^\"]*\"" | head -1 | sed 's/.*:[[:space:]]*"\([^"]*\)".*/\1/'
  fi
}

dir=$(_field 'workspace.current_dir'); [ -z "$dir" ] && dir=$(_field 'cwd')
[ -n "$dir" ] && cd "$dir" 2>/dev/null

model=$(_field 'model.display_name')

branch=$(git branch --show-current 2>/dev/null)
[ -z "$branch" ] && branch=$(git rev-parse --short HEAD 2>/dev/null)

out="⎇ ${branch:-?}"

if git rev-parse --abbrev-ref '@{u}' >/dev/null 2>&1; then
  a=$(git rev-list --count '@{u}..HEAD' 2>/dev/null || echo 0)
  b=$(git rev-list --count 'HEAD..@{u}' 2>/dev/null || echo 0)
  [ "${a:-0}" != "0" ] && out="$out ↑$a"
  [ "${b:-0}" != "0" ] && out="$out ↓$b"
fi

[ -n "$(git status --porcelain 2>/dev/null | head -1)" ] && out="$out ●"

[ -n "$model" ] && out="$out  ·  $model"

printf '%s' "$out"
