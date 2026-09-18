package com.portfolio.chungyak.alert;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 마감 임박 알림 배치가 실패해도 스케줄러 스레드가 죽지 않는지 — 수집 배치(AnnouncementSyncScheduler)
 * 와 같은 실패 격리 방식.
 */
class DeadlineReminderSchedulerTest {

    @Test
    @DisplayName("알림 서비스가 예외를 던져도 스케줄러 메서드는 예외를 밖으로 던지지 않는다")
    void swallowsExceptionFromService() {
        DeadlineReminderService service = mock(DeadlineReminderService.class);
        doThrow(new RuntimeException("메일 발송 실패")).when(service).remindClosingSoon();
        DeadlineReminderScheduler scheduler = new DeadlineReminderScheduler(service);

        scheduler.remindDaily();   // 예외 없이 반환되어야 한다

        verify(service).remindClosingSoon();
    }
}
