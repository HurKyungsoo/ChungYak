package com.portfolio.chungyak.web.view;

import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.domain.UnitType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DetailHighlightsTest {

    private static UnitType unit(String name, Integer topAmount, String area, int general, int special) {
        return UnitType.builder().modelNo(name).typeName(name).topAmount(topAmount).supplyArea(area)
                .generalSupplyCount(general).specialSupplyCount(special).build();
    }

    @Test
    @DisplayName("가장 비싼 주택형의 최고가·평당가, 총 공급과 특공/일반 합계")
    void picksMostExpensiveUnit() {
        Announcement a = Announcement.builder().totalSupplyCount(314).build();
        a.addUnitType(unit("84A", 84433, "115.37", 35, 49));
        a.addUnitType(unit("84B", 86878, "116.39", 38, 54));

        DetailHighlights h = DetailHighlights.of(a);

        assertThat(h.topPrice()).isEqualTo("8억 6,878만원");
        assertThat(h.topPriceType()).isEqualTo("84B");
        assertThat(h.perPyeong()).isEqualTo("2,468만원");   // 86878 / (116.39 / 3.3058)
        assertThat(h.supplyAreaNote()).isEqualTo("공급면적 116.39㎡ (35.2평)");
        assertThat(h.totalSupply()).isEqualTo(314);
        assertThat(h.specialSum()).isEqualTo(103);
        assertThat(h.generalSum()).isEqualTo(73);
        assertThat(h.hasSplit()).isTrue();
        assertThat(h.specialPercent()).isEqualTo(59);
    }

    @Test
    @DisplayName("값이 없으면 null 로 둔다 — 화면이 카드를 숨긴다")
    void nullsWhenMissing() {
        Announcement a = Announcement.builder().build();
        a.addUnitType(unit("26A", null, null, 40, 0));

        DetailHighlights h = DetailHighlights.of(a);

        assertThat(h.topPrice()).isNull();
        assertThat(h.perPyeong()).isNull();
        assertThat(h.totalSupply()).isNull();
        assertThat(h.hasSplit()).isFalse();   // 특공 0 이면 비중 막대 없음
    }

    @Test
    @DisplayName("면적이 없으면 평당가만 비운다(최고가는 그대로)")
    void perPyeongNeedsArea() {
        Announcement a = Announcement.builder().build();
        a.addUnitType(unit("59A", 50000, " ", 10, 5));

        DetailHighlights h = DetailHighlights.of(a);

        assertThat(h.topPrice()).isEqualTo("5억원");
        assertThat(h.perPyeong()).isNull();
        assertThat(h.supplyAreaNote()).isNull();
    }
}
