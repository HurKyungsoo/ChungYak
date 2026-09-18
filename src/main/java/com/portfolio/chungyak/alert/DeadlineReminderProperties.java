package com.portfolio.chungyak.alert;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * "마감 임박 알림" — 접수 마감일 {@code daysBefore}일 전인 공고를 대상으로 한다.
 * 배포 없이 조정할 수 있게 코드에 박지 않는다.
 */
@ConfigurationProperties(prefix = "alert.deadline-reminder")
public record DeadlineReminderProperties(int daysBefore) {
}
