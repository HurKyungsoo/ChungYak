package com.portfolio.chungyak.web.view;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 청약홈 APT 공고문 첫 페이지의 "단지 주요정보" 표 (#38) — AI 없이 원문 표를 그대로 읽는다.
 *
 * 표는 머리 줄 {@code 재당첨제한 전매제한 거주의무기간 분양가상한제 택지유형} 바로 다음 줄에 값 다섯 개가 온다
 * (2026-10-13 실측: 민영 공고 2/2건, 예 {@code 없음 6개월 없음 미적용 민간택지}). 공공 공고 등 표가 없는 공고도 있다.
 * 값이 정확히 다섯 칸으로 나뉠 때만 쓴다 — "1년 6개월"처럼 칸 안에 공백이 있어 개수가 어긋나면 추측하지 않고 버린다.
 * 판정과 무관한 정보 표시다.
 */
public record NoticeSummaryTable(List<Item> items) {

    public record Item(String label, String value) {}

    private static final List<String> LABELS = List.of("재당첨제한", "전매제한", "거주의무기간", "분양가상한제", "택지유형");
    /** 표는 첫 페이지에 있다 — 본문 깊은 곳의 같은 단어 나열에 걸리지 않게 앞부분만 본다 */
    private static final int HEAD_LIMIT = 5_000;

    public static Optional<NoticeSummaryTable> parse(String noticeText) {
        if (noticeText == null || noticeText.isBlank()) return Optional.empty();
        String head = noticeText.substring(0, Math.min(HEAD_LIMIT, noticeText.length()));
        String[] lines = head.split("\\R");
        String header = String.join(" ", LABELS);
        for (int i = 0; i + 1 < lines.length; i++) {
            if (!lines[i].trim().replaceAll("\\s+", " ").equals(header)) continue;
            String[] values = lines[i + 1].trim().split("\\s+");
            if (values.length != LABELS.size()) return Optional.empty();
            List<Item> items = new ArrayList<>();
            for (int k = 0; k < values.length; k++) items.add(new Item(LABELS.get(k), values[k]));
            return Optional.of(new NoticeSummaryTable(List.copyOf(items)));
        }
        return Optional.empty();
    }
}
