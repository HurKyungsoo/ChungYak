package com.portfolio.chungyak.web.view;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * 공고 목록의 정렬. {@link MatchRowFilter.Sort} 와 같은 성격의 순수 표시 로직이다 —
 * 순서만 바꿀 뿐 무엇을 보여줄지(접수중·접수예정)는 조회 단계에서 이미 정해져 있다.
 *
 * 기본이 마감 임박순인 이유는 "내 조건으로 찾은 공고" 화면과 같다 — 놓치면 안 되는 순서가
 * 먼저다. 조회 순서(접수 시작일)를 그대로 쓰면 오늘 마감인 공고가 닷새 남은 공고보다
 * 아래에 놓인다.
 */
public enum AnnouncementListSort {

    DEADLINE("마감 임박순"),
    OPENING("접수 시작순"),
    SUPPLY("특공 세대 많은순");

    private final String label;

    AnnouncementListSort(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 모르는 값·빈 값은 기본 정렬로 — 사용자가 URL 을 손대도 400 을 내지 않는다. */
    public static AnnouncementListSort from(String raw) {
        if (raw == null || raw.isBlank()) return DEADLINE;
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return DEADLINE;
        }
    }

    public List<AnnouncementListRow> apply(List<AnnouncementListRow> rows) {
        return rows.stream().sorted(comparator()).toList();
    }

    private Comparator<AnnouncementListRow> comparator() {
        Comparator<AnnouncementListRow> byDeadline = Comparator.comparing(
                AnnouncementListRow::receptEndDate, Comparator.nullsLast(Comparator.<LocalDate>naturalOrder()));

        return switch (this) {
            case OPENING -> Comparator.comparing(
                    AnnouncementListRow::receptBeginDate, Comparator.nullsLast(Comparator.<LocalDate>naturalOrder()));
            // 세대수를 모르는 소스(LH)는 0 이라 자연히 뒤로 간다. 같은 세대수면 마감이 급한 쪽 먼저.
            case SUPPLY -> Comparator.comparingInt(AnnouncementListRow::totalSpecialSupply)
                    .reversed().thenComparing(byDeadline);
            case DEADLINE -> byDeadline;
        };
    }
}
