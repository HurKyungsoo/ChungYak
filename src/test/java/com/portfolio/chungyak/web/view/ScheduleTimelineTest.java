package com.portfolio.chungyak.web.view;

import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.domain.UnitType;
import com.portfolio.chungyak.web.view.ScheduleTimeline.State;
import com.portfolio.chungyak.web.view.ScheduleTimeline.Step;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduleTimelineTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 12);   // 월요일

    private static Announcement.AnnouncementBuilder applyhome() {
        return Announcement.builder().externalId("APT-1-1")
                .noticeDate(LocalDate.of(2026, 10, 2))
                .specialReceptBeginDate(TODAY).specialReceptEndDate(TODAY)
                .receptBeginDate(LocalDate.of(2026, 10, 13)).receptEndDate(LocalDate.of(2026, 10, 14))
                .winnerAnnounceDate(LocalDate.of(2026, 10, 20))
                .moveInYearMonth("202806");
    }

    @Test
    @DisplayName("청약홈: 공고(지남) → 특공(오늘, 진행 중) → 접수·발표(예정) → 계약(모름) → 입주")
    void applyhomeSteps() {
        ScheduleTimeline t = ScheduleTimeline.of(applyhome().build(), TODAY);

        assertThat(t.steps()).extracting(Step::label)
                .containsExactly("모집공고", "특별공급", "청약 접수", "당첨 발표", "계약", "입주 예정");
        assertThat(t.steps()).extracting(Step::state).containsExactly(
                State.DONE, State.CURRENT, State.UPCOMING, State.UPCOMING, State.UNKNOWN, State.UPCOMING);
        assertThat(t.steps().get(1).when()).isEqualTo("10.12 (월)");
        assertThat(t.steps().get(2).when()).isEqualTo("10.13 (화) ~ 10.14 (수)");
        assertThat(t.steps().get(5).when()).isEqualTo("2028년 6월");
        assertThat(t.hasUnknown()).isTrue();   // 계약은 수집하지 않는다
    }

    @Test
    @DisplayName("강조 배지: 오늘 하루짜리 특공이면 '오늘 특별공급 접수', 여러 날 접수면 '청약 접수 중', 없으면 null")
    void currentHighlight() {
        assertThat(ScheduleTimeline.of(applyhome().build(), TODAY).currentHighlight()).isEqualTo("오늘 특별공급 접수");

        Announcement receptOnly = applyhome().specialReceptBeginDate(null).specialReceptEndDate(null)
                .receptBeginDate(TODAY.minusDays(1)).receptEndDate(TODAY.plusDays(1)).build();
        receptOnly.addUnitType(UnitType.builder().modelNo("01").typeName("84A").specialSupplyCount(3).build());
        assertThat(ScheduleTimeline.of(receptOnly, TODAY).currentHighlight()).isEqualTo("청약 접수 중");

        Announcement later = applyhome().specialReceptBeginDate(TODAY.plusDays(3)).specialReceptEndDate(TODAY.plusDays(3))
                .receptBeginDate(TODAY.plusDays(4)).receptEndDate(TODAY.plusDays(5)).build();
        assertThat(ScheduleTimeline.of(later, TODAY).currentHighlight()).isNull();
    }

    @Test
    @DisplayName("진행 중 단계가 없으면 가장 가까운 예정 단계 하나만 '다음'")
    void marksNextWhenNothingCurrent() {
        ScheduleTimeline t = ScheduleTimeline.of(applyhome()
                .specialReceptBeginDate(LocalDate.of(2026, 10, 15)).specialReceptEndDate(LocalDate.of(2026, 10, 15))
                .receptBeginDate(LocalDate.of(2026, 10, 16)).receptEndDate(LocalDate.of(2026, 10, 17))
                .build(), TODAY);

        assertThat(t.steps()).extracting(Step::state).containsExactly(
                State.DONE, State.NEXT, State.UPCOMING, State.UPCOMING, State.UNKNOWN, State.UPCOMING);
    }

    @Test
    @DisplayName("LH: 특별공급 단계는 만들지 않고, 모르는 날짜는 빼지 않고 '모름'")
    void lhSkipsSpecialAndKeepsUnknown() {
        Announcement lh = Announcement.builder().externalId("LH-1")
                .noticeDate(LocalDate.of(2026, 10, 1))
                .receptEndDate(LocalDate.of(2026, 10, 14))   // 시작일 모름
                .build();

        ScheduleTimeline t = ScheduleTimeline.of(lh, TODAY);

        assertThat(t.steps()).extracting(Step::label).containsExactly("모집공고", "청약 접수", "당첨 발표", "계약");
        assertThat(t.steps().get(1).when()).isEqualTo("~ 10.14 (수)");
        // 시작을 모르면 진행 중이라 단정하지 않는다 — 다음 단계로만
        assertThat(t.steps().get(1).state()).isEqualTo(State.NEXT);
        assertThat(t.steps().get(2).state()).isEqualTo(State.UNKNOWN);
    }

    @Test
    @DisplayName("올해가 아닌 날짜는 연도를 붙인다")
    void yearShownWhenDifferent() {
        Announcement a = Announcement.builder().externalId("LH-1")
                .winnerAnnounceDate(LocalDate.of(2027, 1, 28)).build();
        Step winner = ScheduleTimeline.of(a, TODAY).steps().stream()
                .filter(s -> s.label().equals("당첨 발표")).findFirst().orElseThrow();
        assertThat(winner.when()).isEqualTo("2027.01.28 (목)");
    }

    @Test
    @DisplayName("청약홈: 특공 접수일도 특공 세대도 없으면 특별공급 단계를 만들지 않는다 / 세대가 있으면 '모름'으로 남긴다")
    void specialStepOnlyWhenSpecialSupplyExists() {
        Announcement noSpecial = applyhome().specialReceptBeginDate(null).specialReceptEndDate(null).build();
        noSpecial.addUnitType(UnitType.builder().modelNo("01").typeName("84A").generalSupplyCount(45).build());
        assertThat(ScheduleTimeline.of(noSpecial, TODAY).steps()).extracting(Step::label)
                .doesNotContain("특별공급");

        Announcement specialUnknownDates = applyhome().specialReceptBeginDate(null).specialReceptEndDate(null).build();
        specialUnknownDates.addUnitType(UnitType.builder().modelNo("01").typeName("84A").specialSupplyCount(20).build());
        Step special = ScheduleTimeline.of(specialUnknownDates, TODAY).steps().get(1);
        assertThat(special.label()).isEqualTo("특별공급");
        assertThat(special.state()).isEqualTo(State.UNKNOWN);
    }
}
