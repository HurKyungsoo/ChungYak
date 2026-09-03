package com.portfolio.chungyak.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.UUID;

/**
 * 관리자 계정 설정.
 *
 * `ADMIN_PASSWORD` 를 주지 않으면 기동 시 임시 비밀번호를 만들어 로그에 찍는다
 * (Spring Boot 기본 시큐리티와 같은 방식). 운영에서는 반드시 환경변수로 주입한다.
 * 비밀번호를 코드나 `application.yml` 에 하드코딩하지 말 것.
 */
@Slf4j
@ConfigurationProperties(prefix = "security.admin")
public record SecurityProperties(String username, String password) {

    public SecurityProperties {
        if (username == null || username.isBlank()) {
            username = "admin";
        }
    }

    /** 설정된 비밀번호, 없으면 임시 생성해서 로그로 알린다. */
    public String resolvePassword() {
        if (password != null && !password.isBlank()) {
            return password;
        }
        String generated = UUID.randomUUID().toString();
        log.warn("""

                ============================================================
                 ADMIN_PASSWORD 가 설정되지 않아 임시 비밀번호를 생성했습니다.
                 username: {}
                 password: {}
                 (다음 기동 때 바뀝니다. 운영에서는 ADMIN_PASSWORD 를 주입하세요.)
                ============================================================
                """, username(), generated);
        return generated;
    }
}
