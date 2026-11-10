package com.portfolio.chungyak.domain;

import java.util.List;

/**
 * 시·도 이름 표기 통일.
 *
 * 청약홈 `SUBSCRPT_AREA_CODE_NM` 은 "서울"처럼 짧게, LH `CNP_CD_NM` 은 "서울특별시"처럼 길게 온다.
 * 목록 필터(서비스)와 거주요건 판정(rule)이 같은 기준으로 비교하도록 domain 에 둔다.
 */
public final class RegionNames {

    /** 화면에서 고르는 시·도 — 청약홈 `SUBSCRPT_AREA_CODE_NM` 표기 그대로 */
    public static final List<String> SIDO = List.of("서울", "경기", "인천", "부산", "대구", "광주", "대전", "울산",
            "세종", "강원", "충북", "충남", "전북", "전남", "경북", "경남", "제주");

    private RegionNames() {
    }

    /** 원천 데이터의 시·도 표기를 짧은 표기로. 모르는 값은 앞뒤 공백만 뗀다. */
    public static String canonical(String raw) {
        if (raw == null || raw.isBlank()) return raw;
        return switch (raw.trim()) {
            case "서울특별시" -> "서울";
            case "경기도" -> "경기";
            case "부산광역시" -> "부산";
            case "대구광역시" -> "대구";
            case "대전광역시" -> "대전";
            case "인천광역시" -> "인천";
            case "광주광역시" -> "광주";
            case "울산광역시" -> "울산";
            case "세종특별자치시" -> "세종";
            case "충청북도" -> "충북";
            case "충청남도" -> "충남";
            case "전라북도", "전북특별자치도" -> "전북";
            case "전라남도" -> "전남";
            case "경상북도" -> "경북";
            case "경상남도" -> "경남";
            case "강원도", "강원특별자치도" -> "강원";
            case "제주특별자치도" -> "제주";
            default -> raw.trim();
        };
    }
}
