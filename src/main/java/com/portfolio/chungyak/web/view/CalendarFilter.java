package com.portfolio.chungyak.web.view;

import com.portfolio.chungyak.domain.HouseDetailType;
import com.portfolio.chungyak.domain.SupplyCategory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 청약 캘린더의 필터(지역·주택유형·분양/임대) — 공고 목록과 같은 조건이다.
 *
 * 달 이동·날짜 펼치기 링크가 고른 필터를 잃지 않도록 쿼리스트링 꼬리를 만든다.
 * (Thymeleaf 링크 식은 null 파라미터도 이름만 남겨 주소가 지저분해져서, 값 있는 것만 직접 붙인다.)
 */
public record CalendarFilter(String region, HouseDetailType detailType, SupplyCategory category) {

    public boolean active() {
        return (region != null && !region.isBlank()) || detailType != null || category != null;
    }

    /** "&region=서울&detailType=PRIVATE" — 값 있는 것만, 인코딩해서. 없으면 빈 문자열. */
    public String suffix() {
        StringBuilder sb = new StringBuilder();
        if (region != null && !region.isBlank()) sb.append("&region=").append(URLEncoder.encode(region, StandardCharsets.UTF_8));
        if (detailType != null) sb.append("&detailType=").append(detailType.name());
        if (category != null) sb.append("&category=").append(category.name());
        return sb.toString();
    }
}
