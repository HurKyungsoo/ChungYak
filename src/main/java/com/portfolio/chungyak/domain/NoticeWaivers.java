package com.portfolio.chungyak.domain;

import java.util.regex.Pattern;

/**
 * 특별공급 판정을 하면 안 되는 공고 알아보기 — 공고문·제목에 적힌 그대로만 본다(추측 금지).
 *
 * 2026-11-10 운영 공고 24건(제목에 선착순·잔여세대·일반매각·추가모집) 공고문을 대조했다:
 * <ul>
 *   <li>LH "일반매각"·"선착순" — 무주택 성년자(+거주지역, 일부는 유주택도 가능)에게 파는 공고. 소득·자산·청약통장을 안 본다(13건 모두)</li>
 *   <li>"추가 입주자모집"·"잔여세대 추가입주자모집" — 대부분 "금회 추가모집은 청약저축 가입여부, 소득·자산 요건, 과거당첨사실 여부를
 *       불문"(5건). 같은 제목 형식인데 그 문구가 없고 신혼희망타운 자격·소득·자산을 그대로 보는 공고도 있다(1건) — 그래서 제목이 아니라 공고문 문구로 가른다</li>
 * </ul>
 */
public final class NoticeWaivers {

    /** "소득·자산 요건 … 불문", "소득 및 자산요건, 거주지역, … 불문", "소득·자산기준 충족여부는 불문" — "불문하지 않" 은 제외 */
    private static final Pattern INCOME_ASSET_WAIVED =
            Pattern.compile("소득\\s?(?:·|및|과|,)?\\s?자산[^.■※]{0,60}불문(?!하지)");

    private static final Pattern OPEN_SALE_TITLE = Pattern.compile("일반매각|선착순");

    private NoticeWaivers() {}

    /** 공고문 원문이 특별공급 소득·자산 요건을 보지 않는다고 밝히는지 */
    public static boolean waivesIncomeAndAssets(String noticeText) {
        if (noticeText == null || noticeText.isBlank()) return false;
        return INCOME_ASSET_WAIVED.matcher(noticeText.replaceAll("\\s+", " ")).find();
    }

    /** LH 일반매각·선착순 공급인지(제목) — 청약 순위·특별공급 구조가 없다 */
    public static boolean isOpenSaleTitle(String houseName) {
        return houseName != null && OPEN_SALE_TITLE.matcher(houseName).find();
    }
}
