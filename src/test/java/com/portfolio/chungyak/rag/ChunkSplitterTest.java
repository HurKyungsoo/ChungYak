package com.portfolio.chungyak.rag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 청크 분할 — 순수 함수. 목표 길이·겹침·경계 존중을 검증한다.
 */
class ChunkSplitterTest {

    private final ChunkSplitter splitter = new ChunkSplitter(200, 40);

    @Test
    @DisplayName("빈/공백 입력은 청크 없음")
    void emptyInput() {
        assertThat(splitter.split(null)).isEmpty();
        assertThat(splitter.split("   \n  ")).isEmpty();
    }

    @Test
    @DisplayName("목표 길이보다 짧으면 한 덩어리")
    void shortTextIsOneChunk() {
        List<String> chunks = splitter.split("입주자모집공고 잔여세대 신청 안내입니다.");
        assertThat(chunks).hasSize(1);
    }

    @Test
    @DisplayName("긴 문단은 목표 길이 근처로 쪼개지고 각 청크가 상한을 크게 넘지 않는다")
    void longTextIsSplit() {
        String para = "가".repeat(150) + ". " + "나".repeat(150) + ". " + "다".repeat(150) + ".";
        List<String> chunks = splitter.split(para);

        assertThat(chunks).hasSizeGreaterThan(1);
        // 한 청크가 목표(200) + overlap(40) 근처를 크게 넘지 않는다
        assertThat(chunks).allSatisfy(c -> assertThat(c.length()).isLessThanOrEqualTo(245));
    }

    @Test
    @DisplayName("인접 청크는 겹치므로 전체 합이 원문보다 길다 (경계 문장 유실 방지)")
    void adjacentChunksOverlap() {
        String noOverlap = String.join("", new ChunkSplitter(200, 0)
                .split("가".repeat(150) + ". " + "나".repeat(150) + ". " + "다".repeat(150) + "."));
        String withOverlap = String.join("", new ChunkSplitter(200, 40)
                .split("가".repeat(150) + ". " + "나".repeat(150) + ". " + "다".repeat(150) + "."));

        assertThat(withOverlap.length()).isGreaterThan(noOverlap.length());
    }

    @Test
    @DisplayName("문단 경계(빈 줄)를 우선 존중한다")
    void respectsParagraphs() {
        String text = "첫 문단. 짧다.\n\n두 번째 문단도 짧다.\n\n세 번째.";
        List<String> chunks = splitter.split(text);
        // 다 합쳐도 200 미만이라 한 청크. 문단 구분은 개행으로 남는다.
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).contains("첫 문단").contains("세 번째");
    }

    @Test
    @DisplayName("한 문장이 목표 길이를 넘으면 강제로 잘라도 내용은 보존된다")
    void oversizeSentenceIsHardCut() {
        String monster = "조건" + "요건내용".repeat(200);   // 800자 한 문장
        List<String> chunks = splitter.split(monster);
        assertThat(String.join("", chunks).replace("\n", "")).contains("요건내용");
        // 강제로 자른 조각은 목표 길이(200)로 딱 떨어진다
        assertThat(chunks).allSatisfy(c -> assertThat(c.length()).isLessThanOrEqualTo(200));
    }
}
