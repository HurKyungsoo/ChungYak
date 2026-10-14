package com.portfolio.chungyak.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis 매퍼 스캔 — 메인 클래스가 아니라 여기 둔다 (#34, 첫 매퍼 CompetitionMapper).
 * 메인 클래스에 {@code @MapperScan} 을 두면 {@code @DataJpaTest} 같은 슬라이스 테스트도 매퍼 빈을 만들려다
 * SqlSessionFactory 가 없어 컨텍스트가 뜨지 않는다. 슬라이스는 스캔으로 찾은 {@code @Configuration} 을 빼므로 여기면 안전하다.
 */
@Configuration
@MapperScan("com.portfolio.chungyak.mapper")
public class MyBatisConfig {
}
