package com.portfolio.chungyak.rag;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 국토부 「2024 주택청약 FAQ」 480문항(질문·답 원문)과 그 뒤 「주택공급에 관한 규칙」 개정 목록.
 *
 * 이용자가 실제로 묻는 건 대부분 "내 상황이 어떻게 세어지나"다(FAQ 표본 신청자 질문의 59% — {@code faq-questions-dev.tsv}).
 * 그 공식 해석은 공고문보다 국토부 FAQ 에 있다. 답은 <b>원문 그대로</b> 보여 주고 LLM 이 바꿔 쓰지 않는다 —
 * 판정이 아니라 국토부 해석을 옮기는 것이다. 기준일이 2024-05-29 라 그 뒤 개정과 겹치는 문항은
 * {@link #amendmentsFor} 로 경고를 붙인다(개정 이유는 국가법령정보센터 문장 그대로).
 *
 * 자료: {@code src/main/resources/faq/} — 만드는 법은 {@code scripts/faq/}.
 */
public record OfficialFaq(String source, String basisDate, String url, List<Item> items, List<Amendment> amendments) {

    public record Item(int no, String part, String section, Integer page, String question, String answer) {

        /** 새 문단으로 둘 줄 — 원문에서 들여쓴 줄, 목록 기호(※ * - ① 가) 1.)로 시작하는 줄 */
        private static final Pattern NEW_PARAGRAPH =
                Pattern.compile("^(\\s{3,}|\\s*[※*\\-•·○◦▶]|\\s*[①-⑳]|\\s*[가-하]\\)|\\s*\\d+[.)]\\s|\\s*\\(\\d+\\))");

        /**
         * 화면용 답 — PDF 줄바꿈(문장 중간에서 끊긴 줄)을 잇는다. 낱말은 바꾸지 않는다.
         * PDF 추출은 줄 끝 띄어쓰기를 잃어 "지방\n자치단체"가 붙여야 할지 띄워야 할지 모른다 — 띄어 쓴다(읽기엔 지장이 없다).
         */
        public String displayAnswer() {
            StringBuilder out = new StringBuilder();
            for (String line : answer.split("\n")) {
                if (line.isBlank()) continue;
                if (out.length() == 0) {
                    out.append(line.strip());
                } else if (NEW_PARAGRAPH.matcher(line).find()) {
                    out.append('\n').append(line.strip());
                } else {
                    out.append(' ').append(line.strip());
                }
            }
            return out.toString().replaceAll(" {2,}", " ");
        }

        /** 긴 답(표가 섞인 소득 산정 등)은 앞부분만 펼쳐 두고 나머지는 접는다 */
        public boolean isLong() {
            return displayAnswer().length() > 700;
        }

        public String displayAnswerHead() {
            String d = displayAnswer();
            if (d.length() <= 700) return d;
            int cut = d.lastIndexOf('\n', 600);
            return d.substring(0, cut > 300 ? cut : 600) + " …";
        }
    }

    public record Amendment(String effective, String decree, String title, String pattern, String reason) {
        boolean touches(Item item) {
            return Pattern.compile(pattern).matcher(item.question() + "\n" + item.answer()).find();
        }
    }

    private record FaqFile(String source, String basisDate, String url, List<Item> items) {}

    private record AmendmentFile(String source, String checkedOn, String after, List<Amendment> amendments) {}

    public static OfficialFaq load() {
        ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        try (InputStream faq = OfficialFaq.class.getResourceAsStream("/faq/molit-faq-2024-05.json");
             InputStream amend = OfficialFaq.class.getResourceAsStream("/faq/amendments.json")) {
            if (faq == null || amend == null) throw new IllegalStateException("faq 자료 없음 — src/main/resources/faq/");
            FaqFile f = mapper.readValue(faq, FaqFile.class);
            AmendmentFile a = mapper.readValue(amend, AmendmentFile.class);
            return new OfficialFaq(f.source(), f.basisDate(), f.url(), List.copyOf(f.items()), List.copyOf(a.amendments()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 이 문항과 겹치는 기준일 뒤 개정(시행일 최신순) */
    public List<Amendment> amendmentsFor(Item item) {
        return amendments.stream().filter(a -> a.touches(item)).toList();
    }

    public Item item(int no) {
        return items.stream().filter(i -> i.no() == no).findFirst().orElseThrow();
    }
}
