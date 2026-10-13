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
- **LH 공고문 원문(`announcement_document.raw_text`)은 대부분 잘려 있다.** 서버 DB 확인(2026-10-13): 311건 중 304건이
  상한 15,500자에서 잘림(`LhClient` 절단, V3 `VARCHAR(16000)`). 공고문 뒤쪽(제출 서류·소득 기준·입주 시기 등)은 인덱스에
  없어 Q&A 가 "발췌에서 확인되지 않습니다"로 답한다. 늘리려면 스키마 변경(행 상한 65,535B — utf8mb4 주의) 필요.
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
   청크만 저장(`embedding="[]"`, 모델 `bm25`)하고 키워드로만 검색, 응답은 Groq. 수집 직후 자동 인덱싱은 BM25 모드에서만
   (임베딩은 과금이라 `/api/admin/rag/reindex` 수동). 키워드 검색이라 의역("돈 얼마 내야")은 못 찾는다 — 무료 임베딩은 후속.
3. ~~신혼희망타운 — 별도 `SpecialSupplyType` + 규칙~~ 완료.
   `SpecialSupplyType.NEWLYWED_HOPE_TOWN` + `NewlywedHopeTownRule` + 두 클라이언트의
   `reclassifyForHopeTown` 까지 있다. 남은 건 코드가 아니라 **원본 데이터**다 —
   위 "데이터에서 확인된 사실" 참고(유형별 세대수가 오지 않아 배정이 늘 0 이다).
5. ~~Docker + CI + 배포~~ 서버 배포 완료 (2026-10-12, Lightsail, `docs/DEPLOYMENT.md`).
   CI 자동 배포 완료(2026-10-12) — `master` push 시 서버 자동 배포, 절차·키 교체는 `docs/DEPLOYMENT.md` "자동 배포". 남은 건 정식 도메인(바꾸면 GitHub 변수 `DEPLOY_HEALTH_URL`도 함께).
6. ~~LH 공고 화면 보강 — 이슈 #18~~ 완료(2026-10-12). UI 개선(판정 근거 카드·타임라인 등) — 이슈 #19 완료(2026-10-12).
