package com.portfolio.chungyak.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.domain.SpecialSupplyType;
import com.portfolio.chungyak.external.ApplyhomeClient;
import com.portfolio.chungyak.external.SpecialSupplyApplication;
import com.portfolio.chungyak.external.SpecialSupplyApplication.Count;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 공고의 특별공급 유형별 배정 세대·신청 건수(주택형 합) — 판정 결과 화면의 <b>참고 정보</b> (#33).
 *
 * 판정과 무관하다(규칙 엔진은 이 값을 보지 않는다). 숫자는 청약홈 집계를 합산만 하고 해석·확률을 만들지 않는다.
 * 특공 접수가 끝나야 데이터가 생겨 접수 예정 공고는 비어 있다. 청약홈 APT 공고만 — LH 는 이 API 범위 밖이다.
 * 같은 공고를 여러 사람이 보므로 1시간 캐시한다(접수 직후 데이터가 생기는 공고도 1시간 안에 반영).
 */
@Service
public class SpecialSupplyApplicationService {

    private final ApplyhomeClient client;
    private final Cache<String, Summary> cache = Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterWrite(Duration.ofHours(1))
            .build();

    public SpecialSupplyApplicationService(ApplyhomeClient client) {
        this.client = client;
    }

    /** 유형별 합계. 비어 있으면 데이터 없음(접수 전·LH·조회 실패). 화면은 유형 이름(enum name)으로 찾는다. */
    public record Summary(Map<String, Count> byTypeName) {
        public static final Summary EMPTY = new Summary(Map.of());

        public boolean isEmpty() {
            return byTypeName.isEmpty();
        }
    }

    public Summary forAnnouncement(Announcement a) {
        if (a == null || a.getHouseManageNo() == null
                || (a.getExternalId() != null && a.getExternalId().startsWith("LH"))) {
            return Summary.EMPTY;
        }
        return cache.get(a.getHouseManageNo(), no -> summarize(client.fetchSpecialSupplyApplications(no)));
    }

    static Summary summarize(List<SpecialSupplyApplication> rows) {
        Map<SpecialSupplyType, Count> sum = new EnumMap<>(SpecialSupplyType.class);
        for (SpecialSupplyApplication row : rows) {
            row.byType().forEach((type, c) -> sum.merge(type, c, Count::plus));
        }
        if (sum.isEmpty()) return Summary.EMPTY;
        Map<String, Count> byName = new java.util.LinkedHashMap<>();
        sum.forEach((type, c) -> byName.put(type.name(), c));
        return new Summary(Collections.unmodifiableMap(byName));
    }
}
