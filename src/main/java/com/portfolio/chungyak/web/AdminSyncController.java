package com.portfolio.chungyak.web;

import com.portfolio.chungyak.service.AnnouncementSyncScheduler;
import com.portfolio.chungyak.service.AnnouncementSyncScheduler.SyncAlreadyRunningException;
import com.portfolio.chungyak.service.AnnouncementSyncScheduler.SyncReport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 관리자 동기화 API.
 *
 * TODO(security): 지금은 스켈레톤 단계라 누구나 호출할 수 있다.
 *   Spring Security 를 붙이면 이 컨트롤러 전체에 ROLE_ADMIN 을 요구하도록 바꾼다.
 *   (@PreAuthorize("hasRole('ADMIN')") 또는 SecurityFilterChain 에서 /api/admin/** 보호)
 */
@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminSyncController {

    private final AnnouncementSyncScheduler syncScheduler;

    /**
     * 청약홈 공고를 지금 즉시 수집한다.
     * 응답은 스케줄러가 낸 SyncReport 를 그대로 반환한다
     * (pagesFetched / received / created / updated).
     */
    @PostMapping("/sync")
    public SyncReport sync() {
        log.info("수동 동기화 요청 수신");
        return syncScheduler.runSync();
    }

    /** 이미 동기화가 돌고 있으면 409 로 알린다 — 재시도는 클라이언트가 판단한다. */
    @ExceptionHandler(SyncAlreadyRunningException.class)
    public ResponseEntity<Map<String, String>> handleAlreadyRunning(SyncAlreadyRunningException e) {
        log.info("동기화 중복 요청 거부: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "SYNC_ALREADY_RUNNING", "message", e.getMessage()));
    }
}
