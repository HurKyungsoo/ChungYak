# CLAUDE.md

청약 자격 확인 서비스 "청약나침반" 프로젝트 작업 규칙.

## 프로젝트 개요

공공데이터로 분양 공고를 수집하고, 사용자 조건에 맞는 특별공급 유형을 찾아주는 서비스.

**이 프로젝트의 핵심 가치는 "판정을 LLM 에 맡기지 않는다"는 설계다.**
기능을 늘리는 것보다 이 경계를 지키는 것이 우선한다.

## 스택

- Java 21 / Spring Boot 3.3.0 / Gradle
- JPA(정적 CRUD·락) + MyBatis(동적 조회·집계, 도입 예정)
- MariaDB(prod) / H2 MODE=MySQL(local, test)
- 스키마: **Flyway** (`db/migration/V{n}__*.sql`). `ddl-auto: validate`.
  적용된 마이그레이션은 수정 금지 — 새 파일 추가. H2·MariaDB 공용 SQL 로 작성.
  **H2 그린 ≠ MariaDB OK.** utf8mb4 는 글자당 4B — VARCHAR 길이×4 가 행 상한 65,535B 안이어야 한다
  (V3·V1 이 첫 서버 배포에서 실패했다). TEXT 로 바꾸면 Hibernate validate 가 막으니 VARCHAR 유지 + 숫자/JSON 은 ascii.
- Thymeleaf
- 배포: Docker Compose (app + MariaDB + Caddy). `docs/DEPLOYMENT.md`, 계획은 `docs/ROADMAP.md`
- LLM: 공식 Anthropic Java SDK (`com.anthropic:anthropic-java`) — Spring AI 아님.
  자연어 추출 + 판정 요약. SDK 호출은 `llm/AnthropicProfileCaller`·`llm/AnthropicExplainer` 두 곳에만.
  무료 대안: **Groq**(OpenAI 호환, raw HTTP — `llm/GroqChatClient`). `ANTHROPIC_API_KEY` 가 없고 `GROQ_API_KEY` 가
  있을 때만 켜진다(Anthropic 우선). 자연어 추출·공고문 Q&A 답변에만 쓰고, **판정 요약은 기본 꺼짐**
  (`GROQ_EXPLAIN_ENABLED=false` — 요약 입력에 소득·자산이 들어가 무료 등급으로 내보내지 않는다. 꺼두면 `FallbackSummary`).
  **무료 등급 한도는 모델별**이다(2026-10-13 확인 — 120b 하루 한도가 찬 동안 20b 는 정상): gpt-oss-120b·20b·qwen3.8-27b
  모두 분당 8,000 토큰(TPM)·하루 200,000 토큰(TPD). 공고문 Q&A 는 발췌 4개 ≈ 3,200 토큰, 2개 ≈ 1,800, **3개(현재 기본) ≈ 2,450**(#39 — 검색 eval 에서 정답 발췌가
  LLM 까지 가는 비율 23→28/38). 빠듯하면 `RAG_QA_CONTEXT_CHUNKS=2`.
  **공고문 Q&A 는 결정론 답변이 먼저다**(#39 `rag/FactAnswerer`): 요약표·API 에 답이 있는 질문(전매·발표일·세대수·
  특공 유형별 세대·분양가 등)은 LLM 을 부르지 않는다. 그다음 `rag/TableFactAnswerer`(#42)가 표를 편 원문의 "열 이름: 값" 행에서
  계약금 비율·임대보증금/월임대료·소득 기준을 **행 그대로** 보여 준다 — 열 이름이 애매하면(예: "최대전환 시 월임대료"만) 넘긴다.
  AI 답 검사는 둘이다: `AnswerGrounding`(#31, 금액·날짜가 어긋나면 답을 숨김) + `SentenceGrounding`(#41, 숫자+단위가 발췌에 없거나
  핵심어 겹침이 60% 미만인 문장을 "원문 확인 필요"로 표시 — 오프라인 판정 세트에서 근거 있는 문장 14 중 오탐 0, 근거 없는 10 중 7).
  발췌 문구를 엉뚱하게 이어 붙이거나 부정을 뒤집은 문장은 원리상 못 잡는다. 실제 Groq(20b) 답 12개로도 쟀다(2026-10-14,
  `NoticeQaSemanticEvalTest.groqAnswers`, `EVAL_GROQ_ANSWERS=true` 일 때만): 사람 판정 9 정확·1 핵심 누락·1 이어붙이기,
  검사는 0개 표시(오탐 0, 문제 2개는 못 잡음 — 누락·이어붙이기는 낱말이 발췌에 다 있다). **로컬 빌드는 `GROQ_API_KEY` 를 빼고**
  (`env -u GROQ_API_KEY ./gradlew build`) — 있으면 Groq 실시간 eval 이 20분간 운영과 같은 한도를 쓴다. 검색 품질은 `NoticeQaRetrievalEvalTest`(실제 공고문 3건·38문항,
  오프라인)가 기준선으로 지킨다 — 검색·청크·동의어를 바꾸면 이 숫자를 본다.
  **분당 한도가 먼저 걸린다**(Q&A 2~4회/분). gpt-oss 의 추론 토큰은 `max_tokens` 안에서 쓰여, 기본 추론으로 1,024 를
  다 써 답이 빈 적이 있다 → Q&A 는 `GROQ_QA_REASONING_EFFORT=low`(추출에는 적용 안 됨). Q&A 모델은 `GROQ_QA_MODEL`.
  한도는 **조직(계정) 단위**다(429 메시지가 `in organization org_…` 로 지목) — 같은 계정이면 키를 새로 만들어도
  공유한다. 로컬 eval·실사용 시험을 많이 돌리면 운영이 429 로 막힌다(실제로 막혔다). 분리하려면 별도 Groq 계정.
  운영 관찰: `GET /api/admin/llm/stats`(추출·Q&A 결과 분포, 429 횟수, 모델별 호출·토큰 — 재배포 시 0 부터).
  **비슷한 질문 답 재사용**(#45): 같은 공고에서 질문 임베딩 코사인 ≥ `RAG_QA_SIMILAR_MIN_SCORE`(0.85) **이고**
  `rag/SimilarQuestionGuard`(사전 낱말·순서·숫자·묻는 것 + **사전 밖 낱말**)가 같으면 이전 답(`qa.SIMILAR_HIT`). 메모리만, 원문 저장 없음.
  코사인만으로는 안 된다 — "보증금 올리고 월세 줄이기"와 그 반대가 0.95, 같은 뜻 "견본주택/모델하우스"는 0.59.
  사전 낱말만 보던 첫 가드는 나중에 쓴 hold-out 에서 25쌍 중 2쌍 오탐(몇 시까지/부터 0.94, 이혼/재혼 0.91) → 사전 밖 낱말 비교 추가.
  지금은 안전 쪽: 다른 뜻 412쌍 오탐 0, 같은 뜻 재사용 10/54. `SimilarQuestionGuardTest`(오프라인)가 다른 뜻 쌍 전부 차단을 강제.
  가드를 고치면 eval 쌍은 이미 본 것이 된다 — 검증하려면 **고치기 전에** 새 hold-out 을 쓸 것.
  **국토부 공식 답변(FAQ)**: 이용자 질문의 다수는 "내 상황이 어떻게 세어지나"다(국토부 FAQ 표본 신청자 질문 112 중 상황형 59%,
  엔진 입력값만으로 답 나는 건 ~6% — `faq-questions-dev.tsv`). 그 해석은 공고문보다 국토부 「2024 주택청약 FAQ」(2024-05-29,
  공공데이터 15035942, 이용 제한 없음, 480문항)에 있다 → `rag/OfficialFaqSearch` 가 비슷한 문항을 찾아 **답을 원문 그대로**
  AI 답 위에 보여 준다(LLM 안 거침). 방식: 제목 BM25+임베딩 RRF 상위 20 → Voyage rerank 1위 ≥ `rag.faq.min-score`(0.60).
  `OfficialFaqEvalTest`(말투 질문): dev 1위 22/26·부정 오탐 0, test 1위 22/25·3위 안 25/25·**부정 1건 오탐**("동호수 추첨은 언제" 0.617
  — test 로 기준을 고치지 않음). Voyage 가 없으면 꺼진다. 공고 데이터로 답한(FACT) 질문엔 안 붙인다.
  FAQ 는 2024-05 기준 — 그 뒤 「주택공급에 관한 규칙」 개정 7건(`faq/amendments.json`, 국가법령정보센터 개정이유 원문)의 pattern 이
  걸리는 문항(120/480)엔 개정 경고를 붙인다. 최신판 FAQ 는 2026-10 현재 없다(부동산원 「주택청약의 모든 것」 2025~26 은 유료 책).
  자료 만드는 법 `scripts/faq/`(PDF 는 저장소 밖).
  Q&A 주제별 집계(#40): `GET /api/admin/qa/topics?days=30` — 주제(`rag/QuestionTopic`) × 결과 × 버튼 문구 × 날짜 개수만
  `qa_topic_stat`(V9)에 쌓는다. **질문 원문은 저장하지 않는다**(버튼 문구는 서버가 만든 문장이고, 실제 버튼 목록에 있을 때만 저장).
  자주 묻는 질문 버튼(`rag/QuickQuestions`): 공식 데이터 버튼은 `FactAnswerer` 로 답이 나오는 것만 띄우고, AI 버튼 문구는
  검색 eval(`notice-qa-cases.tsv`) 질문과 같게 둔다(`QuickQuestionsTest` 가 강제). 30일 클릭 순으로 정렬.
  외부 LLM 호출 코드는 `llm/`·`rag/` 의 구현체에만 두고, `rule/` 에는 어떤 경우에도 들이지 않는다(절대 규칙 1).
  AI 를 새로 켜거나 공급자를 바꾸면 `/privacy`(처리방침) 위탁 항목이 켜진 기능을 따라가므로 `LegalController` 도 함께 본다.

## 빌드 · 실행

```bash
./gradlew build
./gradlew bootRun
./gradlew test --tests '*EligibilityEngineTest*'
```

`PUBLICDATA_SERVICE_KEY` 가 없으면 외부 API 는 빈 리스트를 반환한다(앱은 정상 기동).
`ANTHROPIC_API_KEY` 도 `GROQ_API_KEY` 도 없으면 자연어 입력 기능만 꺼진다(`@ConditionalOnExpression`).
통합테스트(`*ProfileExtractionIntegrationTest*`)는 키 없으면 skip.
로컬 기본(`local`)은 **유료 AI(Anthropic·Voyage) 꺼짐** — 셸에 키가 있어도 안 켜진다. 켜려면
`./gradlew bootRun --args='--spring.profiles.active=local,local-ai'` + 환경변수 또는 `application-local-ai.yml`
(gitignore, 예시 `application-local-ai.example.yml`). Groq 는 `GROQ_API_KEY` 만 있으면 켜진다.
`@SpringBootTest` 는 `ai-off.properties` 로 외부 AI 빈을 항상 끈다 — 새 통합 컨텍스트 테스트에도 붙일 것.
`application-local.yml` 에 유료 키를 넣지 말 것(그 파일은 `local` 마다 읽힌다).

## ★ 절대 규칙 — LLM 과 규칙 엔진의 경계

이 항목을 어기면 프로젝트의 존재 이유가 사라진다.

1. **자격 판정은 `rule` 패키지 안에서만 한다.**
   LLM 호출 코드가 `EligibilityRule` 구현체나 `EligibilityEngine` 에 들어가면 안 된다.

2. **LLM 의 역할은 앞뒤 두 곳뿐이다.** 둘 다 `llm` 패키지, 구현 완료.
   - 앞: 자연어 질문 -> `ExtractedProfile` 추출 (`ProfileExtractionService`)
   - 뒤: `MatchResult` -> 자연어 요약 (`ExplanationService`)
   그 사이는 전부 결정론적이어야 한다.
   - 앞: 추출값이 판정으로 직행하면 안 된다 — 사용자가 폼에서 확인·수정한 뒤에만
     `EligibilityEngine` 으로 들어간다. 애매한 필드는 추측하지 말고 null.
   - 뒤: 판정은 이미 끝났고 LLM 은 근거를 문장으로 재구성만 한다. `ContradictionCheck` 가
     요약이 판정과 어긋나는지 사후 검사하고, 어긋나면 `FallbackSummary`(결정론적)로 대체.

3. **`EligibilityDecision` 은 반드시 이유를 남긴다.**
   `satisfied`/`failed`/`missing` 중 최소 하나는 채워져야 한다.
   이유 없는 판정은 LLM 이 근거를 지어내게 만든다.

4. **판정 규칙을 수정하면 `EligibilityEngineTest` 를 반드시 돌린다.**
   경계값(혼인 84/85개월, 통장 6/24개월, 소득 상한, 자산 상한) 테스트가 깨지면 규칙이 바뀐 것이다.

5. **새 특별공급 유형은 `EligibilityRule` 구현체를 추가한다.**
   기존 규칙에 if-else 를 덧붙이지 말 것.
   여러 유형에 공통인 요건(재당첨 제한·소득·자산)은 `CommonRequirements` 로 묶어
   규칙이 주입받아 쓴다 (규칙마다 복붙 금지). 새 공통 요건은 여기에 추가.
   각 요건은 `RequirementCheck`(PASS/FAIL/MISSING)를 사유와 함께 돌려준다.

6. **제도 수치는 `application.yml` 에.** 소득 상한(%), 자산 상한, 도시근로자 소득표,
   재당첨 제한 기간은 코드에 박지 말 것 —
   `special-supply`, `income-reference`, `re-win-restriction` 로 두어 배포 없이 갱신한다.

## 외부 API 규칙

- 청약홈은 **odcloud 게이트웨이**(`api.odcloud.kr`)다. `api.data.go.kr` 과 다르다.
  - 페이징: `page`/`perPage` (`pageNo`/`numOfRows` 아님)
  - 조건: `cond[FIELD::EQ]=value`
  - 응답: `{data:[...], totalCount, matchCount}`
- LH 는 `apis.data.go.kr` 이고 응답 최상위가 **배열**이며 `dsSch`/`dsSplScdl` 등
  데이터셋이 나뉘어 담긴다. 청약홈과 구조가 완전히 다르므로 어댑터에서 흡수한다.
- `cond[..]` 파라미터는 대괄호 인코딩이 필요해 `build(true)` 를 쓰면 안 된다.
  반면 `serviceKey` 는 이미 인코딩된 값이라 재인코딩하면 401 이 난다.
  두 요구가 충돌하므로 URI 조립 방식을 바꿀 때 반드시 라이브 호출로 확인할 것.
- 파싱 실패는 예외를 던지지 말고 **해당 건만 스킵**한다.
- API 키를 코드나 `application.yml` 에 하드코딩하지 말 것.
- 파서가 읽는 필드명을 바꾸면 `ApplyhomeParseContractTest`(오프라인 픽스처)가 깨진다.
  실제 API 스키마 변화는 `ApplyhomeApiContractTest`(라이브, `PUBLICDATA_SERVICE_KEY` 있을 때)가 잡는다.

## 데이터에서 확인된 사실 (추측 금지)

라이브 호출로 검증한 내용이다. 바꾸기 전에 다시 호출해서 확인할 것.

- 청약홈 공고 총 2,861건, 주택형 총 14,637건
- 특별공급 세대수는 **주택형(Mdl)마다 다르다.** 공고 단위가 아니다.
- 청년(`YGMN_HSHLDCO`)은 **공공주택에만** 채워진다
  (`HOUSE_DTL_SECD='03'` + `PUBLIC_HOUSE_SPCLW_APPLC_AT='Y'`). 현재 데이터에 5개 공고뿐.
- 신생아(`NWBB_HSHLDCO`)는 **공공·민영 모두** 채워진다 (2024 제도 개편).
  라이브 확인(2026-09-03): 민영 49공고·205주택형에 배정.
  예) "올 뉴 챔피언스시티 1차" `PBLANC_NO=2026000419`(민영·광주) 084 타입 신생아 31세대.
  → `NewbornRule` 은 공고 유형으로 거르면 안 된다. 물량 유무는 엔진이 `SupplyBreakdown` 으로 본다.
- `HSSPLY_ADRES` 에 "전남광주통합특별시" 같은 통합 명칭이 들어온다.
  지역 필터는 `SUBSCRPT_AREA_CODE_NM` 을 쓸 것.
- null 이 흔한 필드: `NSPRC_NM`, `SPSPLY_RCEPT_*`, `GNRL_RNK1_ETC_GG_*`
- **신혼희망타운 공고에는 유형별 배정 세대수가 아예 없다.** DB 확인(2026-09-23):
  `HOUSE_TYPE='NEWLYWED_HOPE_TOWN'` 99공고·주택형 199개에서 `SPECIAL_SUPPLY_COUNT`
  합계는 13,430세대인데 `NEWLYWED`·`NEWLYWED_HOPE_TOWN` 등 유형별 컬럼은 전부 0 이다.
  청약홈이 이 공고 유형에는 총 특공 세대수만 주기 때문에 `ApplyhomeClient.reclassifyForHopeTown`
  은 옮길 값이 없어 한 번도 발동하지 않는다(코드가 틀린 게 아니라 원본에 값이 없는 것).
  → 총계를 희망타운 배정으로 단정하지 말 것. 신혼희망타운은 우선공급/잔여공급 구조라
  특공 총계와 유형 배정이 같지 않다. 화면은 "세대수를 세지 못했다"고 밝히고 공고문으로 넘긴다.
- **LH 토지·상가 공고는 수집하지 않는다** (`UPP_AIS_TP_CD` ∉ {05,06,13,39} → `toExternal` 이 null).
  `AIS_TP_CD=40`(가정어린이집 운영자 모집)도 주택이 아니라 제외.
  LH 주택 공고의 약 70% 는 "예비입주자 모집"(제목에 "예비입주자" — 코드로는 안 온다).
- **LH 상세 API 는 예비입주자 공고에도 일정·주소를 준다** (2026-10-12 라이브 30건: 시작일 30 /
  발표일 29 / 주소 19 / 공고문 PDF 30). 반드시 **목록이 준 `SPL_INF_TP_CD`** 로 호출할 것 —
  다른 값(예: 390)으로 부르면 첨부만 와서 "API 로 못 채운다"고 오판한다(#18 초기 판단이 그랬다).
  스키마가 둘이다: A형(임대·매입임대) `SBSC_ACP_ST_DT`/`LGDN_ADR`, B형(공공분양 050·리츠 060·
  신혼희망 분양 390) `ACP_DTTM` 문자열·`yyyyMMdd`·`LCT_ARA_ADR`. `9999.xx.xx` 는 미정.
  `dsSbd.HSH_CNT` 는 **기존 단지 전체 세대수**라 공급세대로 쓰면 안 된다.
- LH 목록의 `ALL_CNT` 는 세대수가 아니라 **조회 전체 건수**다(모든 행이 같은 값).
  LH 총 공급세대는 주택형 `NOW_HSH_CNT`/`SIL_HSH_CNT`("금회공급 세대수") 합계로 채운다.
- **청약홈 경쟁률·당첨가점·특공 신청현황 API** (`ApplyhomeInfoCmpetRtSvc/v1`, data.go.kr 15098905, 2026-10-13 활용신청·라이브 확인).
  같은 odcloud 게이트웨이·같은 키. 공고 2020~현재, 최신순. 조인 키는 `HOUSE_MANAGE_NO`(+ `MODEL_NO`/`HOUSE_TY`).
  - `getAPTLttotPblancCmpet`(55,222행): 주택형 × 순위(`SUBSCRPT_RANK_CODE`) × 거주(`RESIDE_SENM` 해당/기타지역)별
    `SUPLY_HSHLDCO`·`REQ_CNT`(문자열)·`CMPET_RATE`(문자열 — "2.00", 미달은 "(△1)", 2순위 미집계는 "-").
  - `getAPTLttotPblancScore`(29,918행): 주택형 × 거주별 `LWET_SCORE`/`AVRG_SCORE`/`TOP_SCORE`(문자열, 추첨·미달은 "0").
  - `getAPTSpsplyReqstStus`(12,436행): 주택형별 특공 유형 배정 `*_HSHLDCO` + 신청 건수 `CRSPAREA_/CTPRVN_/ETC_AREA_*_CNT`
    (해당지역/시도/기타지역). 유형 접미사: MNYCH 다자녀·NWWDS_NMTW 신혼부부·LFE_FRST 생애최초·OPS(배정은
    `OLD_PARNTS_SUPORT_HSHLDCO`) 노부모·NWBB_NWBBSHR 신생아·YGMN 청년·INSTT_RECOMEND 기관추천·TRANSR_INSTT_ENFSN 이전기관.
    예) 2026000374 051.9931B 생애최초 1세대에 2,950건(해당 2,461 + 기타 489).
  - `getRemndrLttotPblancCmpet`: 무순위·잔여세대 경쟁률. LH 공고는 이 API 범위 밖이다.
  - **분포(2026-10-14 전체 수집, #34)**: 가점·경쟁률에 지역이 없어 공고(운영 DB 3,200건)와 조인 — 100% 붙는다.
    최저가점 "0" 20%(제외), 0 아닌 값 7~84·중앙값 49. 경쟁률 "-" 47%·"(△n)" 31%·숫자 22%(쉼표 "7,474.67" 있음).
    비교 단위는 **시도 × 면적대 × 12개월**(최근 공고 25건 중 20건이 표본 3 이상, 시군구는 2건뿐). 같은 묶음 안 최저가점
    표준편차 ~10.7점 — 범위·표본 수로만 보여 준다.
    저장은 `competition_score`·`competition_rate`(V12, **첫 MyBatis 매퍼** `mapper/CompetitionMapper`), 수집은
    `CompetitionSyncService` 매일 04:30(표가 비면 전체 ≈86회, 아니면 최신 3페이지) · 수동 `POST /api/admin/competition/sync`.
    화면은 청약홈 민영 분양 공고 상세의 "○○ 최근 당첨 기록 (참고)" — 판정(`rule/`)과 무관.
- **LH 공급 API 의 금액 필드는 유형에 따라 다르다** (2026-10-13 라이브, 최근 공고 400건 유형별 3건씩):
  10년 공공임대(`SPL_INF_TP_CD=060`, `dsList02`)만 주택형별 `LS_GMY`(임대보증금, 원)·`MM_RFE`(월임대료, 원)가 숫자.
  분양(050)·신혼희망 분양(390)은 `SIL_AMT`(분양가, 원 → `topAmount` 만원으로 이미 수집). 국민·영구·행복·통합공공임대·
  공공임대 061·분양 051 은 `LS_GMY`/`RFE` 가 문자열 "공고문 참조", 매입임대(13)는 금액 필드 없음.
  → 대부분의 임대 금액은 공고문 PDF 표에만 있다. 표는 텍스트 추출 시 열이 섞인다(Q&A 가 월임대료 292,150원을
  6,600,000원으로 답한 사례 — #2901).
- **LH 공고문 PDF 전문은 30,000~68,000자다**(2026-10-13 8건 실측). 예전에는 15,500자에서 잘라 23~52% 만 저장했고
  서버 311건 중 304건이 잘려 있었다. V8(#29)부터 앞 15,000자는 `raw_text`, 나머지는 `announcement_document_part` 에
  15,000자씩 나눠 전문을 저장한다(MariaDB 행 상한 때문에 한 컬럼 불가). 잘린 예전 원문은 수집 때 목록에 다시 나오면
  PDF 를 다시 받아 교체한다(`AnnouncementDocument.isLegacyTruncated`). 전문이면 청크가 ~3배(공고당 ~60) —
  검색은 코퍼스 통계만 메모리에 두고 공고별 청크를 즉석 채점한다(전 청크 단어 빈도 보관 시 19,000청크 ~426MB).
- **공고문 PDF 표는 실제 괘선으로 그려져 있다**(한글에서 내보낸 PDF, 2026-10-14 — 2997 은 페이지당 선분 수백 개).
  위치순 추출은 합친 칸("362,000천원 이하")이 옆 칸 글자 사이에 끼고 머리글이 값과 떨어져 LLM 이 표 값을 못 읽었다.
  #42 부터 `external/PdfTables`(괘선 → 칸 → 표) + `TableAwareTextStripper`(표 행마다 "열 이름: 값 · …" 한 줄)로 편다.
  오른쪽 테두리 없는 표가 흔하다(가로선 끝이 맞으면 세로선으로 본다). 표 행 때문에 원문이 20~30% 길다(상한 300,000자).
  원문에 `text_format`(V11)을 두고, 예전 형식(1)이면 **접수 전·중 공고만** 다음 수집에서 PDF 를 다시 받는다.
- **LH 정정·취소공고는 새 `PAN_ID` 로 온다**(2026-10-14 라이브, 주택 310건 중 "[정정공고]" 63건·"[취소공고]" 1건).
  제목 태그("[정정공고]"·"(정정)"·"(정정공고)"·"[취소공고]")로만 구별되고, `PAN_DT`(최초 공고일)는 원본과 같다.
  정정본이 나오면 원본의 `PAN_SS` 가 **"정정공고중"** 이 된다. 취소는 상태 코드가 없다. → `domain/NoticeLineage`
  (계보 키 = PAN_DT + AIS_TP_CD + 태그 뗀 제목, #47). 목록·매칭·알림은 `revision_state=CURRENT` 만.
- **공고문(Q&A) 보유 범위**(2026-10-15 운영 DB): 현재 공고 3,465건 중 공고문 있는 건 LH 263/263, 청약홈 APT 접수 전·중 9/9 —
  **청약홈 마감 공고 3,185건은 일부러 받지 않는다**(공식 API 밖 페이지·PDF 요청 ~6,400회, APT 공고문 건당 ~230청크 →
  ~73만 청크·Voyage ~3.3억 토큰으로 무료분·서버 메모리 초과). 화면은 "마감된 공고는 공고문을 불러오지 않는다" 안내.
  무순위·재공급은 수집 소스가 공고문을 안 받던 버그(10-15 수정 — 페이지에 APT 와 같은 PDF 링크, 11쪽·~1.7만 자).
- **청약홈의 LH 공급 공고(신혼희망타운·LH 시행 공공분양)는 페이지에 PDF 가 없고 LH 청약플러스 링크뿐**이다. 같은 공고가
  LH 수집에도 있어(이름 표기 다름: "양주회천지구 A-26블록" / "양주회천 A-26BL") `domain/LhNoticeTwin`(공고일 ±1 + 지구명·블록)으로
  짝을 찾아 `announcement.document_announcement_id`(V13)로 LH 계보 최신본의 공고문을 빌려 쓴다(`LhDocumentLinkService`,
  수집 끝·`POST /api/admin/rag/link-lh-documents`). LH 수집이 2026-08-12 공고부터라 그 뒤 8건 중 6건만 짝이 있다
  (나머지 2건은 민간 시행 "힐스테이트 고덕엘리스트" — LH 목록에 없음). 같은 공고가 목록에 두 줄로 보이는 문제는 별도.
- **특별공급 횟수 제한 특례(주택공급규칙 제55조의3, 2025-03-31 신설·2026-06-15 현행)** — `ReWinRequirement` 반영(#49, 2026-10-15):
  배우자의 혼인 전 당첨 이력은 신생아·신혼부부·생애최초에서 안 따짐(①), 본인 혼인 전 당첨이어도 신혼부부 한 차례(②),
  2024-06-19 이후 출생 자녀(태아·입양)가 있으면 신생아·다자녀·신혼부부·노부모 한 차례 더(③). 생애최초는 배우자의 혼인 전
  주택 소유도 안 따짐(①2, `FirstTimeRule`). 입력이 없으면(누가·언제 = OTHER) 예전처럼 FAIL. ③2(본인·배우자 주택 소유 시
  처분 조건으로 무주택 요건 면제)는 소유자가 누구인지 입력이 없어 **판정에 넣지 않고 사유 문장에 밝힌다**.
  ⚠️ 미해결(#49): 제35조의3(2026-06-15 전문개정) 신생아 소득 기준은 국민주택 130%·**민영 160%(맞벌이 구분 없음)** 인데
  `special-supply.income-limit.newborn` 은 150/200(공공분양 기준으로 보임)을 모든 공고에 쓴다. 조문 원문은 국가법령정보센터
  `lsInfoR.do`(lsiSeq=286965) 로 받았다 — 법령 페이지는 JS 렌더링이라 WebFetch 로는 본문이 안 온다.
- LH `UPP_AIS_TP_CD=39`(신혼희망) 아래 `AIS_TP_CD=42` 는 **행복주택(임대)** 이다 — 신혼희망타운이 다 분양이 아니다.
- 분양가(`LTTOT_TOP_AMOUNT` -> `UnitType.topAmount`)는 **만원 단위**이고 주택형 14,713건
  전부 채워져 있다(2026-09-23, 범위 116 ~ 1,600,000). 층·향과 무관한 **최고** 공급금액이라
  화면 라벨에 "(최고)"를 붙인다. `SUPLY_AR` 은 전용이 아니라 **공급면적**이다
  (084.8508 타입의 값이 109.57 로 전용 84.85 보다 크다) — 평당가는 이걸로 나눈다.

## 하지 말 것

- Lombok `@Data`, `@Setter` 사용
- 컨트롤러에 비즈니스 로직 작성
- 규칙 엔진에서 LLM 호출
- 판정 결과를 이유 없이 반환
- `application.yml` 에 실제 키·비밀번호 커밋

## Git

- **브랜치 없이 `master` 에 바로 커밋한다.** (1인 프로젝트, PR 리뷰 없음)
- 작업 시작 전 `git pull --rebase`, 커밋 전 `./gradlew build` 그린 확인.
- 예외 — 이때만 브랜치: 규칙 엔진 변경(`rule/`), 며칠짜리 큰 작업(RAG 등).
- 여러 컴퓨터에서 작업하므로 오래된 base 위에 스택 쌓지 말 것.

## 보안

- `/api/admin/**` = `ROLE_ADMIN` (HTTP Basic), `config/SecurityConfig`.
  나머지 화면은 전부 공개. CSRF 는 비활성(세션 사용자 인증 없음 + 관리자 API stateless).
- 계정: `ADMIN_USERNAME`/`ADMIN_PASSWORD` 환경변수. 미설정 시 임시 비밀번호 자동 생성 → 로그.
- 컨트롤러 단위테스트(`AdminSyncControllerTest`)는 `standaloneSetup` 이라 시큐리티를 안 탄다.
  인증 동작은 `AdminSecurityTest`(`@SpringBootTest`)가 검증한다.

## 남은 작업

1. ~~LH 목록 API~~ 완료 — `LhClient` 운영 중(수집·상세·공급, #18). 임대 유형 **판정 규칙**은 미구현(대기자 모집이라 특공 구조가 아님).
2. ~~벡터 검색~~ 코드 완료 — `rag/` (BM25+벡터 하이브리드, 공고문 Q&A). `VOYAGE_API_KEY` 가 없으면 **BM25 모드**(#22):
   청크만 저장(`embedding="[]"`, 모델 `bm25`)하고 키워드로만 검색, 응답은 Groq. **운영은 2026-10-14 부터 Voyage 하이브리드**
   (`RAG_HYBRID_WEIGHT=0.4`). 수집 직후 자동 인덱싱은 두 모드 모두(임베딩 비용 실측 후 켬). 키를 처음 넣었을 때만
   `/api/admin/rag/reindex` 로 기존 공고를 한 번 임베딩한다. 벡터 없는 청크(재인덱싱 전)의 공고는 BM25 로 찾는다.
   **Voyage 를 켤지 eval 로 쟀다(2026-10-14, `NoticeQaSemanticEvalTest` — 키 있을 때만, 임베딩은 `build/eval-cache` 캐시)**:
   공고문 7건·69문항(새 4건·31문항은 공고문을 읽기 전에 질문을 쓴 hold-out). LLM 까지 가는 수:
   BM25 43 / **질문 임베딩 하이브리드 0.4: 49, 0.6: 48** / 벡터만 41. 이득은 전부 hold-out 에서 났다(14→20, 기존 38 은 29→29).
   처음 38문항만으로는 "이득 없음"으로 보였다 — **표본 38 개로 내린 결론이 뒤집혔다.**
   질문을 보내지 않는 주제 보정(`QuestionTopic` 설명문 × 청크 임베딩 RRF)은 기존 38 에서만 +2, hold-out 0 → **과적합, 채택 안 함**.
   **재정렬(Voyage rerank-2.5, 같은 키)**: 하이브리드 상위 20 을 다시 매겨 앞 3개만 LLM 에 → 49 → **61/69**(hold-out 20 → 25),
   후보 8: 56, 12: 59. "BM25·벡터 1위 고정"은 45 로 더 나빴다. 질문당 약 1.4만 토큰(rerank 무료 2억). 실패하면 하이브리드 순서로 답한다.
   남은 8개는 대부분 정답이 상위 20 밖이거나(동의어·청크 문제) 표가 깨진 경우다 — LLM 이 찾은 표를 못 읽는 실패는 이 eval 밖이다.
   **표를 행 단위로 편 추출(#42, 같은 PDF 6건)**: rerank-20 61 → **62/69**(hold-out 25 → 26). BM25 단독은 43 → 34 로 내려간다
   (표 글이 늘어 청크가 바뀐다) — BM25 기준선은 34 로 내렸다. 표 값 eval(`notice-qa-table-cases.tsv`, 9문항) 5 → 6.
   하이브리드를 켜면 이용자 질문이 Voyage(미국)로 간다 — `/privacy` 위탁 항목이 켜진다. **켜기로 결정(2026-10-14, w=0.4).**
   한글 공고문은 Voyage 토크나이저로 **글자당 0.72 토큰**(실측) — 서버 전체 인덱싱 ≈ 1,400만 토큰(voyage-4-lite 무료 2억).
   Voyage 대시보드의 Budget Limit 은 **알림 메일일 뿐 호출을 막지 않는다**.
3. ~~신혼희망타운 — 별도 `SpecialSupplyType` + 규칙~~ 완료.
   `SpecialSupplyType.NEWLYWED_HOPE_TOWN` + `NewlywedHopeTownRule` + 두 클라이언트의
   `reclassifyForHopeTown` 까지 있다. 남은 건 코드가 아니라 **원본 데이터**다 —
   위 "데이터에서 확인된 사실" 참고(유형별 세대수가 오지 않아 배정이 늘 0 이다).
5. ~~Docker + CI + 배포~~ 서버 배포 완료 (2026-10-12, Lightsail, `docs/DEPLOYMENT.md`).
   CI 자동 배포 완료(2026-10-12) — `master` push 시 서버 자동 배포, 절차·키 교체는 `docs/DEPLOYMENT.md` "자동 배포". 남은 건 정식 도메인(바꾸면 GitHub 변수 `DEPLOY_HEALTH_URL`도 함께).
6. ~~LH 공고 화면 보강 — 이슈 #18~~ 완료(2026-10-12). UI 개선(판정 근거 카드·타임라인 등) — 이슈 #19 완료(2026-10-12).
