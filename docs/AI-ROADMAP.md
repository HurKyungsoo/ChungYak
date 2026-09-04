# 청약나침반 — AI 활용 로드맵

이 프로젝트에서 AI(LLM)를 "더 어필"하기 위한 계획. `docs/ROADMAP.md` 의 AI 부분을 따로 정리한 것.

> **큰 방향: "AI가 판정을 안 해서 오히려 믿을 수 있다"**
> AI를 판정 자리에 넣으면 프로젝트 존재 이유가 사라진다. 경계를 지키면서 AI가 빛나는 자리를 키운다.

## ★ 절대 경계 (CLAUDE.md)

- 자격 판정은 `rule` 패키지 안에서만. 결정론적.
- LLM 은 **앞**(자연어→`ApplicantProfile`)·**뒤**(`EligibilityDecision`→자연어 설명) 두 자리에만.
- AI 가 "자격이 됩니다/안 됩니다"를 **말하게 하지 않는다** — 그 문장은 항상 엔진 결과 그대로, AI 는 *이유*만 rephrase.
- AI 설명에 규칙 근거에 없는 새 사실·수치 추가 금지. (`llm/ContradictionCheck` 가 사후 검사)

---

## 방향 1 — 자연어 입력 + 추출 검증 UI  ✅ 완료

폼 20+ 필드를 다 채우게 하는 건 이탈 포인트.
- "결혼 3년차, 아이 둘, 청약통장 2년, 무주택입니다" → 폼 자동 채움
- **어필 핵심 = "채운 값을 보여주고 고치게 하는 화면"** — AI 가 틀릴 수 있다는 걸 인정하고 사용자가 확정. 이 검증 단계 자체가 신뢰 장치.
- 못 뽑은 필드 명시 ("소득은 언급이 없어 비워 뒀습니다") → 판정 불가와 자연스럽게 연결.

**구현:** `llm/ProfileExtractionService` + `AnthropicProfileCaller`(구조화 출력, 애매하면 null),
`POST /announcements/{id}/eligibility/extract` (폼 화면으로만 리턴 — 추출이 곧 판정이 되지 않음),
`eligibility-form.html` 의 `${extractionAvailable}` / `${extraction}` 훅.

---

## 방향 2 — 판정 근거 설명 + "이렇게 하면 자격이 생겨요"

규칙 엔진의 `satisfied/failed/missing` 을 AI 가 문단으로:
> "신혼부부 특공은 혼인 7년 이내가 조건인데 현재 8년 2개월이 지났습니다. 대신 생애최초는 조건을 모두 충족합니다."

**한 발 더 — 개선 경로:**
> "청약통장을 6개월 더 유지하면 이 공고(규제지역)의 24개월 요건을 채웁니다."

단순 O/X → **액션 가이드**로 격상 = 어필 포인트.

### 2a. 기본 설명  ✅ 완료
`llm/ExplanationService` + `ExplanationFacts`(확정 근거 텍스트화) + `AnthropicExplainer`
+ `ContradictionCheck`(요약이 판정과 모순되면 1회 재생성 → 그래도 모순이면 `FallbackSummary` 결정론적 대체)
+ `ANTHROPIC_API_KEY` 없으면 DISABLED (요약 영역 안 보임).
화면: `eligibility-result.html` 의 "AI 요약" 카드 — *"판정은 규칙 엔진이 했고 AI 는 근거를 문장으로 정리만 했습니다"* 배지.

### 2b. 개선 경로

- **3a (프롬프트만)  ✅ 완료:** `failed` 이유 문장에 이미 두 수치가 다 있음 ("12개월로 요건(24개월)에 미달").
  `ExplanationFacts.format()` 마지막 지시 + `AnthropicExplainer.SYSTEM_PROMPT` 를
  *"미충족·미확인 항목은 근거 문장의 두 수치만으로 '얼마나 모자라는지, 무엇을 채우면 충족되는지'까지 설명 —
  근거에 없는 새 수치·기한·금액·조건 금지, 판정은 그대로"* 로 변경.
  `ContradictionCheck` 가 자격 뒤집기는 이미 방어.
  테스트: `ExplanationFactsTest`(프롬프트에 두 수치가 담기는지 결정론적 검증) +
  `MatchResults.shortfall()`(규제지역·통장 12/24개월 부분 매칭) +
  `ExplanationIntegrationTest.explainsShortfallWithoutFlippingVerdict`(실 LLM, 키 있을 때).
- **3b (결정론적, 반나절~1일):** `EligibilityDecision.improvementHints` 를 각 규칙이 결정론적으로 계산
  (예: `MIN_ACCOUNT_MONTHS_REGULATED - accountMonths` = 12). 엔진이 `ExplanationFacts` 로 전달, AI 는 문장화만.
  → `EligibilityDecision` + 5개 규칙 + `EligibilityEngineTest` 수정. 이게 프로젝트 thesis 에 더 부합.
  **먼저 3a 로 문장 품질 보고, 필요하면 3b.**

---

## 방향 3 — 공고문 RAG Q&A (LH 4000자)  🚧 진행 중

LH 상세 API `dsEtcInfo.PAN_DTL_CTS`(공고내용 전문, 4000자) 임베딩:
> "이 공고 잔여세대 신청 조건이 뭐야?" → 공고문 근거 인용해서 답변

판정이 아니라 **정보 검색**이라 LLM 을 자유롭게. 근거(공고문 위치) 인용 필수.

**결정 (2026-09-04):** 임베딩 provider = **Voyage AI**(`voyage-3-lite`), 벡터 저장소 = **앱 메모리 코사인**
(공고 수백 건 규모 — 벡터는 DB에 JSON 문자열로, 네이티브 벡터 타입/pgvector 는 과잉).

### 슬라이스 1 — 수집→인덱싱 파이프라인  ✅ 완료 (PR: feat/rag-ingestion)

- 스키마 `V3__announcement_documents.sql`: `announcement_document`(원문 1:1, text_hash) +
  `document_chunk`(청크 + 임베딩 JSON, `(announcement_id, chunk_index)` 유일)
- `LhClient.fetchNoticeContent` — 상세 응답에서 `PAN_DTL_CTS` (없으면 최장 텍스트),
  HTML 벗겨 평문. `AnnouncementSource` 에 default 메서드 추가(청약홈은 empty).
- 수집 시 신규 공고면 원문도 받아 `AnnouncementDocument` 저장 (스케줄러·SyncService)
- `com.portfolio.chungyak.rag`:
  - `ChunkSplitter` — 문단·문장 경계 존중 + overlap (순수 함수)
  - `embedding/EmbeddingClient` + `VoyageEmbeddingClient`(raw HTTP) — `VOYAGE_API_KEY` 없으면 빈 `Optional`
  - `AnnouncementIndexer.indexPending()` — 원문/모델 바뀐 공고만 재인덱싱 (idempotent)
  - `VectorSearch` — 전체 청크 메모리 코사인 top-K, `searchWithin(announcementId, ...)`
- 관리자 API: `POST /api/admin/rag/reindex`, `GET /api/admin/rag/status|search`
- 설정 `rag.voyage.*` / `rag.chunk.*` / `rag.search.top-k`
- 테스트: `ChunkSplitterTest` `CosineTest` `FloatArrayJsonConverterTest`
  `VoyageEmbeddingClientTest`(응답 파싱) `AnnouncementIndexerTest`(오케스트레이션·idempotency·실패격리)
  `VectorSearchTest`(랭킹) `LhClientTest`(공고문 파싱) + `VoyageEmbeddingIntegrationTest`(키 있을 때)

### 슬라이스 2 — Q&A 화면  ⬜ 남음

- 공고 상세에 "이 공고에 물어보기" 입력창 → `VectorSearch.searchWithin` → 상위 청크를
  컨텍스트로 `AnthropicExplainer` 스타일 답변 (근거 청크 인용 필수, 새 사실 금지)
- 하이브리드(BM25 + 벡터)는 그 다음. `docs/ROADMAP.md` B4.
- ⚠️ 실제 인덱싱 1회 = LH 공고 수 × 청크 ≈ Voyage `voyage-3-lite` 기준 극소($0.01~0.05).
  LH `enabled: true` + `PUBLICDATA_SERVICE_KEY` + `VOYAGE_API_KEY` 필요.

---

## 그 밖의 AI 관련 (ROADMAP D3)

| 항목 | 규모 | 메모 |
|---|---|---|
| **AI 요약 캐시**  ✅ 완료 | — | `ExplanationService` 안에 Caffeine 캐시 — 키는 `ExplanationFacts.format()` 근거 텍스트(공고+모든 판정 근거를 통째로 담음), 값은 **성공한 AI 요약만**. maxSize 1000·TTL 24h·recordStats. 폼 재제출·새로고침이 LLM 재호출로 안 이어짐. FALLBACK(모순 반복·호출 실패)은 캐시 안 함. |
| **AI 요약 온디맨드 버튼** | 반나절 | 남음. 결과 화면 로드 시가 아니라 "AI 설명 보기" 클릭 시 호출 — 별도 엔드포인트 + JS 필요. 캐시가 있어 급하진 않음. |
| **eval 세트**  ✅ 완료 | — | 아래 "eval 실행법" 참고. 추출·요약 품질을 실 LLM 으로 측정, 임계값 미달 시 빌드 실패. 채점 로직은 오프라인 단위테스트로 검증. |
| **모델 비용** | — | `llm.anthropic.model` 기본 `claude-sonnet-5`. 추출·요약은 단발이라 `claude-haiku-4-5` 로 낮추면 흐름 1회 ~6원 (Sonnet ~12원) |

---

## 권장 순서

1. ~~**2b-3a** (개선 경로 프롬프트)~~ ✅ 완료
2. ~~**D3 요약 캐시**~~ ✅ 완료
3. ~~**eval 세트**~~ ✅ 완료
4. **2b-3b** (개선 경로 결정론적 계산) — 3a 문장 품질을 eval 로 보고, 필요하면
5. **방향 3 (RAG)** — 별도 큰 프로젝트, 임베딩 provider 결정부터

---

## eval 실행법

품질 회귀를 잡는 두 세트. **실 LLM 을 호출하므로 `ANTHROPIC_API_KEY` 가 있을 때만 돈다**
(없으면 skip). CI 자동 실행은 안 한다 — 프롬프트·스키마·모델을 바꾼 뒤 수동으로 돌린다.

```bash
# 채점 로직만 (오프라인, 키 불필요) — 데이터셋 온전성 + 오라클/널 sanity
./gradlew test --tests '*ExtractionEvalScorerTest*'

# 품질 측정 (실 LLM, 유료) — 스코어카드가 표준출력에 찍힘
ANTHROPIC_API_KEY=sk-... ./gradlew test --tests '*EvalTest' -i
#   모델 바꾸기:   LLM_MODEL=claude-haiku-4-5
#   요약 반복 수:  EVAL_REPS=3   (기본 2)
```

- **추출** (`ProfileExtractionEvalTest`, 데이터셋 `src/test/resources/eval/profile-extraction-cases.json`):
  자연어 18문장 → `ExtractedProfile`. 필드 정확도 / **null 원칙 준수**(언급 없는 필드를 추측하지 않음) /
  케이스 통과율. 개월 수는 허용구간, "애매" 케이스는 반드시 null.
- **요약** (`ExplanationEvalTest`): 5 시나리오 × `EVAL_REPS`. `AnthropicExplainer` 원문을
  규칙 엔진 결과와 대조 — **모순 없음 비율**(자격 유무 안 뒤집음)이 핵심, 그 외 기대는
  `MatchResult` 에서 결정론적으로 끌어냄(기대값 하드코딩 없음).
- 비용: 1회 ≈ 추출 18콜 + 요약 10콜 ≈ `claude-sonnet-5` 기준 수십 원.
- 임계값은 각 테스트의 `MIN_*` 상수. **첫 실행 뒤 스코어카드를 보고 조정**한다.

## 착수 전 체크

- **이 프로젝트는 다른 세션이 master 에 직접 커밋하며 병렬 진행 중.** 브랜치 전 반드시 `git fetch origin && git log --oneline origin/master`.
- 로컬 `data/*.mv.db` 가 Flyway 도입(`f8f5204`) 전 것이면 부팅 실패 → `data/` 폴더 삭제.
