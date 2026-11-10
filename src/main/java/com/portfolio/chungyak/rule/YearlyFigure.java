package com.portfolio.chungyak.rule;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 매년 바뀌는 제도 금액 한 버전 — 도시근로자 소득표·공공 특공 자산 기준·민영 부동산 추첨 기준·신혼희망타운 자산.
 *
 * 청약 자격은 <b>입주자모집공고일</b> 기준으로 따진다. 새 해 기준이 나왔다고 설정값을 덮어쓰면 그 전에 나온
 * (아직 접수 중인) 공고까지 새 기준으로 판정된다. 그래서 금액마다 {@code applies-from}(이 값을 쓰기 시작한 공고일)을 두고
 * 공고일에 맞는 버전을 고른다. 판정은 결정론적이다 — 같은 공고·같은 입력이면 언제 물어도 같은 버전이다.
 *
 * @param value       금액(또는 표)
 * @param basis       화면·사유에 적는 기준 이름 — "2026년도", "2025년" 등
 * @param appliesFrom 이 값을 쓰기 시작한 공고일. 비우면(null) 시작일 미확인 — 가장 이른 버전만 비울 수 있다
 */
public record YearlyFigure<T>(T value, String basis, LocalDate appliesFrom) {

    /**
     * 고른 버전.
     *
     * @param outsideKnownRange 공고일이 알고 있는 모든 버전의 시작일보다 이르다 — 가장 이른 버전을 썼고, 사유에 밝혀야 한다
     */
    public record Pick<T>(T value, String basis, boolean outsideKnownRange, LocalDate noticeDate) {

        /** 사유 문장 뒤에 붙일 안내 — 범위 밖일 때만 */
        public String note() {
            return outsideKnownRange
                    ? " 이 공고일(" + noticeDate + ")에 맞는 기준을 확인하지 못해 가장 가까운 " + basis
                      + " 기준으로 판정했습니다 — 공고문의 기준 금액을 확인하세요."
                    : "";
        }
    }

    /**
     * 공고일에 맞는 버전 — 시작일이 공고일 이하인 것 중 가장 늦은 것. 공고일을 모르면 가장 최근 버전.
     * 공고일이 모든 시작일보다 이르면 가장 이른 버전을 고르고 {@link Pick#outsideKnownRange} 를 켠다.
     */
    public static <T> Pick<T> pick(List<YearlyFigure<T>> versions, LocalDate noticeDate) {
        List<YearlyFigure<T>> sorted = sorted(versions);
        YearlyFigure<T> latest = sorted.get(sorted.size() - 1);
        if (noticeDate == null) return new Pick<>(latest.value(), latest.basis(), false, null);
        YearlyFigure<T> chosen = null;
        for (YearlyFigure<T> v : sorted) {
            if (v.appliesFrom() == null || !v.appliesFrom().isAfter(noticeDate)) chosen = v;
        }
        if (chosen == null) {
            YearlyFigure<T> earliest = sorted.get(0);
            return new Pick<>(earliest.value(), earliest.basis(), true, noticeDate);
        }
        return new Pick<>(chosen.value(), chosen.basis(), false, noticeDate);
    }

    /**
     * 시작일 순 — 시작일 없는 버전(미확인)은 가장 이르다. 시작일 없는 버전이 둘 이상이거나 시작일이 겹치면
     * 어느 값을 쓸지 정할 수 없으므로 설정 오류로 막는다(앱 기동 때 드러난다).
     */
    static <T> List<YearlyFigure<T>> sorted(List<YearlyFigure<T>> versions) {
        if (versions == null || versions.isEmpty()) throw new IllegalStateException("제도 금액 버전이 하나도 없습니다");
        List<YearlyFigure<T>> sorted = new ArrayList<>(versions);
        sorted.sort(Comparator.comparing(YearlyFigure::appliesFrom, Comparator.nullsFirst(Comparator.naturalOrder())));
        for (int i = 1; i < sorted.size(); i++) {
            LocalDate prev = sorted.get(i - 1).appliesFrom();
            LocalDate cur = sorted.get(i).appliesFrom();
            if (cur == null || cur.equals(prev)) {
                throw new IllegalStateException("제도 금액 버전의 applies-from 이 비었거나 겹칩니다 — 시작일 없는 버전은 가장 이른 하나뿐이어야 합니다: "
                        + versions.stream().map(v -> v.basis() + "@" + v.appliesFrom()).toList());
            }
        }
        return sorted;
    }

    /** 현재 값 + 예전 값들 → 버전 목록 */
    static <T> List<YearlyFigure<T>> of(YearlyFigure<T> current, List<YearlyFigure<T>> previous) {
        List<YearlyFigure<T>> all = new ArrayList<>(previous == null ? List.of() : previous);
        all.add(current);
        sorted(all);   // 설정 검증
        return List.copyOf(all);
    }
}
