package com.portfolio.chungyak.external;

import com.portfolio.chungyak.domain.HouseDetailType;
import com.portfolio.chungyak.domain.HouseType;
import com.portfolio.chungyak.domain.SupplyCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 청약홈 무순위·잔여세대(getRemndrLttotPblancDetail) 파싱 — 픽스처는 2026-10-13 라이브 응답(2026910263)을 본뜬 것.
 * APT 와 달리 접수일이 SUBSCRPT_RCEPT_BGNDE/ENDDE 이고 규제·민영/국민 구분이 없다.
 */
class ApplyhomeRemainderParseTest {

    private final ApplyhomeClient client = new ApplyhomeClient(null, null, new PublicDataParser());

    private List<ExternalAnnouncement> parse() throws Exception {
        try (var in = getClass().getResourceAsStream("/fixtures/applyhome-remainder-detail.json")) {
            return client.parseRemainderAnnouncements(in.readAllBytes());
        }
    }

    @Test
    @DisplayName("무순위 공고 — 구분 REMAINDER, 접수일은 SUBSCRPT_RCEPT_*, 외부 id 는 APT 와 겹치지 않게 접두사")
    void parsesRemainder() throws Exception {
        ExternalAnnouncement a = parse().get(0);

        assertThat(a.getExternalId()).isEqualTo("APPLYHOME-RM-2026910263-2026910263");
        assertThat(a.getHouseName()).isEqualTo("청계 노르웨이숲(13차)");
        assertThat(a.getHouseType()).isEqualTo(HouseType.REMAINDER);
        assertThat(a.getSupplyCategory()).isEqualTo(SupplyCategory.REMAINDER);
        assertThat(a.getHouseDetailType()).isEqualTo(HouseDetailType.UNKNOWN);
        assertThat(a.getReceptBeginDate()).isEqualTo(LocalDate.of(2026, 10, 19));
        assertThat(a.getReceptEndDate()).isEqualTo(LocalDate.of(2026, 10, 19));
        assertThat(a.getWinnerAnnounceDate()).isEqualTo(LocalDate.of(2026, 10, 22));
        assertThat(a.getTotalSupplyCount()).isEqualTo(10);
        assertThat(a.getRegionName()).isEqualTo("서울");
        assertThat(a.getRegulationFlags().isRegulatedArea()).isFalse();
    }

    @Test
    @DisplayName("불법행위 재공급(06)도 무순위 구분으로, 이름 없는 행은 스킵한다")
    void resupplyAndInvalidRows() throws Exception {
        List<ExternalAnnouncement> list = parse();

        assertThat(list).hasSize(2);
        assertThat(list.get(1).getHouseType()).isEqualTo(HouseType.ILLEGAL_RESUPPLY);
        assertThat(list.get(1).getSupplyCategory()).isEqualTo(SupplyCategory.REMAINDER);
    }
}
