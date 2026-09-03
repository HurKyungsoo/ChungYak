package com.portfolio.chungyak;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@MapperScan("com.portfolio.chungyak.mapper")
@SpringBootApplication
public class ChungyakApplication {
    public static void main(String[] args) {
        SpringApplication.run(ChungyakApplication.class, args);
    }
}
