package com.portfolio.chungyak.external;

import com.portfolio.chungyak.domain.SpecialSupplyType;
import com.portfolio.chungyak.external.SpecialSupplyApplication.Count;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 청약홈 특공 신청현황(getAPTSpsplyReqstStus) 파싱 (#33) — 픽스처는 2026-10-13 라이브 응답(2026000374)을 본뜬 것.
 * 필드 이름을 바꾸면 이 테스트가 깨진다(CLAUDE.md "데이터에서 확인된 사실").
 */
class ApplyhomeSpecialSupplyParseTest {

    private final ApplyhomeClient client = new ApplyhomeClient(null, null, new PublicDataParser());

    private List<SpecialSupplyApplication> parse() throws Exception {
        try (var in = getClass().getResourceAsStream("/fixtures/applyhome-spsply-status.json")) {
            return client.parseSpecialSupplyApplications(in.readAllBytes());
        }
    }

    @Test
    @DisplayName("유형별 배정 세대 + 신청 건수(해당·시도·기타지역 합) — 생애최초 1세대에 2,461 + 489 = 2,950건")
    void parsesTypesAndSumsAreas() throws Exception {
        SpecialSupplyApplication first = parse().get(0);

        assertThat(first.houseType()).isEqualTo("051.9931B");
        assertThat(first.byType()).containsEntry(SpecialSupplyType.FIRST_TIME, new Count(1, 2950))
                .containsEntry(SpecialSupplyType.MULTI_CHILD, new Count(2, 59))
                .containsEntry(SpecialSupplyType.NEWLYWED, new Count(1, 221))
                .containsEntry(SpecialSupplyType.NEWBORN, new Count(1, 155));
        // 배정·신청이 모두 0 인 유형(노부모·청년)은 담지 않는다
        assertThat(first.byType()).doesNotContainKeys(SpecialSupplyType.OLD_PARENTS, SpecialSupplyType.YOUTH);
    }

    @Test
    @DisplayName("숫자 문자열도 읽고, 노부모는 배정 필드 이름이 다르다(OLD_PARNTS_SUPORT_HSHLDCO + OPS_CNT)")
    void readsStringNumbersAndOldParentsField() throws Exception {
        SpecialSupplyApplication second = parse().get(1);

        assertThat(second.byType()).containsEntry(SpecialSupplyType.FIRST_TIME, new Count(2, 150))
                .containsEntry(SpecialSupplyType.OLD_PARENTS, new Count(1, 3));
    }

    @Test
    @DisplayName("숫자가 아닌 값이 있는 행은 그 행만 스킵한다 — 예외를 던지지 않는다(외부 API 규칙)")
    void skipsBrokenRow() throws Exception {
        assertThat(parse()).hasSize(2);
        assertThat(client.parseSpecialSupplyApplications("{}".getBytes())).isEmpty();
        assertThat(client.parseSpecialSupplyApplications(null)).isEmpty();
    }
}
