package com.portfolio.chungyak.external;

import com.portfolio.chungyak.domain.SpecialSupplyType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LhSpecialSupplyMapperTest {

    private final LhSpecialSupplyMapper mapper = new LhSpecialSupplyMapper();

    @Test
    @DisplayName("유형별 라벨을 SpecialSupplyType 으로 정규화한다")
    void mapsLabels() {
        assertThat(mapper.map("다자녀특별(85㎡이하)")).contains(SpecialSupplyType.MULTI_CHILD);
        assertThat(mapper.map("신혼부부특별")).contains(SpecialSupplyType.NEWLYWED);
        assertThat(mapper.map("예비신혼부부")).contains(SpecialSupplyType.NEWLYWED);
        assertThat(mapper.map("생애최초특별")).contains(SpecialSupplyType.FIRST_TIME);
        assertThat(mapper.map("노부모부양특별(85㎡이하)")).contains(SpecialSupplyType.OLD_PARENTS);
        assertThat(mapper.map("기관추천")).contains(SpecialSupplyType.INSTITUTION_RECOMMEND);
        assertThat(mapper.map("신생아특별")).contains(SpecialSupplyType.NEWBORN);
        assertThat(mapper.map("신생아 우선공급")).contains(SpecialSupplyType.NEWBORN);
        assertThat(mapper.map("청년")).contains(SpecialSupplyType.YOUTH);
    }

    @Test
    @DisplayName("일반공급·순위 라벨은 특별공급이 아니라 비어 있다")
    void generalSupplyIsEmpty() {
        assertThat(mapper.map("일반공급(우선)")).isEmpty();
        assertThat(mapper.map("일반1순위")).isEmpty();
        assertThat(mapper.map("일반2순위")).isEmpty();
        assertThat(mapper.map(null)).isEmpty();
        assertThat(mapper.map("")).isEmpty();
    }

    @Test
    @DisplayName("신생아는 신혼보다 먼저 매칭돼야 한다(라벨에 둘 다 안 겹치지만 방어적으로 확인)")
    void newbornNotMisreadAsNewlywed() {
        assertThat(mapper.map("신생아특별")).contains(SpecialSupplyType.NEWBORN);
    }

    @Test
    @DisplayName("mapAll — 중복 제거하고 집합으로")
    void mapAllDedupes() {
        assertThat(mapper.mapAll(List.of("신혼부부특별", "예비신혼부부", "일반공급(추첨)", "기관추천")))
                .containsExactlyInAnyOrder(SpecialSupplyType.NEWLYWED, SpecialSupplyType.INSTITUTION_RECOMMEND);
    }
}
