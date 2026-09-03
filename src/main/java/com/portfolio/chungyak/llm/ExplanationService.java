package com.portfolio.chungyak.llm;

import com.portfolio.chungyak.rule.EligibilityEngine.MatchResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * {@link MatchResult} -> 자연어 요약.
 *
 * ★ CLAUDE.md 절대 규칙의 "뒤" 쪽이다.
 *   - 판정은 이미 규칙 엔진이 끝냈다. 여기서 LLM 은 그 근거를 문장으로 재구성만 한다.
 *   - {@link ContradictionCheck} 가 요약이 확정 판정과 어긋나는지 사후 검사한다.
 *     어긋나면 한 번 재생성하고, 그래도 어긋나면 {@link FallbackSummary}(결정론적)로 대체.
 *   - ANTHROPIC_API_KEY 가 없으면 DISABLED — 화면은 규칙 기반 이유만 보여준다.
 */
@Slf4j
@Service
public class ExplanationService {

    private static final int MAX_ATTEMPTS = 2;

    private final Optional<LlmExplainer> explainer;

    public ExplanationService(Optional<LlmExplainer> explainer) {
        this.explainer = explainer;
    }

    public boolean isAvailable() {
        return explainer.isPresent();
    }

    public ExplanationResult explain(MatchResult result) {
        if (explainer.isEmpty()) {
            return ExplanationResult.disabled();
        }

        String facts = ExplanationFacts.format(result);

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
