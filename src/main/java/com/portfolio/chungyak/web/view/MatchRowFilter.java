package com.portfolio.chungyak.web.view;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * "내 조건으로 찾은 공고" 목록의 정렬·필터.
 *
 * <b>판정과는 무관한 순수 표시 로직이다</b> — 자격은 이미 {@code EligibilityEngine} 이
 * 다 가렸고, 여기서는 그 결과를 지역·유형으로 추리고 순서만 바꾼다. 거르는 기준을 늘리더라도
 * 자격에 관한 조건은 절대 여기에 두지 않는다(그건 rule 패키지 몫이다).
 */
public final class MatchRowFilter {

    /** 선택 가능한 정렬. 기본은 마감 임박순 — 놓치면 안 되는 순서가 먼저다. */
    public enum Sort {
        DEADLINE("마감 임박순"),
        ALLOCATION("배정 세대 많은순");

        private final String label;

        Sort(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        /** 모르는 값·빈 값은 기본 정렬로 — 사용자가 URL 을 손대도 400 을 내지 않는다. */
        public static Sort from(String raw) {
            if (raw == null || raw.isBlank()) return DEADLINE;
            try {
                return valueOf(raw.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                return DEADLINE;
            }
        }
    }

    private MatchRowFilter() {}

    public static List<MatchRow> apply(List<MatchRow> rows, String region, String detailType, Sort sort) {
        return rows.stream()
                .filter(r -> blank(region) || region.equals(r.regionName()))
                .filter(r -> blank(detailType) || detailType.equals(r.houseDetailTypeLabel()))
                .sorted(comparator(sort))
                .toList();
    }

    /** 드롭다운 후보 — 결과에 실제로 있는 값만 넣는다(매칭이 없는 지역을 고르게 해봐야 빈 화면만 나온다). */
    public static List<String> regionsOf(List<MatchRow> rows) {
        return rows.stream()
                .map(MatchRow::regionName)
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    public static List<String> typesOf(List<MatchRow> rows) {
        return rows.stream()
                .map(MatchRow::houseDetailTypeLabel)
                .filter(s -> s != null && !s.isBlank() && !"-".equals(s))
                .distinct()
                .sorted()
                .toList();
    }

    private static Comparator<MatchRow> comparator(Sort sort) {
        Comparator<MatchRow> byDeadline =
                Comparator.comparing(MatchRow::receptEndDate, Comparator.nullsLast(Comparator.<LocalDate>naturalOrder()));
        if (sort == Sort.ALLOCATION) {
            // 세대수를 모르는 소스(LH)는 0 이라 자연히 뒤로 간다. 같은 세대수면 마감이 급한 쪽 먼저.
            return Comparator.comparingInt(MatchRow::totalAllocated).reversed().thenComparing(byDeadline);
        }
        return byDeadline;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
