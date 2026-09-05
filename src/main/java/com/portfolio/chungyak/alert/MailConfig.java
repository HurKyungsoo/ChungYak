package com.portfolio.chungyak.alert;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.nio.charset.StandardCharsets;

/**
 * 새 공고 알림 메일 빈 구성.
 *
 * {@code alert.mail.host} 가 없으면 {@link JavaMailSender} 빈을 아예 만들지 않는다.
 * {@link AlertMailer} 는 {@code Optional<JavaMailSender>} 를 받아 없으면 로그로 대체 —
 * ANTHROPIC_API_KEY/VOYAGE_API_KEY 없을 때와 같은 패턴({@link com.portfolio.chungyak.llm.AnthropicClientConfig} 참고).
 */
@Configuration
@EnableConfigurationProperties(AlertMailProperties.class)
public class MailConfig {

    @Bean
    @ConditionalOnExpression("'${alert.mail.host:}'.length() > 0")
    public JavaMailSender javaMailSender(AlertMailProperties properties) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setDefaultEncoding(StandardCharsets.UTF_8.name());
        sender.setHost(properties.mail().host());
        sender.setPort(properties.mail().port());
        sender.setUsername(properties.mail().username());
        sender.setPassword(properties.mail().password());
        sender.getJavaMailProperties().put("mail.smtp.auth", "true");
        sender.getJavaMailProperties().put("mail.smtp.starttls.enable", "true");
        return sender;
    }
}
