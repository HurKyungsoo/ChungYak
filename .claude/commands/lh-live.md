---
description: LH 실 API 호출 확인 (LhClientLiveTest, PUBLICDATA_SERVICE_KEY 필요)
---

LH 오픈 API(목록·상세·공급)가 여전히 예상대로 응답하는지 라이브로 확인한다.
CLAUDE.md: "바꾸기 전에 다시 호출해서 확인할 것."

1. `PUBLICDATA_SERVICE_KEY` 환경변수가 없으면 사용자에게 알리고 멈춘다.
2. `LH_LIVE_TEST=1 ./gradlew test --tests '*LhClientLiveTest*'` 실행.
   (이 테스트는 `@EnabledIfEnvironmentVariable("LH_LIVE_TEST"=1)` 라 평소엔 안 돎)
3. 결과 요약: 목록에서 공고 몇 건 왔는지, 첫 공고의 providerParams, 주택형 개수,
   특별공급 유형 집합, `countsKnown=false` 인지.
4. 응답 구조가 픽스처(`LhClientTest`)와 어긋난 흔적이 보이면(파싱 0건, 필드 누락 등)
   경고하고 어느 부분인지 지목한다. 메모리 `lh-open-api` 와 대조.

코드는 건드리지 않는다. $ARGUMENTS 가 있으면 그 유형/공고에 집중.
