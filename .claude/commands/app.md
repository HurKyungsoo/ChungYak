---
description: 앱 띄우고 주요 화면 확인 (로컬 DB 이슈 자동 처리)
---

이 프로젝트 앱을 로컬에서 띄우고 정상인지 확인한다. $ARGUMENTS 가 있으면 그 화면/기능에 집중.

1. 이미 떠 있으면(`jps` 에 `ChungyakApplication`) 재사용. 아니면:
   - `./gradlew bootRun` 을 백그라운드로 실행.
   - 부팅 로그에 `Found non-empty schema(s)` (pre-Flyway DB 이슈)가 보이면
     → `data/chungyak.mv.db` / `data/chungyak.trace.db` 삭제하고 재시도. (이건 자동 처리해도 됨 — 로컬 개발 DB일 뿐)
   - `APPLICATION FAILED` 면 로그를 보고 원인 보고 후 멈춤.
2. 뜨면(`curl -s -m3 http://localhost:8080/announcements` 200) 주요 경로 상태코드 확인:
   `/announcements`, `/announcements/{첫 ID}`, `/announcements/{ID}/eligibility`, `/general-supply`.
   Flyway 로그(`Successfully applied N migrations`)도 확인.
3. `$ARGUMENTS` 가 특정 화면이면 브라우저(claude-in-chrome)로 열어 스크린샷.
   아니면 상태코드 요약만.
4. 확인 끝나면 띄운 프로세스는 `taskkill` 로 정리 (사용자가 계속 보고 싶다고 하면 남겨둠).

로그·DB 는 로컬 개발용이라 마음껏 지워도 된다. 코드·커밋은 건드리지 않는다.
