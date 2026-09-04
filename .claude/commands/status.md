---
description: 프로젝트 현황 한눈에 (git · 로드맵 미완 · 로컬 DB · 앱 상태)
---

다음을 조회해서 짧게 정리한다. 코드는 건드리지 않는다.

1. `git fetch origin -q` 후 `git log --oneline -5` + `git status --short` — 원격 대비 앞/뒤, 미커밋 변경.
2. `git log --oneline origin/master -1` — 로컬이 원격보다 뒤면 "pull 필요" 명시.
3. `docs/ROADMAP.md` + `docs/AI-ROADMAP.md` 에서 `⬜`(미완) 항목만 뽑아 나열.
4. 로컬 DB: `ls data/` — `.mv.db` 가 있으면, Flyway 도입 이후 것인지(V2 이상 적용됐는지)는 알 수 없으니 "부팅 실패 시 `data/` 삭제" 를 상기.
5. `jps` 로 `ChungyakApplication` 이 이미 떠 있는지 확인.
6. `ANTHROPIC_API_KEY` / `PUBLICDATA_SERVICE_KEY` 환경변수 설정 여부 (값은 출력하지 말 것, 설정 여부만).

정리 형식: 표나 불릿으로 5줄 내외. "다음에 뭘 하면 좋은지" 한 줄 추천으로 마무리.
