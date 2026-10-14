package com.portfolio.chungyak.web;

import com.portfolio.chungyak.rag.QaTopicStats;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 공고문 Q&A 주제별 집계 조회 (#40) — {@code /api/admin/**} = ROLE_ADMIN (SecurityConfig).
 *
 *   curl -u admin:$ADMIN_PASSWORD 'https://…/api/admin/qa/topics?days=30'
 *
 * 주제 순위·주제별 결과 분포와 못 찾음 비율, 자주 묻는 질문 버튼별 클릭 수. 질문 원문은 저장하지 않아 볼 수 없다.
 * DB 에 쌓이므로 재배포해도 이어진다(인메모리 {@code /api/admin/llm/stats} 와 다르다).
 */
@RestController
@RequestMapping("/api/admin/qa")
@RequiredArgsConstructor
public class AdminQaController {

    private final QaTopicStats topicStats;

    @GetMapping("/topics")
    public Map<String, Object> topics(@RequestParam(value = "days", defaultValue = "30") int days) {
        return topicStats.summary(Math.min(Math.max(days, 1), 366));
    }
}
