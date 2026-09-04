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

### 2b. 개선 경로  ⬜ 미구현
현재 `AnthropicExplainer.SYSTEM_PROMPT` 가 *"새로운 조건·추천·다음 단계를 지어내지 마라"* 로 막아둠.

- **3a (프롬프트만, 30분):** `failed` 이유 문장에 이미 수치가 다 있음 ("12개월로 요건 24개월에 미달").
  `ExplanationFacts.format()` 마지막 지시 + `AnthropicExplainer` 시스템 프롬프트를
  *"미충족·미확인 항목은 근거 문장의 수치를 근거로 '얼마나 부족한지, 무엇을 하면 충족되는지'도 설명 (새 수치 만들지 말 것)"* 으로 변경.
  `ContradictionCheck` 가 자격 뒤집기는 이미 방어. 테스트 추가.
- **3b (결정론적, 반나절~1일):** `EligibilityDecision.improvementHints` 를 각 규칙이 결정론적으로 계산
  (예: `MIN_ACCOUNT_MONTHS_REGULATED - accountMonths` = 12). 엔진이 `ExplanationFacts` 로 전달, AI 는 문장화만.
  → `EligibilityDecision` + 5개 규칙 + `EligibilityEngineTest` 수정. 이게 프로젝트 thesis 에 더 부합.
  **먼저 3a 로 문장 품질 보고, 필요하면 3b.**

---

## 방향 3 — 공고문 RAG Q&A (LH 4000자)  ⬜ 미착수 (큰 작업)

LH 상세 API `dsEtcInfo.PAN_DTL_CTS`(공고내용 전문, 4000자) + 첨부 HWP/PDF 임베딩:
> "이 공고 잔여세대 신청 조건이 뭐야?" → 공고문 근거 인용해서 답변

판정이 아니라 **정보 검색**이라 LLM 을 자유롭게. 근거(공고문 위치) 인용 필수.

- ⚠️ **Anthropic 은 임베딩 API 가 없다.** Voyage AI(Anthropic 파트너) 또는 로컬 한국어 임베딩(`bge-m3`, `ko-sroberta` 등) + pgvector 필요. **provider 결정부터.**
- LH 원천은 이미 확보 (`LhClient` 가 상세 API 를 호출함, 현재는 유형만 쓰고 `PAN_DTL_CTS` 는 미사용).
- 하이브리드 검색 (BM25 + 벡터). `docs/ROADMAP.md` B4 / 남은작업 #2.

---

## 그 밖의 AI 관련 (ROADMAP D3)

| 항목 | 규모 | 메모 |
|---|---|---|
| **AI 요약 온디맨드/캐시** | 반나절 | `EligibilityController.evaluate()` 가 매 판정마다 `explanationService.explain()` 호출 중. "AI 설명 보기" 버튼 온디맨드 or `(공고ID + ApplicantProfile 해시) → 요약` 캐시(Caffeine) |
| **eval 세트** | 반나절 | 추출/요약 품질 측정. 자연어 20문장 + 기대 필드값, 모순 케이스. `/claude-api build-eval`. 회귀 방지 + "측정했다" 근거 |
| **모델 비용** | — | `llm.anthropic.model` 기본 `claude-sonnet-5`. 추출·요약은 단발이라 `claude-haiku-4-5` 로 낮추면 흐름 1회 ~6원 (Sonnet ~12원) |

---

## 권장 순서

1. **2b-3a** (개선 경로 프롬프트) — 30분, 차별성 대비 가성비 최고
2. **D3 온디맨드/캐시** — 지금 실제 비용 새는 중
3. **eval 세트** — 위 둘 검증
4. **방향 3 (RAG)** — 별도 큰 프로젝트, 임베딩 provider 결정부터

## 착수 전 체크

- **이 프로젝트는 다른 세션이 master 에 직접 커밋하며 병렬 진행 중.** 브랜치 전 반드시 `git fetch origin && git log --oneline origin/master`.
- 로컬 `data/*.mv.db` 가 Flyway 도입(`f8f5204`) 전 것이면 부팅 실패 → `data/` 폴더 삭제.
