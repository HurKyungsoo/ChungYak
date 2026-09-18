package com.portfolio.chungyak.web;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.portfolio.chungyak.web.form.EligibilityForm;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * "내 조건으로 공고 찾기" 의 PRG(Post-Redirect-Get) 저장소 — {@link EligibilityResultStore} 와
 * 같은 이유로 존재한다. 여기는 특정 공고 하나에 매인 판정이 아니라 조건 자체가 결과이므로
 * announcementId·explain 필드 없이 폼만 들고 있는다.
 */
@Component
public class MatchResultStore {

    private static final Duration TTL = Duration.ofMinutes(30);
    private static final long MAX_ENTRIES = 5_000;

    private final Cache<String, EligibilityForm> cache = Caffeine.newBuilder()
            .maximumSize(MAX_ENTRIES)
            .expireAfterWrite(TTL)
            .build();

    public String put(EligibilityForm form) {
        String token = UUID.randomUUID().toString();
        cache.put(token, form);
        return token;
    }

    public Optional<EligibilityForm> get(String token) {
        return Optional.ofNullable(cache.getIfPresent(token));
    }
}
