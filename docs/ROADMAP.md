# 청약나침반 — 작업 진행 계획

실사용 서비스로 가기 위한 로드맵. 상태: ✅ 완료 / 🔨 진행 / ⬜ 예정.

원칙(불변): **자격 판정은 `rule` 패키지 안에서만.** LLM 은 앞(추출)·뒤(요약)에만.
기능 확장보다 이 경계 유지가 우선. (`CLAUDE.md`)

---

## 완료된 기반 (0단계)

- ✅ 청약홈 수집 (2,861 공고 / 14,637 주택형) — 매일 04시 + `POST /api/admin/sync`
- ✅ 규칙 엔진 5종 (신혼·생애최초·다자녀·노부모·신생아), 결정론적, 경계값 테스트
- ✅ 화면 — 목록/상세/판정폼/판정결과
- ✅ LLM 앞단 — 자연어 → 폼 값 (`ProfileExtractionService`, 애매하면 null)
- ✅ LLM 뒷단 — 판정 근거 → 요약 + 모순 검사 + 결정론적 폴백 (`ExplanationService`)
- ✅ 보안 — `/api/admin/**` ROLE_ADMIN (HTTP Basic)
- ✅ 실데이터 검증 중 발견한 `NewbornRule` 민영 오판정 수정

---

## A. 배포 인프라 — 🔨 진행 중

| # | 항목 | 상태 | 비고 |
|---|---|---|---|
| A1 | Flyway 스키마 관리 | ✅ | `V1__init_schema.sql`. `ddl-auto: validate`. H2/MariaDB 공용 |
| A2 | Actuator | ✅ | `/actuator/health`·`/info` 공개, 그 외 차단 |
| A3 | Dockerfile (멀티스테이지) | ✅ | temurin 21, non-root, `MaxRAMPercentage` |
| A4 | compose.yaml (db + app + Caddy) | ✅ | Caddy 자동 HTTPS. app 은 미노출 |
| A5 | `.env.example` + 시크릿 분리 | ✅ | `.env` gitignore. 필수 5개 값 |
| A6 | GitHub Actions CI | ✅ | build+test / master 시 Docker 빌드 검증 |
| A7 | **실제 배포 (서버·도메인·DNS)** | ⬜ | 사용자 몫. `docs/DEPLOYMENT.md` 참고 |
| A8 | CI → 레지스트리 push → 서버 자동 배포 | ⬜ | A7 이후. GHCR + SSH 배포 잡 |

**남은 것**: A7(서버 준비 + `docker compose up`), A8(자동화). 코드/설정은 준비됨.

---

## B. 판정 신뢰도 — 🔨 (제품의 핵심 가치)

| # | 항목 | 상태 | 비고 |
|---|---|---|---|
| B1a | 소득·자산·거주 입력 스캐폴딩 | ✅ | `ApplicantProfile`+폼에 6개 필드(월소득·가구원수·맞벌이·총자산·자동차·지역거주개월). `IncomeReference` — 도시근로자 소득 대비 % 환산(기준표는 `application.yml`). 결과 화면에 "소득 대비 약 X%" 표시 |
| B1b | 각 특공 규칙에 소득·자산 요건 | ✅ | `IncomeRequirement`(유형별 % 상한, 맞벌이 완화) + `AssetRequirement`(공공주택만, 총자산·자동차). 5개 규칙이 생성자 주입으로 사용. 기준값은 `application.yml`(special-supply). 경계값 테스트 |
| B1c | 재당첨 제한 | ✅ | `ReWinRequirement` — 특공 평생 1회 + 재당첨 제한 기간(투기과열 10년/그외 5년). 소득·자산과 함께 `CommonRequirements` 로 묶어 5개 규칙이 주입 |
| B1d | 청약통장 납입횟수/예치금 | ✅ | `AccountRequirement` — 국민주택 납입 횟수(12/24), 민영 지역별 예치금(서울·부산 300만 등). 가입 기간과 별개. `CommonRequirements` 에 합류 |
| B2 | 일반공급 가점 계산기 | ✅ | `GeneralSupplyScoreCalculator` (rule 패키지, 순수 함수). 무주택 32 + 부양가족 35 + 통장 17 = 84. `/general-supply` 화면. 경계값 테스트 |
| B2b | 일반공급 추첨제 | ⬜ | 가점 외 추첨 물량 안내 |
| B3 | 신혼희망타운 유형 | ⬜ | 별도 `SpecialSupplyType` + 규칙 (현재 엔진이 매칭 못 냄) |
| B4 | 공고 원문 반영 | ⬜ | PDF(4,000자) 파싱 or "세부 요건은 공고문 확인" 고지 강화 |
| B5 | 수집 실패 감지 | ✅ | `SyncStatus` + `SyncHealthIndicator`(bean `sync`) — 예외/저조수집/오래됨 → `/actuator/health` DOWN + ERROR 로그. `/actuator/health/liveness` 는 별개라 라우팅엔 영향 없음 |
| B6 | 외부 API 계약 테스트 | ✅ | `ApplyhomeParseContractTest`(오프라인 픽스처 — 파서 필드명 회귀 방지) + `ApplyhomeApiContractTest`(`@EnabledIfEnvironmentVariable` — 실제 응답이 핵심 필드를 채우는지) |
| B7 | 거주요건 판정 | ✅ | `RegionResidenceRequirement` — 규제지역 24개월/수도권 12개월 계속 거주. 미충족은 FAIL("기타지역 물량은 가능" 안내). `CommonRequirements` 에 합류 |

각 규칙 수정 시 `EligibilityEngineTest` 필수. 새 유형은 구현체 추가 (if-else 금지).

---

## C. 사용자 유지 — ⬜

| # | 항목 | 상태 | 비고 |
|---|---|---|---|
| C0 | UI 디자인 | ✅ | 손 CSS 디자인시스템(토큰·다크모드) `fragments/common.html`. Pretendard, 스티키 헤더, 카드·배지, 반응형(테이블 가로스크롤). 6개 화면 전부 |
| C1 | 회원 + 조건 저장 | ⬜ | 매번 폼 재입력 방지. OAuth(카카오) 로그인 |
| C2 | 새 공고 알림 | ⬜ | "내 조건에 맞는 공고" 이메일/카카오. **유일한 재방문 트리거** |
| C3 | 청약 캘린더 / D-day | ⬜ | 접수일 놓침 방지 |
| C4 | 모바일 UI 다듬기 | 🔨 | 기본 반응형은 C0 에 포함. 필요 시 카드형 목록·바텀시트 등 |
| C5 | 공고 검색 (공고명) + SEO | ⬜ | 유입 |

---

## D. 운영·성능·법적 — ⬜

| # | 항목 | 비고 |
|---|---|---|
| D1 | MyBatis 동적 조회 전환 | 현재 `findOpenWithUnitTypes` 전건 로드 후 메모리 필터 — 누적되면 한계 |
| D2 | 캐싱 | 목록은 하루 1회 갱신 → 캐시 적합 |
| D3 | LLM 비용 통제 | 요약을 매 판정마다 호출 중 → 캐시 or "AI 설명 보기" 버튼 온디맨드 |
| D4 | 데이터 출처 표기 | 공공데이터포털 이용약관 (출처 명시 의무) |
| D5 | 개인정보 처리방침 | C1(조건 저장) 전제 조건 |
| D6 | 면책 고지 강화 | 판정 결과 상단에도 |
| D7 | DB 백업 자동화 | C1 이후 필수 |
| D8 | LH 연동 | API 활용신청 승인 후. `LhClient` + 임대 유형 규칙 (B와 묶어서) |

---

## 권장 순서

1. **A7–A8** (배포) — 지금. 코드는 준비됨, 서버만 붙이면 됨
2. **B1** (특공 규칙 확대: 소득·자산·거주요건) — 이게 진짜 제품. 1~2주 (B2 완료)
3. **C2** (새 공고 알림) — 재방문 이유. 1주
4. D4–D6 (법적) — 공개 전
5. 나머지 트래픽 보면서
