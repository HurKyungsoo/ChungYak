package com.portfolio.chungyak.rag;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 공고문 원문을 검색 단위로 쪼갠다.
 *
 * 순수 함수(설정값 주입 외 상태 없음) — LLM 없이 단위테스트로 검증한다.
 * 규칙:
 *  - 문단(빈 줄) 경계를 우선 존중하고, 한 문단이 목표 길이를 넘으면 문장(。.!?\n) 경계로 자른다.
 *  - 청크 사이에 {@code overlap} 문자만큼 겹치게 해서 경계에 걸친 문장이 사라지지 않게 한다.
 *  - 공백만 남는 조각은 버린다.
 */
@Component
public class ChunkSplitter {

    private final int targetSize;
    private final int overlap;

    @Autowired
    public ChunkSplitter(RagProperties properties) {
        this.targetSize = properties.chunk().size();
        this.overlap = properties.chunk().overlap();
    }

    ChunkSplitter(int targetSize, int overlap) {
        this.targetSize = targetSize;
        this.overlap = overlap;
    }

    public List<String> split(String text) {
        if (text == null || text.isBlank()) return List.of();
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n').strip();

        // 1) 문단·문장 단위 조각으로 분해
        List<String> units = new ArrayList<>();
        for (String para : normalized.split("\n{2,}")) {
            String p = para.strip();
            if (p.isEmpty()) continue;
            if (p.length() <= targetSize) {
                units.add(p);
            } else {
                units.addAll(splitLongParagraph(p));
            }
        }

        // 2) 목표 길이까지 조각을 이어붙이고, 청크 사이에 overlap 을 준다
        List<String> chunks = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String unit : units) {
            // 조각 하나가 이미 목표 길이 이상이면(강제로 자른 긴 문장) 그대로 한 청크로 둔다
            if (unit.length() >= targetSize) {
                if (cur.length() > 0) { chunks.add(cur.toString()); cur.setLength(0); }
                chunks.add(unit);
                continue;
            }
            if (cur.length() > 0 && cur.length() + 1 + unit.length() > targetSize) {
                chunks.add(cur.toString());
                cur = new StringBuilder(tail(cur.toString(), overlap));
            }
            if (cur.length() > 0) cur.append('\n');
            cur.append(unit);
        }
        if (cur.length() > 0) chunks.add(cur.toString());

        chunks.removeIf(c -> c.isBlank());
        return chunks;
    }

    private List<String> splitLongParagraph(String p) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String sentence : p.split("(?<=[。.!?])\\s+|(?<=\\n)")) {
            String s = sentence.strip();
            if (s.isEmpty()) continue;
            if (cur.length() + 1 + s.length() > targetSize && cur.length() > 0) {
                out.add(cur.toString());
                cur = new StringBuilder();
            }
            if (s.length() > targetSize) {           // 문장 하나가 너무 길면 강제로 자른다
                for (int i = 0; i < s.length(); i += targetSize) {
                    out.add(s.substring(i, Math.min(i + targetSize, s.length())));
                }
                continue;
            }
            if (cur.length() > 0) cur.append(' ');
            cur.append(s);
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    private static String tail(String s, int n) {
        return n <= 0 || s.length() <= n ? "" : s.substring(s.length() - n);
    }
}
