package com.portfolio.chungyak.domain;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 공고문 원문에서 "입주자모집공고일"(자격 판단 기준일)을 읽는다 — 적힌 그대로만, 못 읽으면 null.
 *
 * 표기(2026-11-10 LH 공고문 17건): "이 주택의 입주자모집공고일은 2025.03.05.(수)이며, 이는 신청자격…의 판단기준일이 됩니다",
 * "입주자 모집공고일(2026.09.04.) 현재". 일반매각 공고의 머리 "공고일 : 2026.11.05"는 게시일이라 보지 않는다(어차피 판정 대상 아님).
 */
public final class NoticeBaseDate {

    private static final Pattern BASE_DATE = Pattern.compile(
            "입주자\\s?모집\\s?공고일\\s?(?:은|는|:|\\()?\\s*(20\\d{2})\\s*[.\\-년]\\s*(\\d{1,2})\\s*[.\\-월]\\s*(\\d{1,2})");

    private NoticeBaseDate() {}

    /** 처음 나오는 입주자모집공고일. 없거나 날짜가 아니면 null */
    public static LocalDate find(String noticeText) {
        if (noticeText == null || noticeText.isBlank()) return null;
        Matcher m = BASE_DATE.matcher(noticeText.replaceAll("\\s+", " "));
        if (!m.find()) return null;
        try {
            return LocalDate.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
        } catch (DateTimeException e) {
            return null;
        }
    }
}
