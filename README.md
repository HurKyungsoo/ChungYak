# 청약나침반

공공데이터로 분양 공고를 수집하고, 사용자 조건에 맞는 **특별공급 유형**을 찾아주는 서비스.

이 프로젝트의 한 문장: **"자격 판정을 LLM 에게 맡기지 않는다."**
LLM 은 앞(자연어 → 조건)과 뒤(판정 결과 → 설명)에만 쓰고, 그 사이의 판정은 전부 결정론적 규칙 엔진이 한다.

---

## 기술 스택

| 구분 | 선택 | 이유 |
|---|---|---|
| 언어/런타임 | Java 21, Spring Boot 3.3 | 레코드·패턴매칭·가상스레드까지 쓸 수 있는 최신 LTS |
| 영속성 | JPA + MyBatis 병행 | 정적 CRUD·락은 JPA, 동적 조회·집계는 MyBatis (도입 예정) |
| DB | MariaDB (운영) / H2 파일 (로컬·테스트) | 로컬은 무설정 기동, 운영은 표준 RDBMS |
| 화면 | Thymeleaf | 서버 렌더링, 판정 결과를 그대로 보여주는 정보 중심 화면 |
| 빌드 | Gradle 8.10.2 | Spring Boot 3.3 Gradle 플러그인 호환 (9.x 는 `bootJar` 에서 깨짐) |
| 외부 API | 한국부동산원 청약홈 (odcloud), 한국토지주택공사 LH (승인 대기) | 공공데이터포털 |
| LLM | 공식 Anthropic Java SDK (`com.anthropic:anthropic-java`) | Spring AI 는 2.x 가 Spring Boot 4.0 을 요구 — 버전에 묶이지 않게 SDK 직접 사용. 구조화 출력(`output_config.format`)으로 "애매하면 null" 을 파싱이 아니라 **스키마로 강제** |

---

## 아키텍처

```
              [자연어 질문]                                    [판정 결과]
                   │                                               ▲
                   ▼ ProfileExtractionService (LLM: 값 추출만)       │ ExplanationService
              ExtractedProfile ──► [사용자가 폼에서 확인·수정] ──┐     │ (LLM: 근거 재구성 + 모순검사)
                                                              ▼     │
  청약홈/LH API ──► 어댑터 ──► Announcement ──────────►  ApplicantProfile
   (ApplyhomeClient)  (PublicDataParser)  (JPA)                │
        │                                                     ▼
        │                                            ┌──────────────────┐
        └── AnnouncementSyncScheduler ──────────────►│ EligibilityEngine │  ◄── 여기 안에서만 판정
             (수동 /api/admin/sync + 매일 04시)        │   (rule 패키지)    │
                                                     └──────────────────┘
                                                              │
                                                 EligibilityDecision (이유 필수)
                                                              │
                                          EligibilityResultView (화면 재배열)
                                                   + ExplanationResult (AI 요약 / 폴백)
```

LLM 은 앞뒤 두 곳에만 있다. **앞**: 자연어 → 폼 값 채우기(사용자가 확인·수정 후 규칙 엔진으로).
**뒤**: 확정된 판정 근거를 문장으로 재구성(모순 검사 후 표시, 실패 시 규칙 데이터로 조립한 폴백).
어느 쪽도 판정 자체에는 관여하지 않는다.

### 레이어

- **external** — 청약홈(odcloud)·LH(data.go.kr) 응답 구조 차이를 흡수해 `ExternalAnnouncement` 로 정규화.
  파싱 실패는 예외를 던지지 않고 그 건만 건너뛴다.
- **domain** — `Announcement` ─< `UnitType` (주택형). 특별공급 세대수(`SupplyBreakdown`)는
  **공고가 아니라 주택형마다** 다르므로 `UnitType` 에 매단다.
- **rule** — `EligibilityRule` 구현체 하나 = 특별공급 유형 하나.
  현재 신혼부부·생애최초·다자녀·노부모부양·신생아 5종. 새 유형은 구현체 추가로만 늘린다.
- **service** — 수집(`AnnouncementSyncService`), 조회(`AnnouncementQueryService`).
- **llm** — CLAUDE.md 규칙의 "앞·뒤"만 담당. `ProfileExtractionService`(자연어 → 폼 값),
  `ExplanationService`(판정 근거 → 요약 + `ContradictionCheck` 모순 검사 + `FallbackSummary` 폴백).
  SDK 호출은 `AnthropicProfileCaller` / `AnthropicExplainer` 두 파일에만 격리(테스트는
  `LlmProfileCaller` / `LlmExplainer` 인터페이스에 목을 끼운다).
  `ANTHROPIC_API_KEY` 가 없으면 빈이 안 만들어지고 두 기능만 꺼진다.
- **web** — 컨트롤러 3개(관리자 sync / 공고 목록·상세 / 자격 판정+자연어 추출). 비즈니스 로직 없음.
- **config** — `SecurityConfig`: `/api/admin/**` 만 `ROLE_ADMIN`(HTTP Basic), 나머지 화면은 공개.
  CSRF 비활성(세션 기반 사용자 인증 없음 + 관리자 API 는 stateless Basic). 계정은
  `ADMIN_USERNAME`/`ADMIN_PASSWORD` 환경변수, 미설정 시 임시 비밀번호를 기동 로그에 남긴다.

### 화면

| 경로 | 설명 |
|---|---|
| `GET /announcements` | 접수중·예정 공고 목록, 지역·주택유형 필터 |
| `GET /announcements/{id}` | 공고 상세 + 주택형별 특별공급 세대수 표 |
| `GET /announcements/{id}/eligibility` | 조건 입력 폼 (`ANTHROPIC_API_KEY` 있으면 자연어 입력창 추가) |
| `POST /announcements/{id}/eligibility/extract` | 자연어 문장 → 폼 자동 채우기. 판정 안 함 — 확인 못 한 항목은 "직접 선택" 안내 |
| `POST /announcements/{id}/eligibility` | 판정 결과 — 주택형별 신청 가능 유형·배정 세대수, "자격은 되지만 물량 없음", 전체 판정 근거. `ANTHROPIC_API_KEY` 있으면 맨 위에 "AI 요약" 별도 표시(규칙 근거는 그대로 유지) |
| `POST /api/admin/sync` | 청약홈 즉시 수집 (응답: `pagesFetched/received/created/updated`). **`ROLE_ADMIN` 필요 (HTTP Basic)** |

---

## 설계 판단 — 왜 규칙 엔진과 LLM 을 분리했나

### 판정에 LLM 을 쓰면 안 되는 이유

1. **재현되지 않는다.** 같은 조건을 두 번 물으면 다른 답이 나올 수 있다.
   청약은 "될 수도 있다"가 아니라 되거나 안 되거나 둘 중 하나다.
2. **근거를 지어낸다.** "안 됩니다"만 필요한 게 아니라 *왜* 안 되는지가 필요한데,
   LLM 은 그럴듯한 문장을 만들어낸다. 틀린 근거는 없는 근거보다 나쁘다.
3. **틀린 답의 대가가 크다.** 잘못 신청하면 청약통장을 쓰고 재당첨 제한이 걸린다.

### 그래서 이렇게 나눴다

```
자연어 질문 ──(LLM)──► ApplicantProfile ──(규칙 엔진)──► EligibilityDecision ──(LLM)──► 자연어 설명
             추출                        결정론적 판정                          설명 생성
```

- **경계는 코드로 강제한다.** `EligibilityRule` 구현체와 `EligibilityEngine` 안에는
  LLM 호출 코드가 없다. (`CLAUDE.md` 절대 규칙)
- **`EligibilityDecision` 은 이유를 반드시 남긴다.** `satisfied` / `failed` / `missing` 중
  최소 하나는 채워진다. 입력이 부족하면 판정을 내리지 않고 무엇이 빠졌는지 알린다.
- **경계값은 테스트로 못 박는다.** 혼인 84/85개월, 청약통장 6/24개월(규제지역).
  `EligibilityEngineTest` 9건이 이 경계를 지킨다 — 깨지면 규칙이 바뀐 것이다.

### 자연어 추출 — LLM 을 어디까지만 쓰나

`ProfileExtractionService` 는 자연어 문장에서 폼 값을 뽑는다. 그 이상은 안 한다.

- **판정 없음.** 서비스는 `ExtractedProfile`(nullable 필드 9개)만 만든다. 판정은 사용자가
  값을 확인·수정해 폼을 제출한 뒤 `EligibilityEngine` 이 한다. LLM 결과가 판정으로 직행하는
  경로 자체가 없다.
- **추측 금지 → null.** 구조화 출력 스키마의 모든 필드가 nullable 이고, 시스템 프롬프트도
  "근거 없거나 모호하면 null" 을 강제한다. `"신혼인 것 같은데"` → `married=true` 는 되지만
  `monthsSinceMarriage=null`. null 필드는 화면에서 "직접 선택하세요" 로 되묻는다.
- **키 없으면 기능만 꺼진다.** `ANTHROPIC_API_KEY` 미설정 시 `AnthropicClient` 빈을 안
  만들고(`@ConditionalOnExpression`), 서비스는 `Optional.empty()` 를 받아 비활성. 자연어
  입력창이 화면에서 사라지고 나머지는 그대로 동작한다.
- **SDK 격리.** Anthropic SDK 를 만지는 코드는 `AnthropicProfileCaller` 한 파일뿐.
  `ProfileExtractionService` 는 `LlmProfileCaller` 인터페이스만 의존 →
  단위테스트가 LLM 응답을 목으로 고정해 null 처리 로직만 검증(`ProfileExtractionServiceTest`),
  실제 호출은 `@EnabledIfEnvironmentVariable` 통합테스트로 분리(`ProfileExtractionIntegrationTest`).

### 판정 요약 (LLM 뒷단) — 왜 설명에만 쓰나

`ExplanationService` 는 이미 확정된 판정을 자연어로 정리한다. 판정에는 손대지 않는다.

- **재구성만, 판정 안 함.** 규칙 엔진이 낸 `satisfied`/`failed`/`missing` 이유를 그대로 근거로
  주고 "이걸 문장으로 정리해줘" 만 시킨다. 프롬프트에 "판정 결과나 세대수를 새로 만들지 말 것"
  을 명시. LLM 이 판정 로직에 들어갈 여지가 없다 — 입력이 이미 확정된 결론이다.
- **모순 검사로 방어.** LLM 이 지시를 어기고 "신청 가능합니다" 를 지어낼 수 있다.
  `ContradictionCheck` 가 `MatchResult.hasAnyMatch()` 극성과 요약 문장의 단정을 대조한다 —
  신청 가능 유형이 없는데 "가능하다" 고 하거나, 있는데 "없다" 고 하면 모순.
  ("가능" 한 단어가 아니라 "신청가능합니다"/"신청가능한특별공급이없" 같은 문형으로 판단해 오탐을 줄인다.)
- **모순이면 재생성 → 폴백.** 최대 2회 재생성하고, 그래도 모순이면 `FallbackSummary` 로 대체한다.
  폴백은 `MatchResult` 값만으로 조립한 결정론적 한 문단이라 판정과 절대 어긋나지 않는다.
  이 흐름을 `ExplanationServiceTest`(모순되는 목 → 폴백 확인)와 `ContradictionCheckTest` 가 고정한다.
- **화면.** "AI 요약" 은 규칙 근거 목록 **위에 별도로** 얹힌다. 근거 목록은 그대로 남아 있어
  요약이 근거를 가리지 않는다. 폴백일 땐 "AI" 배지 없이 "요약(규칙 데이터로 자동 생성)" 으로 표시.
- **키 없으면 안 보인다.** `ANTHROPIC_API_KEY` 없으면 `DISABLED` — 요약 영역 자체가 사라지고
  규칙 기반 이유만 보인다.

### 데이터에서 확인한 사실 (추측 아님, 라이브 호출로 검증)

- 청약홈 공고 2,861건, 주택형 14,637건 (2026-09-03 수집).
- 특별공급 세대수는 주택형(Mdl)마다 다르다. 공고 단위가 아니다.
- **청년** 세대수는 공공주택(`HOUSE_DTL_SECD='03'` + 특별법 적용)에만 채워진다 (현재 5개 공고).
- **신생아** 세대수는 공공·민영 모두 채워진다 (2024 제도 개편). 민영 49공고·205주택형에 배정 —
  예: "올 뉴 챔피언스시티 1차"(민영·광주) 084 타입 신생아 31세대.
- 주택형 14,637개 중 특공 세부배정이 모두 0인 건 3,656개.
  이 중 3,399개는 특공 자체가 0(일반공급 전용), 257개는 특공은 있으나 세부배정 0
  (대부분 신혼희망타운·행복주택 — 표준 특공 9종으로 쪼개지지 않는 별도 프로그램). 파싱 버그 아님.
- 지역 필터는 주소 문자열(`HSSPLY_ADRES`, "전남광주통합특별시" 같은 통합 명칭 포함)이 아니라
  `SUBSCRPT_AREA_CODE_NM` 을 쓴다.

---

## 실행 방법

### 요구사항

- JDK 21
- 공공데이터포털 인증키 (한국부동산원 청약홈 API 활용신청)
- (선택) `ANTHROPIC_API_KEY` — 자연어 입력·판정 요약용. 없으면 두 기능만 비활성
- (선택) `ADMIN_PASSWORD` — `/api/admin/**` 관리자 계정. 없으면 기동 시 임시 비밀번호를 로그에 출력

### 로컬 실행

```bash
# 두 키 없이도 기동된다 (외부 API 는 빈 리스트, 자연어 입력창은 숨김)
./gradlew bootRun

# 실제 수집 + 자연어 추출 + 판정 요약까지
PUBLICDATA_SERVICE_KEY=<발급키> ANTHROPIC_API_KEY=<발급키> ./gradlew bootRun

# LLM 모델 변경 (기본 claude-sonnet-5)
LLM_MODEL=claude-haiku-4-5 ./gradlew bootRun
```

- 로컬 프로필은 H2 파일 DB(`./data/chungyak`)를 쓴다. `ddl-auto: update`.
- H2 콘솔: `http://localhost:8080/h2-console` (JDBC URL `jdbc:h2:file:./data/chungyak`)
- **키를 `application.yml` 이나 코드에 하드코딩하지 말 것.**

### 첫 데이터 수집

```bash
# /api/admin/** 는 ROLE_ADMIN (HTTP Basic)
curl -u admin:$ADMIN_PASSWORD -X POST http://localhost:8080/api/admin/sync
# → {"pagesFetched":29,"received":2861,"created":2861,"updated":0}
```

이후 `http://localhost:8080/announcements` 에서 공고 목록을 확인한다 (목록·상세·판정 화면은 인증 불필요).

### 테스트

```bash
./gradlew test                                   # 전체 (LLM 통합테스트는 키 없으면 skip)
./gradlew test --tests '*EligibilityEngineTest*' # 규칙 엔진 경계값

# 실제 LLM 호출까지 검증 (비용 발생) — 자연어 추출 + 판정 요약
ANTHROPIC_API_KEY=<키> ./gradlew test --tests '*IntegrationTest*'
```

### 빌드

```bash
./gradlew build   # test 포함
```

---

## 알려진 한계 (정형 데이터 MVP 기준)

- **신혼희망타운**: 청약홈이 이 유형은 특공을 9종으로 쪼개지 않고 총량만 준다
  (`SPSPLY_HSHLDCO` 만 채워지고 `NWWDS_HSHLDCO` 등은 0). 현재 엔진은 신혼희망타운
  공고(97건)에서 아무 매칭도 내지 못한다. 별도 `SpecialSupplyType` + 규칙이 필요하다.
- **소득·자산·거주기간·재당첨 제한**은 판정에 넣지 않았다. 공개된 기본 요건만 본다
  (화면 하단에 고지).
- 규칙 5종만 구현 — 기관추천·이전기관·청년·일반공급은 아직 없다.

## 남은 작업

1. **LH 연동** — 목록 API 활용신청 승인되면 `LhClient` 추가 (응답 구조가 청약홈과 완전히 다름)
2. **벡터 검색** — LH 공고내용(4,000자) 임베딩 + 하이브리드 검색
3. **Docker + CI + 배포**
