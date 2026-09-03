package com.portfolio.chungyak.service;

import com.portfolio.chungyak.service.AnnouncementSyncScheduler.SyncReport;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 마지막 수집 시도의 결과를 들고 있는다.
 *
 * 수집이 조용히 실패하면(키 만료, API 다운, 스키마 변경) 데이터가 며칠씩 낡는다.
 * {@link SyncHealthIndicator} 가 이 상태를 읽어 /actuator/health 를 DOWN 으로 만들고,
 * 실패·이상은 ERROR 로그로 남긴다.
 */
@Component
public class SyncStatus {

    public enum Outcome {
        /** 아직 한 번도 안 돌림 */
        NEVER_RUN,
        /** 정상 수집 */
        SUCCESS,
        /** 돌긴 했으나 수집 건수가 비정상적으로 적음 (API 이상 의심) */
        LOW_YIELD,
        /** 예외로 중단됨 */
        FAILED
    }

    private volatile Outcome outcome = Outcome.NEVER_RUN;
    private volatile Instant lastAttemptAt;
    private volatile Instant lastSuccessAt;
    private volatile SyncReport lastReport;
    private volatile String detail;

    void recordAttempt(Instant now) {
        this.lastAttemptAt = now;
    }

    void recordSuccess(SyncReport report, int minExpectedRecords, Instant now) {
        this.lastReport = report;
        if (report.received() < minExpectedRecords) {
            this.outcome = Outcome.LOW_YIELD;
            this.detail = "수집 " + report.received() + "건 — 기대 최소 " + minExpectedRecords
                    + "건에 못 미침 (API 키·응답 확인 필요)";
        } else {
            this.outcome = Outcome.SUCCESS;
            this.lastSuccessAt = now;
            this.detail = report.toString();
        }
    }

    void recordFailure(Throwable t) {
        this.outcome = Outcome.FAILED;
        this.detail = t.getClass().getSimpleName() + ": " + t.getMessage();
    }

    public Outcome getOutcome() { return outcome; }
    public Instant getLastAttemptAt() { return lastAttemptAt; }
    public Instant getLastSuccessAt() { return lastSuccessAt; }
    public SyncReport getLastReport() { return lastReport; }
    public String getDetail() { return detail; }
}
