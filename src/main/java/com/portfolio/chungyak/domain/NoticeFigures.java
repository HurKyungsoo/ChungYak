package com.portfolio.chungyak.domain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 공고문에 적힌 제도 금액 꺼내기 — 설정값(application.yml)이 낡았는지 대조하려고({@code FigureWatchService}). 판정에는 쓰지 않는다.
 *
 * 표기(2026-11-10 LH 분양 공고문): 소득표 "도시근로자 가구당 월평균소득(액)의 100% 7,533,763 8,802,202 9,326,985 9,906,263 10,485,541 11,064,819"
 * (3인 이하·4인…8인), 자산 "부동산 215,500천원 이하", "자동차 45,420천원 이하", 신혼희망타운 "362,000천원".
 * 임대 공고는 소득표 모양(1·2·3인 따로)과 자산 기준이 달라 대조 대상이 아니다.
 */
public final class NoticeFigures {

    private static final String AMOUNT = "\\d{1,3}(?:,\\d{3})+";
    /** 100% 행 — 3인 이하·4·5·6·7·8인 여섯 칸 */
    private static final Pattern INCOME_100 = Pattern.compile(
            "월평균소득액?의\\s?100%\\s+((?:" + AMOUNT + "\\s+){5}" + AMOUNT + ")");

    private NoticeFigures() {}

    /** 소득표 100% 행(3인 이하, 4~8인 순서 6개, 원). 없으면 빈 목록 */
    public static List<Long> incomeRow100(String text) {
        if (text == null) return List.of();
        Matcher m = INCOME_100.matcher(normalize(text));
        if (!m.find()) return List.of();
        List<Long> out = new ArrayList<>();
        for (String n : m.group(1).trim().split("\\s+")) out.add(Long.parseLong(n.replace(",", "")));
        return out;
    }

    /** "{라벨} 215,500천원" 꼴로 적힌 금액들(원). 라벨 바로 뒤 금액만 본다 */
    public static Set<Long> thousandWonAfter(String text, String label) {
        Set<Long> out = new LinkedHashSet<>();
        if (text == null) return out;
        Matcher m = Pattern.compile(Pattern.quote(label) + "\\s?(" + AMOUNT + ")\\s?천원").matcher(normalize(text));
        while (m.find()) out.add(Long.parseLong(m.group(1).replace(",", "")) * 1000);
        return out;
    }

    /** 원 단위 금액이 공고문에 적혀 있는지 — "362,000천원", "362,000,000원", "362백만원", "3억 6,200만원" 표기 모두 */
    public static boolean mentionsAmount(String text, long won) {
        if (text == null) return false;
        String t = normalize(text).replace(" ", "");
        List<String> forms = new ArrayList<>();
        forms.add(String.format(java.util.Locale.ROOT, "%,d원", won));
        if (won % 1000 == 0) forms.add(String.format(java.util.Locale.ROOT, "%,d천원", won / 1000));
        if (won % 1_000_000 == 0) forms.add(String.format(java.util.Locale.ROOT, "%,d백만원", won / 1_000_000));
        long eok = won / 100_000_000, man = (won % 100_000_000) / 10_000;
        if (won % 10_000 == 0 && eok > 0) {
            forms.add(man == 0 ? eok + "억원" : eok + "억" + String.format(java.util.Locale.ROOT, "%,d", man) + "만원");
        }
        return forms.stream().anyMatch(t::contains);
    }

    private static String normalize(String text) {
        return text.replaceAll("\\s+", " ");
    }
}
