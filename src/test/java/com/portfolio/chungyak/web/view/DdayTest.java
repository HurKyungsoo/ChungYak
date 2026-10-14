package com.portfolio.chungyak.web.view;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class DdayTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 12);

    @Test
    @DisplayName("접수중이면 마감까지, 접수예정이면 시작까지 — 무엇까지 남았는지 붙인다")
    void labelsWhatTheCountIsFor() {
        assertThat(Dday.of("접수중", TODAY.minusDays(1), TODAY.plusDays(2), TODAY).label()).isEqualTo("마감 D-2");
        assertThat(Dday.of("접수예정", TODAY.plusDays(5), TODAY.plusDays(7), TODAY).label()).isEqualTo("접수 시작 D-5");
        assertThat(Dday.of("접수중", TODAY.minusDays(1), TODAY, TODAY).label()).isEqualTo("오늘 마감");
        assertThat(Dday.of("접수예정", TODAY, TODAY.plusDays(2), TODAY).label()).isEqualTo("오늘 접수 시작");
    }

    @Test
    @DisplayName("3일 이내만 임박")
    void urgentWithinThreeDays() {
        assertThat(Dday.of("접수중", TODAY, TODAY.plusDays(3), TODAY).urgent()).isTrue();
        assertThat(Dday.of("접수중", TODAY, TODAY.plusDays(4), TODAY).urgent()).isFalse();
    }

    @Test
    @DisplayName("시작일을 몰라 상태가 '-' 여도 마감일이 남았으면 마감 D-n (LH 일부)")
    void deadlineEvenWithoutBegin() {
        assertThat(Dday.of("-", null, TODAY.plusDays(2), TODAY).label()).isEqualTo("마감 D-2");
    }

    @Test
    @DisplayName("마감·날짜 없음은 표시하지 않는다")
    void noneWhenClosedOrUnknown() {
        assertThat(Dday.of("접수마감", null, TODAY.minusDays(1), TODAY).present()).isFalse();
        assertThat(Dday.of("-", null, null, TODAY).present()).isFalse();
        // 취소·정정 전 공고는 마감일이 남아 있어도 "오늘 마감"을 붙이지 않는다 (#47 — 취소공고가 "접수중 · 오늘 마감"으로 보였다)
        assertThat(Dday.of("취소된 공고", null, TODAY, TODAY).present()).isFalse();
        assertThat(Dday.of("정정 전 공고", null, TODAY.plusDays(2), TODAY).present()).isFalse();
        assertThat(Dday.of("접수중", TODAY, null, TODAY).present()).isFalse();
    }
}
