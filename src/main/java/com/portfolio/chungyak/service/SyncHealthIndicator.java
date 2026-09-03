package com.portfolio.chungyak.service;

import com.portfolio.chungyak.external.PublicDataProperties;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * 공고 수집 상태를 /actuator/health 에 노출한다.
 *
 * - 마지막 수집이 예외로 실패 → DOWN
 * - 수집 건수가 비정상적으로 적음 → DOWN
 * - 마지막 성공이 너무 오래됨(기본 30시간 — 매일 04시 배치 기준) → DOWN
 * - 아직 안 돌림 → UNKNOWN (배포 직후 정상)
 *
 * 이 지표는 liveness/readiness 그룹에 들어가지 않으므로, 수집이 낡아도
 * 앱 자체 라우팅(Caddy healthcheck = /actuator/health/liveness)에는 영향이 없다.
 */
@Component("sync")
public class SyncHealthIndicator implements HealthIndicator {

    private final SyncStatus status;
    private final Clock clock;
    private final long staleAfterHours;

    public SyncHealthIndicator(SyncStatus status, Clock clock, PublicDataProperties properties) {
        this.status = status;
        this.clock = clock;
        this.staleAfterHours = properties.getSync().getStaleAfterHours();
    }

    @Override
    public Health health() {
        Health.Builder b = new Health.Builder()
                .withDetail("outcome", status.getOutcome().name());
        putIfPresent(b, "lastAttemptAt", status.getLastAttemptAt());
        putIfPresent(b, "lastSuccessAt", status.getLastSuccessAt());
        putIfPresent(b, "detail", status.getDetail());
        if (status.getLastReport() != null) {
            b.withDetail("lastReport", status.getLastReport().toString());
        }

        return switch (status.getOutcome()) {
            case NEVER_RUN -> b.unknown().withDetail("note", "아직 수집한 적 없음").build();
            case FAILED, LOW_YIELD -> b.down().build();
            case SUCCESS -> {
                Instant last = status.getLastSuccessAt();
                if (last != null
                        && Duration.between(last, clock.instant()).toHours() >= staleAfterHours) {
                    yield b.down().withDetail("note",
                            "마지막 성공 수집이 " + staleAfterHours + "시간 이상 지남").build();
                }
                yield b.up().build();
            }
        };
    }

    private static void putIfPresent(Health.Builder b, String key, Object value) {
        if (value != null) {
            b.withDetail(key, value.toString());
        }
    }
}
