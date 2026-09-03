package com.portfolio.chungyak.external;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 응답 정제기.
 *
 * 청약홈은 필드명이 Swagger 로 확정돼 있어 후보 키 탐색이 필요없지만,
 * 날짜/숫자 파싱과 null 처리는 여전히 필요하다. 실데이터에서 확인된 것:
 *  - RCRIT_PBLANC_DE 등 날짜는 "YYYY-MM-DD" 인데 일부 API 는 "YYYYMMDD"
 *  - NSPRC_NM, SPSPLY_RCEPT_BGNDE 등은 null 이 흔하다
 *  - LTTOT_TOP_AMOUNT 는 숫자인데 문자열로 온다 ("87000")
 *  - Y/N 플래그는 대소문자가 섞일 수 있어 대문자 비교
 *
 * 한 건의 파싱 실패가 배치 전체를 멈추면 안 되므로 전부 null 을 반환하고
 * 호출부가 그 건만 건너뛴다.
 */
@Slf4j
@Component
public class PublicDataParser {

    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyyMMdd"),
            DateTimeFormatter.ofPattern("yyyy.MM.dd")
    };

    public String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return null;
        String s = value.asText().trim();
        return s.isEmpty() ? null : s;
    }

    public LocalDate date(JsonNode node, String field) {
        String raw = text(node, field);
        if (raw == null) return null;

        String cleaned = raw.replaceAll("[^0-9\\-.]", "");
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                return LocalDate.parse(cleaned, format);
            } catch (Exception ignored) {
                // 다음 포맷 시도
            }
        }
        log.debug("날짜 파싱 실패. field={}, value={}", field, raw);
        return null;
    }

    public Integer number(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return null;
        if (value.isNumber()) return value.asInt();

        String raw = value.asText().replaceAll("[^0-9-]", "");
        if (raw.isEmpty()) return null;
        try {
            return Integer.parseInt(raw);
        } catch (Exception e) {
            return null;
        }
    }

    public int intOrZero(JsonNode node, String field) {
        Integer value = number(node, field);
        return value == null ? 0 : value;
    }

    /** Y/N 플래그. 실데이터는 "Y"/"N" 인데 빈 값도 있어 기본 false. */
    public boolean flag(JsonNode node, String field) {
        String raw = text(node, field);
        return "Y".equalsIgnoreCase(raw);
    }
}
