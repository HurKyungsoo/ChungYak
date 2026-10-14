package com.portfolio.chungyak.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 공고 계보 — 원래 공고와 그 정정본·취소공고를 한 묶음으로 본다. (#47)
 *
 * LH 목록에서 확인한 사실(2026-10-14 라이브, 주택 공고 310건):
 * <ul>
 *   <li>정정·취소는 새 PAN_ID 로 올라오고 제목 태그로만 구별된다 — "[정정공고]", "(정정)", "(정정공고)", "[취소공고]".</li>
 *   <li>{@code PAN_DT}(최초 공고일)는 원래 공고와 정정본이 같다 (예: 대구연호 원본 2015122300020855 ↔ 정정
 *       2015122300020878 둘 다 20260930). 그래서 계보 키 = 최초 공고일 + 세부유형(AIS_TP_CD) + 태그 뗀 제목.</li>
 *   <li>정정본이 나오면 원래 공고의 {@code PAN_SS} 가 "정정공고중"이 된다 — 제목이 바뀌어 묶이지 않아도 낡은 본을 안다.</li>
 *   <li>취소공고는 상태 코드가 따로 없다. "[취소공고]태백철암1 …"(…0645)이 같은 날 원래 공고(…0638)를 가리키고,
 *       다시 낸 공고(…0735)는 최초 공고일이 달라 다른 계보다.</li>
 * </ul>
 */
public final class NoticeLineage {

    /** 원래 공고의 PAN_SS — 정정공고가 나와 낡았다는 원천의 표시 */
    public static final String PROVIDER_SUPERSEDED = "정정공고중";

    /** [..] 또는 (..) 태그 하나 */
    private static final Pattern TAG = Pattern.compile("\\[([^\\]]*)]|\\(([^)]*)\\)");
    private static final Pattern SPACES = Pattern.compile("\\s+");
    /** 이보다 긴 괄호는 태그가 아니라 본문이다 — "(소득, 자산, 무주택 요건 완화 …)" 같은 것 */
    private static final int MAX_TAG_LENGTH = 10;

    private NoticeLineage() {}

    /** 제목 태그로 공고 종류를 가른다. 취소가 정정보다 먼저다("[정정공고][취소공고]"면 취소). */
    public static NoticeKind kindOf(String title) {
        if (title == null) return NoticeKind.ORIGINAL;
        boolean correction = false;
        Matcher m = TAG.matcher(title);
        while (m.find()) {
            String tag = tagText(m);
            if (tag.length() > MAX_TAG_LENGTH) continue;
            if (tag.contains("취소")) return NoticeKind.CANCELLATION;
            if (tag.contains("정정")) correction = true;
        }
        return correction ? NoticeKind.CORRECTION : NoticeKind.ORIGINAL;
    }

    /**
     * 같은 계보를 찾는 키. 최초 공고일이 없으면 묶지 않는다(null) — 제목만으로 묶으면
     * 해마다 같은 이름으로 내는 정례 모집이 한 계보가 된다.
     * 64자 hex 라 MariaDB 행 상한·인덱스 길이를 걱정하지 않아도 된다.
     */
    public static String lineageKey(String source, String originalNoticeDate, String subTypeCode, String title) {
        if (originalNoticeDate == null || originalNoticeDate.isBlank() || title == null) return null;
        String raw = source + "|" + originalNoticeDate.trim() + "|" + (subTypeCode == null ? "" : subTypeCode)
                + "|" + normalizeTitle(title);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);   // 모든 JVM 에 SHA-256 은 있다
        }
    }

    /**
     * 정정·취소 태그와 [지역본부] 같은 대괄호 태그, 공백을 뗀 제목.
     * 정정본은 "[정정공고][조기접수마감]양산 삼성파크빌 …"처럼 태그를 더 달기도 해서 대괄호는 전부 뗀다.
     * 소괄호는 날짜·평형 같은 본문이 많아 정정·취소 태그만 뗀다.
     */
    static String normalizeTitle(String title) {
        StringBuilder sb = new StringBuilder();
        Matcher m = TAG.matcher(title);
        while (m.find()) {
            boolean square = m.group(1) != null;
            String tag = tagText(m);
            boolean revisionTag = tag.length() <= MAX_TAG_LENGTH && (tag.contains("정정") || tag.contains("취소"));
            m.appendReplacement(sb, square || revisionTag ? "" : Matcher.quoteReplacement(m.group()));
        }
        m.appendTail(sb);
        return SPACES.matcher(sb).replaceAll("");
    }

    private static String tagText(Matcher m) {
        return (m.group(1) != null ? m.group(1) : m.group(2)).trim();
    }

    /**
     * 한 계보의 공고들에 상태를 매긴다.
     * <ol>
     *   <li>취소공고 자신, 그리고 가장 늦은 취소공고의 공고일 이전(같은 날 포함)에 나온 공고 → CANCELLED.
     *       취소 뒤에 같은 계보로 다시 나온 공고가 있으면 그건 살린다.</li>
     *   <li>남은 공고 중 가장 늦은 것(공고일, 같으면 외부 ID 순) → CURRENT, 나머지 → SUPERSEDED.</li>
     *   <li>원천이 "정정공고중"이라고 한 공고는 마지막이어도 SUPERSEDED — 정정본 제목이 달라 같은 계보로 안 묶인 경우.</li>
     * </ol>
     * 계보 키가 없는 공고는 혼자 한 계보로 넘기면 된다.
     */
    public static void resolve(List<Announcement> members) {
        LocalDate lastCancel = members.stream()
                .filter(a -> a.getNoticeKind() == NoticeKind.CANCELLATION)
                .map(a -> a.getNoticeDate() == null ? LocalDate.MIN : a.getNoticeDate())
                .max(Comparator.naturalOrder())
                .orElse(null);

        List<Announcement> alive = members.stream()
                .filter(a -> {
                    boolean cancelled = a.getNoticeKind() == NoticeKind.CANCELLATION
                            || (lastCancel != null && (a.getNoticeDate() == null || !a.getNoticeDate().isAfter(lastCancel)));
                    if (cancelled) a.applyRevisionState(RevisionState.CANCELLED);
                    return !cancelled;
                })
                .sorted(Comparator.comparing(Announcement::getNoticeDate, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(Announcement::getExternalId))
                .toList();

        for (int i = 0; i < alive.size(); i++) {
            Announcement a = alive.get(i);
            boolean latest = i == alive.size() - 1;
            boolean providerSuperseded = PROVIDER_SUPERSEDED.equals(a.getProviderStatus());
            a.applyRevisionState(latest && !providerSuperseded ? RevisionState.CURRENT : RevisionState.SUPERSEDED);
        }
    }
}
