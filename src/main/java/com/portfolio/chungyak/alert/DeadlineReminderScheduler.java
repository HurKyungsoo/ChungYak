package com.portfolio.chungyak.alert;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매일 새벽 4시 30분 — 공고 수집(4시)이 끝난 뒤 마감 임박 알림을 돌린다.
 *
 * 수집 배치와 분리해 둔 이유: 이 알림은 그날 새로 수집된 데이터에 의존하지 않고
 * 이미 저장된 공고의 마감일만 본다. 수집이 실패해도(어제 데이터 기준으로라도) 이 알림은
 * 정상 동작해야 하므로 같은 트랜잭션·같은 실패 경계에 묶지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeadlineReminderScheduler {

    private final DeadlineReminderService reminderService;

    @Scheduled(cron = "0 30 4 * * *", zone = "Asia/Seoul")
    public void remindDaily() {
        try {
            reminderService.remindClosingSoon();
        } catch (RuntimeException e) {
            log.error("마감 임박 알림 배치가 실패했습니다 — 오늘 알림이 빠집니다. {}", e.toString(), e);
        }
    }
}
