package com.portfolio.chungyak.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 앱 전역 설정 — 지금은 절대 URL 이 필요한 곳(알림 메일의 확인·해지 링크)에서만 쓴다.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(String baseUrl) {

    public AppProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://localhost:8080";
        } else if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
    }
}
