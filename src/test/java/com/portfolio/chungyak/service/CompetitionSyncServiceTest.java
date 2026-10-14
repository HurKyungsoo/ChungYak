package com.portfolio.chungyak.service;

import com.portfolio.chungyak.external.ApplyhomeCompetitionClient.Page;
import com.portfolio.chungyak.external.CompetitionRecords.Score;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 당첨가점·경쟁률 수집의 페이지 모으기 (#34) */
class CompetitionSyncServiceTest {

    private static Score s(String house) {
        return new Score(house, "01", "084.9A", "01", 50, 55, 60);
    }

    @Test
    @DisplayName("다 받지 못했으면 마지막 공고는 잘렸을 수 있어 뺀다")
    void dropsPossiblyTruncatedLastHouse() {
        List<Page<Score>> pages = List.of(
                new Page<>(List.of(s("A"), s("A"), s("B")), 10),
                new Page<>(List.of(s("B"), s("C")), 10));

        Map<String, List<Score>> got = CompetitionSyncService.collect((page, per) -> pages.get(page - 1), Score::houseManageNo, 2, 3);

        assertThat(got).containsOnlyKeys("A", "B");   // C 는 3페이지에 이어질 수 있다
        assertThat(got.get("B")).hasSize(2);           // 페이지 경계에 걸친 B 는 두 페이지 행을 합친다
    }

    @Test
    @DisplayName("끝까지 받았으면 마지막 공고도 넣는다")
    void keepsLastWhenComplete() {
        List<Page<Score>> pages = List.of(new Page<>(List.of(s("A"), s("B")), 2));

        Map<String, List<Score>> got = CompetitionSyncService.collect((page, per) -> page == 1 ? pages.get(0) : new Page<>(List.of(), 2),
                Score::houseManageNo, 5, 100);

        assertThat(got).containsOnlyKeys("A", "B");
    }
}
