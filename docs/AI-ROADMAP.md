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
- **3b (결정론적)  ✅ 완료:** `EligibilityDecision.improvementHints` — 각 규칙/공통요건이
  수치 차이로 결정론적 계산. `ImprovementHints` 순수 함수 모음(복붙 방지),
  `RequirementCheck.fail(reason, hint)` 로 공통요건에서 전달.
  대상 격차: 통장 가입기간·해당지역 거주기간·재당첨 제한·예치금·납입 횟수 (시간/선택으로 메울 수 있는 것).
  제외: 혼인 7년 초과(회복 불가)·소득/자산 초과(안내할 행동 없음).
  `ExplanationFacts` 가 "개선:" 줄로 LLM 에 전달 + 결과 화면에도 `→` 로 노출(`r-hint`).
  `AnthropicExplainer` 프롬프트: "개선:" 줄이 있으면 녹여 쓰고, 없으면 지어내지 마.
  테스트: `ImprovementHintsTest` + `EligibilityEngineTest`(격차별 안내/회복불가 제외) +
  각 요건 테스트 + `ExplanationFactsTest`.

---

## 방향 3 — 공고문 RAG Q&A  ✅ 슬라이스 1·2·3 + B4(PDF 첨부 파싱)

> "이 공고 잔여세대 신청 조건이 뭐야?" → 공고문 근거 인용해서 답변.
> 판정이 아니라 **정보 검색**이라 LLM 을 자유롭게. 근거(공고문 위치) 인용 필수.

### ⚠️ 원천 데이터 — LH `PAN_DTL_CTS` 는 4000자 전문이 아니다 (2026-09-04 실측)

실제 LH `enabled:true` + sync 돌려보니 (`feat/lh-enable`):
- `PAN_DTL_CTS` 는 임대·잔여세대 공고 기준 **600~1600자짜리 "신청 시 유의사항" 짧은 안내문**이다
  (소득검증 대상 입력 방법, 계약 일정, 문의처 등). 자격요건·세대수·평면도 등 상세는 **첨부 HWP/PDF** 에 있다.
- 8개 LH 공고 중 5개만 100자 이상 본문이 나옴. 청약홈 공고는 원문 필드가 아예 없음.
- sync 중 발견·수정한 버그: `LhClient` 가 `RegulationFlags` 를 안 채워 저장 시 NOT NULL 위반
  (`speculation_overheated`). LH·기본 둘 다 all-false 로 채우도록 수정 + 회귀 테스트.

### B4 — 첨부 공고문 PDF 파싱  ✅ (`feat/rag-pdf-notice`)

LH 상세 응답 `dsAhflInfo` 에 **공고문 PDF 첨부 URL** 이 있다 (`SL_PAN_AHFL_DS_CD_NM="공고문(PDF)"`).
- `PdfNoticeExtractor` — PDFBox 3.0.3. 바이트 → 평문 (매직바이트 체크·암호화 스킵·200p 상한, 순수 함수).
- `LhClient.fetchNoticeContent`: 공고문 PDF 다운로드→추출 우선, 실패 시 `PAN_DTL_CTS` 폴백.
- `dsAhflInfo` 에서 hwp·팸플릿·동호표 아닌 "공고문 .pdf" 만 고름 (`parseNoticePdfUrl`).
- 실측(2026-09-04): 8개 LH 공고문 PDF 에서 **6,300~44,900자** 추출 (기존 `PAN_DTL_CTS` 600~1,600자 대비).
- `announcement_document.raw_text` 컬럼이 VARCHAR(20000) 이라 `AnnouncementDocument.MAX_LEN`(19,500)로 클립
  — 앞부분(자격요건·세대수·일정)이 핵심. 전체 보존 필요 시 TEXT 컬럼 마이그레이션.
- HWP/HWPX 는 미지원 (라이브러리 불안정). PDF 첨부가 대부분 함께 제공됨.
- 테스트: `PdfNoticeExtractorTest`(PDFBox 로 만든 PDF 왕복), `LhClientTest`(dsAhflInfo 픽스처),
  `LhClientLiveTest`(실 PDF 추출 >3000자).

### ⚠️ 무료 Voyage 한도가 대량 인덱싱을 막는다 (2026-09-04)

결제수단 미등록 계정 = **3 RPM + 10K TPM**. 공고문 하나(클립 19,500자 ≈ 13K 토큰)가 TPM 한도를 넘겨
`batch-size` 를 8청크로 낮춰도 여러 요청이 계속 429. → 대량 인덱싱은 **Voyage 결제수단 등록 필요**
(무료 토큰 2억은 유지). 코드는 정상 — 백오프로 재시도하다 그 공고만 스킵(`docsFailed`).

**결정 (2026-09-04):** 임베딩 provider = **Voyage AI**(`voyage-4-lite`), 벡터 저장소 = **앱 메모리 코사인**
(공고 수백 건 규모 — 벡터는 DB에 JSON 문자열로, 네이티브 벡터 타입/pgvector 는 과잉).

### 슬라이스 1 — 수집→인덱싱 파이프라인  ✅ 완료 (PR: feat/rag-ingestion)

- 스키마 `V3__announcement_documents.sql`: `announcement_document`(원문 1:1, text_hash) +
  `document_chunk`(청크 + 임베딩 JSON, `(announcement_id, chunk_index)` 유일)
- `LhClient.fetchNoticeContent` — 상세 응답에서 `PAN_DTL_CTS` (없으면 최장 텍스트),
  HTML 벗겨 평문. `AnnouncementSource` 에 default 메서드 추가(청약홈은 empty).
  (실측: 이 필드는 600~1600자 유의사항 안내문 — 위 "원천 데이터 한계" 참고)
- 수집 시 신규 공고면 원문도 받아 `AnnouncementDocument` 저장 (스케줄러·SyncService)
- `com.portfolio.chungyak.rag`:
  - `ChunkSplitter` — 문단·문장 경계 존중 + overlap (순수 함수)
  - `embedding/EmbeddingClient` + `VoyageEmbeddingClient`(raw HTTP, 모델 `voyage-4-lite`) —
    `VOYAGE_API_KEY` 없으면 빈 `Optional`. 429/5xx 는 지수 백오프 재시도(결제수단 미등록 = 3 RPM 제한)
  - `AnnouncementIndexer.indexPending()` — 원문/모델 바뀐 공고만 재인덱싱 (idempotent)
  - `VectorSearch` — 전체 청크 메모리 코사인 top-K, `searchWithin(announcementId, ...)`
- 관리자 API: `POST /api/admin/rag/reindex`, `GET /api/admin/rag/status|search`
- 설정 `rag.voyage.*` / `rag.chunk.*` / `rag.search.top-k`
- 테스트: `ChunkSplitterTest` `CosineTest` `FloatArrayJsonConverterTest`
  `VoyageEmbeddingClientTest`(응답 파싱) `AnnouncementIndexerTest`(오케스트레이션·idempotency·실패격리)
  `VectorSearchTest`(랭킹) `LhClientTest`(공고문 파싱) + `VoyageEmbeddingIntegrationTest`(키 있을 때)

### 슬라이스 2 — Q&A 화면  ✅ 완료 (PR: feat/rag-qa)

- 공고 상세(`detail.html`)에 "이 공고에 물어보기" 입력창 → `POST /announcements/{id}/qa`
- `rag/DocumentQaService`: `VectorSearch.searchWithin` → 최고 유사도 < `rag.qa.min-score`(0.25)면
  LLM 호출 없이 NO_MATCH. 그 이상이면 상위 발췌 N개(`rag.qa.context-chunks`)를 `[1] [2]` 로 번호 붙여
  `rag/DocumentAnswerer` 에 전달.
- `rag/AnthropicDocumentAnswerer`: 시스템 프롬프트로 "발췌에 없으면 지어내지 말고 확인 안 됨,
  근거 [n] 표기, 자격 판정 금지". `ANTHROPIC_API_KEY` 없으면 빈 `Optional` → 입력창 숨김.
  (SDK 호출 3번째 지점 — CLAUDE.md "두 곳에만"을 "세 곳"으로. Q&A는 판정 아님)
- 답변은 **항상 근거 발췌와 함께** 화면에 표시(`<details>` 토글, 유사도 점수 포함).
- 상태: DISABLED(키 없음) / NO_INDEX(이 공고 원문 없음) / NO_MATCH / ANSWERED
- 테스트: `DocumentQaServiceTest`(관련 없으면 LLM 미호출·발췌는 유지·번호 붙인 발췌 전달·
  응답기 예외 graceful).
- ⚠️ 실제 인덱싱 1회 = LH 공고 수 × 청크 ≈ Voyage `voyage-4-lite` 기준 극소($0.01~0.05).
  LH `enabled: true` + `PUBLICDATA_SERVICE_KEY` + `VOYAGE_API_KEY` 필요. Q&A 답변은 질문당 Anthropic 1콜.

### 슬라이스 3 — 하이브리드 검색 (BM25 + 벡터)  ✅ 완료 (PR: feat/rag-hybrid)

- `rag/Bm25Index` — 형태소 분석기 의존성 없이 한국어를 **글자 bi-gram** 으로 토큰화(CJK IR 통용).
  라틴·숫자("1600-1004", "84.97")는 통째로. 순수 함수, 질의마다 새로 만든다(공고 수백 규모).
- `VectorSearch` — 벡터 순위 + BM25 순위를 **RRF**(Reciprocal Rank Fusion, k=60)로 융합.
  점수 스케일이 달라도 순위만 쓰므로 정규화 불필요. `rag.search.hybrid-weight`(0.6) = 벡터 쪽 가중치.
  `searchWithin` 도 하이브리드 — BM25 IDF 는 항상 전체 코퍼스 기준(공고 하나로 좁히면 IDF 무의미).
- `Hit` 에 `keywordScore`(질의 토큰 겹침 비율 0..1) 추가. Q&A 게이트: 코사인 < `min-score` **이고**
  키워드 < `rag.qa.keyword-min-score`(0.5) 일 때만 NO_MATCH — 키워드가 정확히 맞으면 통과(탈출구).
- 화면: 발췌마다 "의미 0.82 · 키워드 50%" 표시.
- 테스트: `Bm25IndexTest`(토큰화·IDF·coverage), `VectorSearchTest`(키워드가 직교 벡터를 끌어올림·
  searchWithin 스코프+글로벌 IDF), `DocumentQaServiceTest`(키워드 탈출구).

---

## 그 밖의 AI 관련 (ROADMAP D3)

| 항목 | 규모 | 메모 |
|---|---|---|
| **AI 요약 캐시**  ✅ 완료 | — | `ExplanationService` 안에 Caffeine 캐시 — 키는 `ExplanationFacts.format()` 근거 텍스트(공고+모든 판정 근거를 통째로 담음), 값은 **성공한 AI 요약만**. maxSize 1000·TTL 24h·recordStats. 폼 재제출·새로고침이 LLM 재호출로 안 이어짐. FALLBACK(모순 반복·호출 실패)은 캐시 안 함. |
| **AI 요약 온디맨드 버튼**  ✅ 완료 | — | 결과 화면이 로드될 때가 아니라 "🔎 AI 요약 보기" 버튼을 눌렀을 때만 `explain()` 호출. JS 없이 — 버튼이 폼을 `explain=true` 로 재제출, 판정은 결정론적으로 다시 이뤄지고 이번엔 요약까지. `EligibilityController` `explain` 파라미터 + `eligibility-result.html` 히든 폼. 크레딧 없으면 FALLBACK(결정론적 요약)로 표시. |
| **eval 세트**  ✅ 완료 | — | 아래 "eval 실행법" 참고. 추출·요약 품질을 실 LLM 으로 측정, 임계값 미달 시 빌드 실패. 채점 로직은 오프라인 단위테스트로 검증. |
| **모델 비용** | — | `llm.anthropic.model` 기본 `claude-sonnet-5`. 추출·요약은 단발이라 `claude-haiku-4-5` 로 낮추면 흐름 1회 ~6원 (Sonnet ~12원) |

---

## 권장 순서

1. ~~**2b-3a** (개선 경로 프롬프트)~~ ✅ 완료
2. ~~**D3 요약 캐시**~~ ✅ 완료
3. ~~**eval 세트**~~ ✅ 완료
4. ~~**2b-3b** (개선 경로 결정론적 계산)~~ ✅ 완료
5. ~~**방향 3 (RAG)** 슬라이스 1(수집·인덱싱) · 2(Q&A 화면) · 3(하이브리드 검색)~~ ✅ 완료
6. ~~파이프라인 라이브 확인~~ ✅ (2026-09-04, `RagPipelineIntegrationTest` — 실 Voyage `voyage-4-lite`,
   공고문 → 청크 → 임베딩 → 하이브리드 검색 동작 확인. 무관 질문은 의미 0.07·키워드 0%)
7. 선택: Q&A 화면 시각 검증(LH 인덱싱 데이터 필요) · AI 요약 온디맨드 버튼 · eval 임계값 실측
8. 규모 커지면: 역색인/캐시(BM25 질의마다 재구축 중) · 네이티브 벡터 인덱스
   · Voyage 결제수단 등록(3 RPM → 표준 rate limit, 무료 토큰은 유지)

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
