package com.portfolio.chungyak.alert;

import com.portfolio.chungyak.domain.AlertSubscription;
import com.portfolio.chungyak.repository.AlertSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;

/**
 * 이메일 확인을 하지 않은 알림 신청을 지운다 (개인정보 처리방침 2절 — "확인 전 신청은 7일 후 자동 삭제").
 *
 * 확인 메일만 받고 링크를 누르지 않은 사람은 해지 링크를 받은 적이 없어 스스로 지울 방법이 없다.
 * 그런데 그 행에는 소득·자산이 담긴 조건이 남는다 — 쓰이지도 않을 개인정보를 계속 들고 있지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertSubscriptionCleanupScheduler {

    static final Duration PENDING_TTL = Duration.ofDays(7);

    private final AlertSubscriptionRepository repository;
    private final Clock clock;

    @Scheduled(cron = "0 15 3 * * *", zone = "Asia/Seoul")
    @Transactional
    public void purgeUnconfirmed() {
        long deleted = repository.deleteByStatusAndCreatedAtBefore(
                AlertSubscription.Status.PENDING, clock.instant().minus(PENDING_TTL));
        if (deleted > 0) log.info("확인하지 않은 알림 신청 {}건 삭제(7일 경과)", deleted);
    }
}
