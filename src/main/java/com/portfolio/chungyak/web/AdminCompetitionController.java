package com.portfolio.chungyak.web;

import com.portfolio.chungyak.service.CompetitionSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 당첨가점·경쟁률 기록 수집을 지금 돌린다 (#34) — {@code /api/admin/**} = ROLE_ADMIN (SecurityConfig).
 *
 *   curl -u admin:$ADMIN_PASSWORD -X POST 'https://…/api/admin/competition/sync'
 *
 * 매일 04:30 배치와 같다. 표가 비어 있으면 전체(≈86회 호출, 몇 분), 아니면 최신 몇 페이지.
 */
@RestController
@RequestMapping("/api/admin/competition")
@RequiredArgsConstructor
public class AdminCompetitionController {

    private final CompetitionSyncService syncService;

    @PostMapping("/sync")
    public CompetitionSyncService.Report sync() {
        return syncService.sync();
    }
}
