package com.portfolio.chungyak.domain;

import java.time.LocalDate;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 청약홈에 올라온 LH 공급 공고와 LH 수집의 같은 공고를 잇는 키 — 결정론이다.
 *
 * 청약홈의 신혼희망타운·공공분양(LH 공급) 공고 페이지에는 공고문 PDF 가 없고 LH 청약플러스 링크만 있다.
 * 같은 공고가 LH 목록에도 오는데 이름 표기가 다르다(2026-10-15 운영 DB 6쌍):
 * <pre>
 *   청약홈 "양주회천지구 A-26블록 공공분양주택"          LH "양주회천 A-26BL 공공분양주택 입주자모집공고"
 *   청약홈 "의정부우정 A2블록 공공분양주택(본청약)"       LH "의정부우정 A-2블록 공공분양주택 입주자모집공고"
 *   청약홈 "인천계양지구 A6블록 공공분양주택(본청약)"     LH "[정정공고]인천계양 A6블록 공공분양주택 입주자모집공고"
 * </pre>
 * 그래서 <b>지구명 + 블록 번호</b>로 줄이고(지구·공백·하이픈·BL/블록 차이를 지움) 공고일이 같은지 본다.
 * 블록 번호가 없는 이름(민간 시행 "힐스테이트 …")은 키가 없다 — 짝을 지어내지 않는다.
 */
public final class LhNoticeTwin {

    private LhNoticeTwin() {}

    /** 공고일이 이만큼까지 달라도 같은 공고로 본다(두 원천이 하루씩 다르게 적는 경우 대비) */
    public static final int NOTICE_DATE_TOLERANCE_DAYS = 1;

    /** "A-26블록", "A26BL", "B2 블록", "A-2-1블록" */
    private static final Pattern BLOCK = Pattern.compile("([A-Za-z])\\s*-?\\s*(\\d+)(?:-(\\d+))?\\s*(?:블록|BL|bl)");
    private static final Pattern BRACKETS = Pattern.compile("\\[[^\\]]*]|\\([^)]*\\)");

    /** 지구명+블록 키. 블록 번호나 지구명이 없으면 empty */
    public static Optional<String> keyOf(String houseName) {
        if (houseName == null) return Optional.empty();
        String name = BRACKETS.matcher(houseName).replaceAll(" ");
        Matcher m = BLOCK.matcher(name);
        if (!m.find()) return Optional.empty();
        String block = m.group(1).toUpperCase() + m.group(2) + (m.group(3) != null ? "-" + m.group(3) : "");
        String district = name.substring(0, m.start()).replace("지구", "").replaceAll("[^0-9가-힣]", "");
        if (district.isEmpty()) return Optional.empty();
        return Optional.of(district + "|" + block);
    }

    /** 공고일 차이가 허용 범위 안이고 지구명+블록이 같은지 */
    public static boolean sameNotice(String nameA, LocalDate dateA, String nameB, LocalDate dateB) {
        if (dateA == null || dateB == null) return false;
        if (Math.abs(dateA.toEpochDay() - dateB.toEpochDay()) > NOTICE_DATE_TOLERANCE_DAYS) return false;
        Optional<String> a = keyOf(nameA);
        return a.isPresent() && a.equals(keyOf(nameB));
    }
}
