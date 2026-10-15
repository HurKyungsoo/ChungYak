package com.portfolio.chungyak.rule;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * application.yml 의 도시근로자 월평균소득 표 모양 — 규칙 테스트는 고정 표(RuleTestSupport)를 써서 운영 설정이 낡아도 못 잡는다.
 * 3인 이하는 한 값이어야 한다: 주택공급규칙·공공주택 특별법 [별표 6] 모두 "4명 이상인 세대는 가구원수별"(2026-10-15 원문 확인).
 */
class IncomeReferenceConfigTest {

    @Test
    @DisplayName("운영 표 — 1·2·3인은 같은 값(가구당 월평균소득), 4~8인은 늘어난다, 기준 연도 있음")
    @SuppressWarnings("unchecked")
    void productionTableShape() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/application.yml")) {
            Map<String, Object> root = new Yaml().load(in);
            Map<String, Object> ref = (Map<String, Object>) root.get("income-reference");
            Map<Integer, Number> t = (Map<Integer, Number>) ref.get("monthly-average");

            assertThat(String.valueOf(ref.get("basis-year"))).matches("\\d{4}");
            assertThat(t.get(1).longValue()).isEqualTo(t.get(3).longValue());
            assertThat(t.get(2).longValue()).isEqualTo(t.get(3).longValue());
            for (int size = 4; size <= 8; size++) {
                assertThat(t.get(size).longValue()).as(size + "인").isGreaterThan(t.get(size - 1).longValue());
            }
        }
    }
}
