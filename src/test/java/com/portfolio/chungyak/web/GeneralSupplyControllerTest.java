package com.portfolio.chungyak.web;

import com.portfolio.chungyak.rule.GeneralSupplyScore;
import com.portfolio.chungyak.rule.GeneralSupplyScoreCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 가점 계산 화면 — 폼 제출이 계산기를 태우고 결과 뷰로 가는지.
 */
class GeneralSupplyControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new GeneralSupplyController(new GeneralSupplyScoreCalculator()))
                .build();
    }

    @Test
    @DisplayName("POST /general-supply — 계산 결과가 모델에 담겨 결과 뷰로")
    void calculatesAndRenders() throws Exception {
        var result = mockMvc.perform(post("/general-supply")
                        .param("age", "40").param("married", "true")
                        .param("houselessMonths", "60")
                        .param("dependents", "2")
                        .param("accountMonths", "84"))
                .andExpect(status().isOk())
                .andExpect(view().name("general-supply/result"))
                .andReturn();

        GeneralSupplyScore score = (GeneralSupplyScore) result.getModelAndView().getModel().get("score");
        assertThat(score.total()).isEqualTo(36);   // 무주택 12 + 부양 15 + 통장 9
    }

    @Test
    @DisplayName("빈 값이 있으면 총점 없이 산정 불가 항목을 담아 렌더")
    void incompleteInputRenders() throws Exception {
        mockMvc.perform(post("/general-supply")
                        .param("married", "true")
                        .param("dependents", "2"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("score"));
    }

    @Test
    @DisplayName("가점 결과 레코드는 이유(detail)를 담는다")
    void scoreCarriesReasons() {
        GeneralSupplyScore score = new GeneralSupplyScoreCalculator().calculate(
                new com.portfolio.chungyak.rule.GeneralSupplyInput(40, true, 60, 2, 84));

        org.assertj.core.api.Assertions.assertThat(score.houselessPeriod().detail()).isNotBlank();
        org.assertj.core.api.Assertions.assertThat(score.dependents().detail()).isNotBlank();
        org.assertj.core.api.Assertions.assertThat(score.accountPeriod().detail()).isNotBlank();
    }
}
