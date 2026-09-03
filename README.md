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

---

## 아키텍처

```
                    [자연어 질문]                         [판정 결과]
                         │                                    ▲
                         ▼ (LLM: 예정)                         │ (LLM: 예정)
  청약홈/LH API ──► 어댑터 ──► Announcement ──►  ApplicantProfile
   (ApplyhomeClient)  (PublicDataParser)  (JPA)        │
        │                                              ▼
        │                                     ┌──────────────────┐
        └── AnnouncementSyncScheduler ───────►│ EligibilityEngine │  ◄── 여기 안에서만 판정
             (수동 /api/admin/sync + 매일 04시) │   (rule 패키지)    │
                                              └──────────────────┘
                                                       │
                                          EligibilityDecision (이유 필수)
                                                       │
                                              EligibilityResultView (화면 재배열)
```

### 레이어

- **external** — 청약홈(odcloud)·LH(data.go.kr) 응답 구조 차이를 흡수해 `ExternalAnnouncement` 로 정규화.
  파싱 실패는 예외를 던지지 않고 그 건만 건너뛴다.
- **domain** — `Announcement` ─< `UnitType` (주택형). 특별공급 세대수(`SupplyBreakdown`)는
  **공고가 아니라 주택형마다** 다르므로 `UnitType` 에 매단다.
- **rule** — `EligibilityRule` 구현체 하나 = 특별공급 유형 하나.
  현재 신혼부부·생애최초·다자녀·노부모부양·신생아 5종. 새 유형은 구현체 추가로만 늘린다.
- **service** — 수집(`AnnouncementSyncService`), 조회(`AnnouncementQueryService`).
- **web** — 컨트롤러 3개(관리자 sync / 공고 목록·상세 / 자격 판정). 비즈니스 로직 없음.

### 화면

| 경로 | 설명 |
|---|---|
| `GET /announcements` | 접수중·예정 공고 목록, 지역·주택유형 필터 |
| `GET /announcements/{id}` | 공고 상세 + 주택형별 특별공급 세대수 표 |
| `GET /announcements/{id}/eligibility` | 조건 입력 폼 |
| `POST /announcements/{id}/eligibility` | 판정 결과 — 주택형별 신청 가능 유형·배정 세대수, "자격은 되지만 물량 없음", 전체 판정 근거 |
| `POST /api/admin/sync` | 청약홈 즉시 수집 (응답: `pagesFetched/received/created/updated`) |

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
  `EligibilityEngineTest` 8건이 이 경계를 지킨다 — 깨지면 규칙이 바뀐 것이다.

### 데이터에서 확인한 사실 (추측 아님, 라이브 호출로 검증)

- 청약홈 공고 약 2,861건, 주택형 약 14,637건.
- 특별공급 세대수는 주택형(Mdl)마다 다르다. 공고 단위가 아니다.
- 청년·신생아 세대수는 **공공주택(`HOUSE_DTL_SECD='03'` + 특별법 적용)에만** 채워진다.
  민영주택 공고에서 이 값이 0 인 것은 파싱 버그가 아니라 정상이다.
- 지역 필터는 주소 문자열(`HSSPLY_ADRES`, "전남광주통합특별시" 같은 통합 명칭 포함)이 아니라
  `SUBSCRPT_AREA_CODE_NM` 을 쓴다.

---

## 실행 방법

### 요구사항

- JDK 21
- 공공데이터포털 인증키 (한국부동산원 청약홈 API 활용신청)

### 로컬 실행

```bash
# 인증키 없이도 기동은 된다 (외부 API 는 빈 리스트 반환)
./gradlew bootRun

# 실제 수집까지 하려면 환경변수로 키 주입
PUBLICDATA_SERVICE_KEY=<발급키> ./gradlew bootRun
```

- 로컬 프로필은 H2 파일 DB(`./data/chungyak`)를 쓴다. `ddl-auto: update`.
- H2 콘솔: `http://localhost:8080/h2-console` (JDBC URL `jdbc:h2:file:./data/chungyak`)
- **키를 `application.yml` 이나 코드에 하드코딩하지 말 것.**

### 첫 데이터 수집

```bash
curl -X POST http://localhost:8080/api/admin/sync
# → {"pagesFetched":29,"received":2861,"created":2861,"updated":0}
```

이후 `http://localhost:8080/announcements` 에서 공고 목록을 확인한다.

### 테스트

```bash
./gradlew test                                   # 전체
./gradlew test --tests '*EligibilityEngineTest*' # 규칙 엔진 경계값
```

### 빌드

```bash
./gradlew build   # test 포함
```

---

## 남은 작업

1. **LH 연동** — 목록 API 활용신청 승인되면 `LhClient` 추가 (응답 구조가 청약홈과 완전히 다름)
2. **LLM 연동** — 자연어 → `ApplicantProfile` 추출, 판정 결과 → 자연어 설명
3. **벡터 검색** — LH 공고내용(4,000자) 임베딩 + 하이브리드 검색
4. **Security** — `/api/admin/**` 에 `ROLE_ADMIN` (현재는 스켈레톤이라 열려 있음)
5. **Docker + CI + 배포**
