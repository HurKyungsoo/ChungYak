package com.portfolio.chungyak.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * 시간 주입.
 *
 * 공고 상태(접수중/예정/마감)는 "오늘"에 따라 갈리므로,
 * 테스트에서 날짜를 고정할 수 있게 Clock 을 빈으로 둔다.
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }
}
