package com.portfolio.chungyak.web.view;

import com.portfolio.chungyak.web.view.MatchRowFilter.Sort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "내 조건으로 찾은 공고" 목록의 정렬·필터.
 *
 * 판정이 아니라 표시 로직이다 — 여기서 거르는 건 지역·유형뿐이고 자격은 이미 규칙 엔진이
 * 다 가린 뒤다. 그래서 "무엇을 빼는가"보다 "순서와 경계가 맞는가"를 본다.
 */
class MatchRowFilterTest {

    private static MatchRow row(String name, String region, String type, LocalDate end, int allocated) {
        return new MatchRow(1L, name, region, type, LocalDate.of(2026, 9, 1), end,
                "접수중", null, false, List.of("신혼부부"), "084A", true, allocated);
    }

    private static final MatchRow 경기_민영_마감빠름 =
            row("경기민영", "경기", "민영", LocalDate.of(2026, 9, 20), 10);
    private static final MatchRow 인천_국민_마감늦음 =
            row("인천국민", "인천", "국민", LocalDate.of(2026, 10, 2), 200);
    private static final MatchRow 경기_국민_중간 =
            row("경기국민", "경기", "국민", LocalDate.of(2026, 9, 25), 50);

    private static final List<MatchRow> ALL = List.of(인천_국민_마감늦음, 경기_민영_마감빠름, 경기_국민_중간);

    @Test
    @DisplayName("기본 정렬은 마감 임박순")
    void sortsByDeadlineByDefault() {
        assertThat(MatchRowFilter.apply(ALL, null, null, Sort.DEADLINE))
                .extracting(MatchRow::houseName)
                .containsExactly("경기민영", "경기국민", "인천국민");
    }

    @Test
    @DisplayName("배정 세대 많은순으로 바꿀 수 있다")
    void sortsByAllocation() {
        assertThat(MatchRowFilter.apply(ALL, null, null, Sort.ALLOCATION))
                .extracting(MatchRow::houseName)
                .containsExactly("인천국민", "경기국민", "경기민영");
    }

    @Test
    @DisplayName("지역·유형으로 추릴 수 있고, 둘을 함께 걸 수도 있다")
    void filtersByRegionAndType() {
        assertThat(MatchRowFilter.apply(ALL, "경기", null, Sort.DEADLINE))
                .extracting(MatchRow::houseName).containsExactly("경기민영", "경기국민");
        assertThat(MatchRowFilter.apply(ALL, null, "국민", Sort.DEADLINE))
                .extracting(MatchRow::houseName).containsExactly("경기국민", "인천국민");
        assertThat(MatchRowFilter.apply(ALL, "경기", "국민", Sort.DEADLINE))
                .extracting(MatchRow::houseName).containsExactly("경기국민");
    }

    @Test
    @DisplayName("빈 값은 조건 없음으로 본다 — 결과가 통째로 사라지지 않는다")
    void blankMeansNoFilter() {
        assertThat(MatchRowFilter.apply(ALL, "", "  ", Sort.DEADLINE)).hasSize(3);
    }

    @Test
    @DisplayName("모르는 정렬 값은 기본값으로 — URL 을 손대도 오류를 내지 않는다")
    void unknownSortFallsBackToDefault() {
        assertThat(Sort.from("bogus")).isEqualTo(Sort.DEADLINE);
        assertThat(Sort.from(null)).isEqualTo(Sort.DEADLINE);
        assertThat(Sort.from("allocation")).isEqualTo(Sort.ALLOCATION);   // 대소문자 무시
    }

    @Test
    @DisplayName("드롭다운 후보는 결과에 실제로 있는 값만")
    void optionsComeFromRowsOnly() {
        assertThat(MatchRowFilter.regionsOf(ALL)).containsExactly("경기", "인천");
        assertThat(MatchRowFilter.typesOf(ALL)).containsExactly("국민", "민영");
        assertThat(MatchRowFilter.typesOf(List.of(row("미상", "서울", "-", LocalDate.now(), 0)))).isEmpty();
    }
}
