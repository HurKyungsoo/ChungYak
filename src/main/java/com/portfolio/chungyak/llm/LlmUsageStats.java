package com.portfolio.chungyak.llm;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * 외부 LLM 사용 관찰용 카운터 (#25 운영 관찰) — 자연어 추출·공고문 Q&A 의 결과 분포, 429(한도 초과) 횟수,
 * 모델별 토큰 합계. 무료 등급은 분당 8K·하루 200K 토큰이 상한이라(#32) 얼마나 쓰고 얼마나 막히는지 봐야 한다.
 *
 * 메모리에만 둔다 — 재배포하면 0 부터 다시 센다({@link #startedAt}). 판정과 무관하고 개인정보(입력 문장)는 담지 않는다.
 * 관리자 API {@code GET /api/admin/llm/stats} 로 본다.
 */
@Component
public class LlmUsageStats {

    private final Map<String, LongAdder> counters = new ConcurrentHashMap<>();
    private final Instant startedAt;

    public LlmUsageStats(Clock clock) {
        this.startedAt = clock.instant();
    }

    /** 예: "extraction.EXTRACTED", "qa.ANSWERED", "groq.429.openai/gpt-oss-120b" */
    public void count(String key) {
        add(key, 1);
    }

    public void tokens(String model, long total) {
        add("tokens." + model, total);
        add("calls." + model, 1);
    }

    private void add(String key, long n) {
        counters.computeIfAbsent(key, k -> new LongAdder()).add(n);
    }

    public Instant startedAt() {
        return startedAt;
    }

    /** 키 이름순 스냅샷 */
    public Map<String, Long> snapshot() {
        Map<String, Long> out = new TreeMap<>();
        counters.forEach((k, v) -> out.put(k, v.sum()));
        return out;
    }
}
