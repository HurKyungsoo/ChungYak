package com.portfolio.chungyak.web.view;

import com.portfolio.chungyak.domain.Announcement;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * "내 캘린더에 추가" — 공고 일정을 iCalendar(.ics, RFC 5545) 로 내려준다.
 *
 * 공고에 실린 날짜만 옮긴다: 특별공급 접수 · 청약 접수 · 당첨자 발표. 모르는 날짜는 일정을 만들지 않는다
 * (계약일은 수집하지 않으므로 넣지 않는다 — 지어낸 날짜가 사용자 캘린더에 남으면 더 위험하다).
 * 접수 시각은 원본에 없어 종일 일정으로 둔다. 서버는 파일만 만들고 누구 캘린더에 넣었는지는 모른다.
 */
public final class AnnouncementIcs {

    /** title 일정 이름 · start/end 종일 일정의 첫날·마지막날(포함) */
    public record Event(String key, String title, LocalDate start, LocalDate end) {}

    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

    private AnnouncementIcs() {}

    /** 공고에 날짜가 있는 단계만. 비어 있으면 화면이 "캘린더에 추가" 버튼을 숨긴다. */
    public static List<Event> events(Announcement a) {
        List<Event> events = new ArrayList<>();
        // LH 는 특별공급 접수를 따로 주지 않는다(ScheduleTimeline 과 같은 기준)
        if (!a.isLh()) add(events, "special", "특별공급 접수", a.getSpecialReceptBeginDate(), a.getSpecialReceptEndDate());
        add(events, "recept", "청약 접수", a.getReceptBeginDate(), a.getReceptEndDate());
        add(events, "winner", "당첨자 발표", a.getWinnerAnnounceDate(), a.getWinnerAnnounceDate());
        return events;
    }

    private static void add(List<Event> out, String key, String label, LocalDate start, LocalDate end) {
        if (start == null && end == null) return;
        // 시작을 모르면 마감일 하루짜리로 — 기간을 지어내지 않고 이름에 "마감"을 붙인다.
        if (start == null) { out.add(new Event(key, label + " 마감", end, end)); return; }
        out.add(new Event(key, label, start, end == null || end.isBefore(start) ? start : end));
    }

    /**
     * @param detailUrl 공고 상세 주소 — 일정 설명·URL 에 넣는다
     * @param now       DTSTAMP (파일을 만든 시각)
     */
    public static byte[] render(Announcement a, String detailUrl, ZonedDateTime now) {
        String stamp = STAMP.format(now.withZoneSameInstant(ZoneOffset.UTC));
        String name = a.getHouseName() == null ? "청약 공고" : a.getHouseName();

        List<String> lines = new ArrayList<>();
        lines.add("BEGIN:VCALENDAR");
        lines.add("VERSION:2.0");
        lines.add("PRODID:-//chungyak//announcement//KO");
        lines.add("CALSCALE:GREGORIAN");
        lines.add("METHOD:PUBLISH");
        for (Event e : events(a)) {
            lines.add("BEGIN:VEVENT");
            // 같은 공고·단계는 같은 UID — 다시 받아 넣어도 캘린더 앱이 새 일정 대신 갱신으로 본다.
            lines.add("UID:chungyak-" + a.getId() + "-" + e.key() + "@chungyak");
            lines.add("DTSTAMP:" + stamp);
            lines.add("DTSTART;VALUE=DATE:" + DATE.format(e.start()));
            lines.add("DTEND;VALUE=DATE:" + DATE.format(e.end().plusDays(1)));   // 종일 일정의 끝은 다음 날(미포함)
            lines.add("SUMMARY:" + escape("[" + e.title() + "] " + name));
            lines.add("DESCRIPTION:" + escape("접수 시각·세부 일정은 공고문에서 확인하세요.\n" + detailUrl));
            lines.add("URL:" + detailUrl);
            lines.add("TRANSP:TRANSPARENT");   // 종일 "바쁨"으로 막지 않는다
            lines.add("END:VEVENT");
        }
        lines.add("END:VCALENDAR");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (String line : lines) {
            byte[] folded = fold(line).getBytes(StandardCharsets.UTF_8);
            out.writeBytes(folded);
            out.writeBytes("\r\n".getBytes(StandardCharsets.US_ASCII));
        }
        return out.toByteArray();
    }

    /** TEXT 값 이스케이프 (RFC 5545 3.3.11) — 공고명은 DB 값이라 쉼표·세미콜론이 들어올 수 있다. */
    static String escape(String s) {
        return s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,")
                .replace("\r\n", "\\n").replace("\n", "\\n");
    }

    /**
     * 한 줄 75옥텟 제한 (RFC 5545 3.1). 한글은 UTF-8 로 3바이트라 글자 수로 자르면 넘친다 —
     * 바이트로 세되 글자(코드포인트) 중간에서는 자르지 않는다. 이어지는 줄은 공백 하나로 시작한다.
     */
    static String fold(String line) {
        StringBuilder sb = new StringBuilder();
        int bytes = 0;
        int limit = 75;
        for (int i = 0; i < line.length(); ) {
            int cp = line.codePointAt(i);
            int len = new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8).length;
            if (bytes + len > limit) {
                sb.append("\r\n ");
                bytes = 1;          // 이어지는 줄의 앞 공백도 75옥텟에 든다
            }
            sb.appendCodePoint(cp);
            bytes += len;
            i += Character.charCount(cp);
        }
        return sb.toString();
    }
}
