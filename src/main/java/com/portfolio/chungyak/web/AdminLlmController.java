package com.portfolio.chungyak.web;

import com.portfolio.chungyak.llm.LlmUsageStats;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 외부 LLM 사용 관찰 API (#25) — {@code /api/admin/**} = ROLE_ADMIN (SecurityConfig).
 *
 *   curl -u admin:$ADMIN_PASSWORD https://…/api/admin/llm/stats
 *
 * 자연어 추출·공고문 Q&A 결과 분포, 429(한도 초과) 횟수, 모델별 호출·토큰 합계. 재배포하면 0 부터 다시 센다.
 */
@RestController
@RequestMapping("/api/admin/llm")
@RequiredArgsConstructor
public class AdminLlmController {

    private final LlmUsageStats stats;

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("since", stats.startedAt().toString());
        out.put("counters", stats.snapshot());
        return out;
    }
}
