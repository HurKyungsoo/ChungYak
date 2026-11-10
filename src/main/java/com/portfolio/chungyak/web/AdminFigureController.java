package com.portfolio.chungyak.web;

import com.portfolio.chungyak.service.FigureWatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 제도 금액 감시 — 열린 분양 공고의 공고문 금액과 설정(application.yml)을 지금 대조한다. {@code /api/admin/**} = ROLE_ADMIN.
 *
 *   curl -u admin:$ADMIN_PASSWORD https://…/api/admin/figures/check
 *
 * 매일 04:50 배치와 같다. findings 가 비어 있으면 설정이 공고문과 맞다. certain=true 면 설정 갱신이 필요하다(CLAUDE.md 절대 규칙 6).
 */
@RestController
@RequestMapping("/api/admin/figures")
@RequiredArgsConstructor
public class AdminFigureController {

    private final FigureWatchService watchService;

    @GetMapping("/check")
    public FigureWatchService.Report check() {
        return watchService.check();
    }
}
