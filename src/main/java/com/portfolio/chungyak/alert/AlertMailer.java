package com.portfolio.chungyak.alert;

import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 새 공고 알림 메일 발송 — 판정 결과를 문장으로 재구성하지 않는다. 이 메일 본문은 규칙
 * 엔진이 낸 확정 판정(유형·공고명)을 그대로 나열만 한다(LLM 관여 없음, CLAUDE.md 경계 밖 일).
 *
 * {@code alert.mail.host} 가 없으면(=SMTP 미설정) 발송하지 않고 로그만 남긴다 — 구독·매칭
 * 로직은 그대로 검증할 수 있게, 메일 서버만 없을 뿐 기능 자체가 죽지 않는다.
 */
@Slf4j
@Component
public class AlertMailer {

    private final Optional<JavaMailSender> mailSender;
    private final String fromAddress;

    public AlertMailer(Optional<JavaMailSender> mailSender, AlertMailProperties properties) {
        this.mailSender = mailSender;
        this.fromAddress = properties.fromAddress();
    }

    public boolean isEnabled() {
        return mailSender.isPresent();
    }

    public void send(String to, String subject, String body) {
        if (mailSender.isEmpty()) {
            log.info("메일 발송 비활성(alert.mail.host 미설정) — to={}, subject={}\n{}", to, subject, body);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        mailSender.get().send(message);
    }
}
