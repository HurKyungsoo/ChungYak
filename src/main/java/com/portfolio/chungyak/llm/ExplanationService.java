package com.portfolio.chungyak.llm;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.portfolio.chungyak.llm.ExplanationResult.Status;
import com.portfolio.chungyak.rule.EligibilityEngine.MatchResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * {@link MatchResult} -> 자연어 요약.
 *
 * ★ CLAUDE.md 절대 규칙의 "뒤" 쪽이다.
 *   - 판정은 이미 규칙 엔진이 끝냈다. 여기서 LLM 은 그 근거를 문장으로 재구성만 한다.
 *   - {@link ContradictionCheck} 가 요약이 확정 판정과 어긋나는지 사후 검사한다.
 *     어긋나면 한 번 재생성하고, 그래도 어긋나면 {@link FallbackSummary}(결정론적)로 대체.
 *   - ANTHROPIC_API_KEY 가 없으면 DISABLED — 화면은 규칙 기반 이유만 보여준다.
 *
 * <b>캐시:</b> 판정 근거 텍스트({@link ExplanationFacts#format})가 같으면 요약도 같다.
 * 그 텍스트를 키로 성공한 AI 요약만 캐시한다 — 폼을 그대로 다시 제출하거나 새로고침해도
 * LLM 을 다시 부르지 않는다. FALLBACK(모순 반복·호출 실패)은 캐시하지 않는다:
 * 일시적 오류일 수 있고, 다음 호출에서 정상 요약이 나올 여지를 남긴다.
 */
@Slf4j
@Service
public class ExplanationService {

    private static final int MAX_ATTEMPTS = 2;

    /** 근거 텍스트 -> 성공한 AI 요약. 키가 판정 내용 전체를 담으므로 오래 둬도 안전하다. */
    private static final long CACHE_MAX_ENTRIES = 1_000;
    private static final Duration CACHE_TTL = Duration.ofHours(24);

    private final Optional<LlmExplainer> explainer;
    private final Cache<String, ExplanationResult> cache;

    public ExplanationService(Optional<LlmExplainer> explainer) {
        this.explainer = explainer;
        this.cache = Caffeine.newBuilder()
                .maximumSize(CACHE_MAX_ENTRIES)
                .expireAfterWrite(CACHE_TTL)
                .recordStats()
                .build();
    }

    public boolean isAvailable() {
        return explainer.isPresent();
    }

    public ExplanationResult explain(MatchResult result) {
        if (explainer.isEmpty()) {
            return ExplanationResult.disabled();
        }

        String facts = ExplanationFacts.format(result);

        ExplanationResult cached = cache.getIfPresent(facts);
        if (cached != null) {
            log.debug("판정 요약 캐시 적중 (entries={}, hitRate={})",
                    cache.estimatedSize(), String.format("%.2f", cache.stats().hitRate()));
            return cached;
        }

        ExplanationResult fresh = generate(facts, result);
        if (fresh.status() == Status.AI) {
            cache.put(facts, fresh);
        }
        return fresh;
    }

    private ExplanationResult generate(String facts, MatchResult result) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            String text;
            try {
                text = explainer.get().explain(facts);
            } catch (RuntimeException e) {
                log.warn("판정 요약 생성 실패 — {}", e.toString());
                return ExplanationResult.fallback(FallbackSummary.of(result));
            }

            Optional<String> contradiction = ContradictionCheck.check(result, text);
            if (contradiction.isEmpty()) {
                return ExplanationResult.ai(text);
            }
            log.warn("판정 요약 모순 감지 (attempt {}/{}): {}", attempt, MAX_ATTEMPTS, contradiction.get());
        }

        log.info("판정 요약 — 모순이 반복돼 결정론적 요약으로 대체");
        return ExplanationResult.fallback(FallbackSummary.of(result));
    }
}
