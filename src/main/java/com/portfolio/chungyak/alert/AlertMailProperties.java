package com.portfolio.chungyak.alert;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 새 공고 알림(C2) 메일 발송 설정. {@code alert.mail.host} 가 비어 있으면
 * {@link MailConfig} 가 {@code JavaMailSender} 빈을 만들지 않는다 — 발송만 꺼지고
 * 구독 저장·확인 흐름은 그대로 동작한다.
 */
@ConfigurationProperties(prefix = "alert")
public record AlertMailProperties(String fromAddress, Mail mail) {

    public record Mail(String host, int port, String username, String password) {}

    public boolean isConfigured() {
        return mail != null && mail.host() != null && !mail.host().isBlank();
    }
}
