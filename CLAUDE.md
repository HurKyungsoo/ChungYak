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
- Thymeleaf
- 배포: Docker Compose (app + MariaDB + Caddy). `docs/DEPLOYMENT.md`, 계획은 `docs/ROADMAP.md`
- LLM: 공식 Anthropic Java SDK (`com.anthropic:anthropic-java`) — Spring AI 아님.
  자연어 추출 + 판정 요약. SDK 호출은 `llm/AnthropicProfileCaller`·`llm/AnthropicExplainer` 두 곳에만.

## 빌드 · 실행

```bash
./gradlew build
./gradlew bootRun
./gradlew test --tests '*EligibilityEngineTest*'
```

`PUBLICDATA_SERVICE_KEY` 가 없으면 외부 API 는 빈 리스트를 반환한다(앱은 정상 기동).
`ANTHROPIC_API_KEY` 가 없으면 자연어 입력 기능만 꺼진다(`@ConditionalOnExpression`).
통합테스트(`*ProfileExtractionIntegrationTest*`)는 키 없으면 skip.

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
   소득·자산처럼 여러 유형에 공통인 요건은 `IncomeRequirement`/`AssetRequirement` 같은
   공유 컴포넌트로 만들고 규칙이 주입받아 쓴다 (규칙마다 복붙 금지).

6. **제도 수치는 `application.yml` 에.** 소득 상한(%), 자산 상한, 도시근로자 소득표는
   코드에 박지 말 것 — `special-supply`, `income-reference` 로 두어 배포 없이 갱신한다.

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

## 하지 말 것

- Lombok `@Data`, `@Setter` 사용
- 컨트롤러에 비즈니스 로직 작성
- 규칙 엔진에서 LLM 호출
- 판정 결과를 이유 없이 반환
- `application.yml` 에 실제 키·비밀번호 커밋

## 보안

- `/api/admin/**` = `ROLE_ADMIN` (HTTP Basic), `config/SecurityConfig`.
  나머지 화면은 전부 공개. CSRF 는 비활성(세션 사용자 인증 없음 + 관리자 API stateless).
- 계정: `ADMIN_USERNAME`/`ADMIN_PASSWORD` 환경변수. 미설정 시 임시 비밀번호 자동 생성 → 로그.
- 컨트롤러 단위테스트(`AdminSyncControllerTest`)는 `standaloneSetup` 이라 시큐리티를 안 탄다.
  인증 동작은 `AdminSecurityTest`(`@SpringBootTest`)가 검증한다.

## 남은 작업

1. LH 목록 API 활용신청 (포털 버튼 404 로 막혀 있음) -> 승인되면 `LhClient` 추가
2. 벡터 검색 — LH 공고내용(4000자) 임베딩 + 하이브리드 검색
3. 신혼희망타운 — 별도 `SpecialSupplyType` + 규칙 (현재 엔진이 매칭 못 냄)
5. Docker + CI + 배포
