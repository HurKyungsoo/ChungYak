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
 * `/api/admin/**` 는 {@link com.portfolio.chungyak.config.SecurityConfig} 에서
 * `ROLE_ADMIN` (HTTP Basic) 을 요구한다. 계정은 `ADMIN_USERNAME` / `ADMIN_PASSWORD`
 * 환경변수로 주입한다(미설정 시 임시 비밀번호를 기동 로그에 남김).
 *
 *   curl -u admin:$ADMIN_PASSWORD -X POST http://localhost:8080/api/admin/sync
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
