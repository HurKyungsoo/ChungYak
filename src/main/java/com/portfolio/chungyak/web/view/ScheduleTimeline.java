package com.portfolio.chungyak.web.view;

import com.portfolio.chungyak.domain.Announcement;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 공고 상세의 가로 일정 타임라인 (#19-2).
 *
 * 공고 → 특별공급 → 청약 접수 → 당첨 발표 → 계약 → 입주. 날짜를 모르는 단계도 빼지 않고 "모름"으로
 * 남겨 흐리게 그리고 공고문으로 넘긴다 — 빠진 단계가 없는 것처럼 보이면 안 된다.
 * 계약 일정은 지금 수집하지 않으므로 항상 "모름"이다(공고문에 있다).
 *
 * 화면 계산만 한다: 오늘 날짜와 단계 날짜를 비교해 지남/진행 중/다음/예정을 정할 뿐이다.
 */
public record ScheduleTimeline(List<Step> steps) {

    public enum State { DONE, CURRENT, NEXT, UPCOMING, UNKNOWN }

    /** label 단계명 · when 표시 날짜("10.12 (월) ~ 10.14 (수)") · state 오늘 기준 상태 */
    public record Step(String label, String when, State state) {
        public boolean done() { return state == State.DONE; }
        public boolean current() { return state == State.CURRENT; }
        public boolean next() { return state == State.NEXT; }
        public boolean unknown() { return state == State.UNKNOWN; }
    }

    private static final DateTimeFormatter MD = DateTimeFormatter.ofPattern("MM.dd");
    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    public static ScheduleTimeline of(Announcement a, LocalDate today) {
        List<Raw> raws = new ArrayList<>();
        raws.add(new Raw("모집공고", a.getNoticeDate(), a.getNoticeDate()));
        // LH 는 특별공급 접수를 따로 주지 않는다(청약 접수에 섞여 있다) — 없는 단계를 만들지 않는다.
        // 청약홈도 특공 접수일이 없고 어느 주택형에도 특공 세대가 없으면 특공이 없는 공고다 —
        // "공고문에서 확인"으로 그리면 있는데 모르는 것처럼 읽힌다.
        boolean hasSpecialDates = a.getSpecialReceptBeginDate() != null || a.getSpecialReceptEndDate() != null;
        boolean hasSpecialUnits = a.getUnitTypes().stream().anyMatch(u -> u.getSpecialSupplyCount() > 0);
        if (!a.isLh() && (hasSpecialDates || hasSpecialUnits)) {
            raws.add(new Raw("특별공급", a.getSpecialReceptBeginDate(), a.getSpecialReceptEndDate()));
        }
        raws.add(new Raw("청약 접수", a.getReceptBeginDate(), a.getReceptEndDate()));
        raws.add(new Raw("당첨 발표", a.getWinnerAnnounceDate(), a.getWinnerAnnounceDate()));
        raws.add(new Raw("계약", null, null));

        List<Step> steps = new ArrayList<>();
        boolean nextMarked = false;
        boolean anyCurrent = raws.stream().anyMatch(r -> r.isCurrent(today));
        for (Raw r : raws) {
            State state;
            if (r.start == null && r.end == null) state = State.UNKNOWN;
            else if (r.isCurrent(today)) state = State.CURRENT;
            else if (r.lastDay().isBefore(today)) state = State.DONE;
            else if (!anyCurrent && !nextMarked) { state = State.NEXT; nextMarked = true; }
            else state = State.UPCOMING;
            steps.add(new Step(r.label, r.when(today), state));
        }

        // 입주예정은 "YYYYMM" 월 단위라 날짜 비교를 하지 않는다 — 있으면 끝에 붙이기만 한다.
        String moveIn = a.getMoveInYearMonth();
        if (moveIn != null && moveIn.matches("\\d{6}")) {
            YearMonth ym = YearMonth.of(Integer.parseInt(moveIn.substring(0, 4)), Integer.parseInt(moveIn.substring(4)));
            State s = ym.isBefore(YearMonth.from(today)) ? State.DONE : State.UPCOMING;
            steps.add(new Step("입주 예정", ym.getYear() + "년 " + ym.getMonthValue() + "월", s));
        }
        return new ScheduleTimeline(steps);
    }

    /** 모르는 단계가 하나라도 있는지 — 화면이 "공고문에서 확인" 안내를 붙일지 정한다. */
    public boolean hasUnknown() {
        return steps.stream().anyMatch(Step::unknown);
    }

    private record Raw(String label, LocalDate start, LocalDate end) {

        LocalDate lastDay() { return end != null ? end : start; }

        boolean isCurrent(LocalDate today) {
            if (start == null && end == null) return false;
            // 시작을 모르면 "진행 중"이라 단정하지 않는다 — 마감 당일만 진행 중으로 본다.
            LocalDate from = start != null ? start : end;
            return !today.isBefore(from) && !today.isAfter(lastDay());
        }

        String when(LocalDate today) {
            if (start == null && end == null) return null;
            if (start == null) return "~ " + fmt(end, today);
            if (end == null || end.equals(start)) return fmt(start, today);
            return fmt(start, today) + " ~ " + fmt(end, today);
        }

        private static String fmt(LocalDate d, LocalDate today) {
            String day = d.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.KOREAN);
            // 날짜와 요일 사이는 줄바꿈 없는 공백 — 좁은 칸에서 "(화)"만 다음 줄로 떨어지지 않게
            return (d.getYear() == today.getYear() ? MD : YMD).format(d) + " (" + day + ")";
        }
    }
}
