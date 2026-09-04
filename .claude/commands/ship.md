---
description: master 에 안전하게 커밋·푸시 (build 그린 확인 → commit → pull --rebase → push)
---

커밋 메시지: $ARGUMENTS

`CLAUDE.md` 의 Git 규칙(브랜치 없이 master 직행)을 실수 없이 실행한다.
각 단계에서 문제가 생기면 **즉시 멈추고** 무엇이 왜 막혔는지 보고한다. 절대 빨간불로 커밋하지 않는다.

1. `git status --short` — 커밋할 변경이 없으면 "변경 없음"이라 알리고 멈춘다.
2. `./gradlew build` — 실패하면 멈추고 에러를 보고한다. (현재 작업 트리가 그린인지 먼저 확인)
3. `git add -A` 후 커밋한다. 메시지는 위 `$ARGUMENTS`.
   - `$ARGUMENTS` 가 비어 있으면, 변경 내용을 보고 커밋 메시지를 직접 제안하고 사용자 확인을 받는다.
   - 커밋 메시지 끝에 세션 지침대로 `Co-Authored-By:` / `Claude-Session:` 를 붙인다.
4. `git pull --rebase origin master`
   - 충돌이 나면 `git rebase --abort` 하고 멈춘다. 다른 컴퓨터/세션의 변경과 겹쳤다는 뜻 — 사용자에게 어떤 파일인지 알리고 지시를 기다린다.
   - 원격에 새 커밋이 있었으면(리베이스가 뭔가 했으면) `./gradlew build` 를 한 번 더 돌려 그린 확인.
5. `git push origin master`
6. `git log --oneline -3` 로 결과를 확인하고 한 줄로 보고한다.

규칙 엔진(`rule/`) 을 건드린 변경이면, 4번 전에 `EligibilityEngineTest` 가 통과했는지 명시적으로 확인한다.
