package com.portfolio.chungyak.web;

import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.domain.HouseDetailType;
import com.portfolio.chungyak.domain.HouseType;
import com.portfolio.chungyak.llm.ExplanationResult;
import com.portfolio.chungyak.llm.ExplanationService;
import com.portfolio.chungyak.llm.ProfileExtractionService;
import com.portfolio.chungyak.rule.EligibilityEngine;
import com.portfolio.chungyak.rule.EligibilityEngine.MatchResult;
import com.portfolio.chungyak.rule.IncomeReference;
import com.portfolio.chungyak.service.AnnouncementQueryService;
import com.portfolio.chungyak.web.view.EligibilityResultAssembler;
import com.portfolio.chungyak.web.view.EligibilityResultView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 자격 판정 컨트롤러 — AI 요약은 온디맨드(버튼)로만 호출된다.
 * 판정 자체는 explain 값과 무관하게 항상 이뤄진다.
 */
class EligibilityControllerTest {

    private ExplanationService explanationService;
    private EligibilityEngine engine;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AnnouncementQueryService queryService = mock(AnnouncementQueryService.class);
        engine = mock(EligibilityEngine.class);
        EligibilityResultAssembler assembler = mock(EligibilityResultAssembler.class);
        ProfileExtractionService extraction = mock(ProfileExtractionService.class);
        explanationService = mock(ExplanationService.class);
        IncomeReference incomeReference = mock(IncomeReference.class);

        Announcement a = Announcement.builder()
                .externalId("T").houseManageNo("1").pblancNo("1").houseName("테스트")
                .houseType(HouseType.APT).houseDetailType(HouseDetailType.PRIVATE)
                .build();
        when(queryService.findDetail(1L)).thenReturn(Optional.of(a));
        when(queryService.statusOf(any())).thenReturn("접수중");
        when(engine.evaluate(any(), any())).thenReturn(new MatchResult(a, Map.of(), List.of()));
        when(assembler.assemble(any())).thenReturn(new EligibilityResultView(
                1L, "테스트", false, false, List.of(), List.of(), List.of()));
        when(explanationService.isAvailable()).thenReturn(true);
        when(explanationService.explain(any())).thenReturn(ExplanationResult.ai("요약입니다"));
        when(incomeReference.basisYear()).thenReturn("2024");

        mockMvc = MockMvcBuilders.standaloneSetup(new EligibilityController(
                queryService, engine, assembler, extraction, explanationService, incomeReference)).build();
    }

    @Test
    @DisplayName("판정만 요청하면 (explain 없음) → LLM 요약을 호출하지 않는다")
    void judgesWithoutSummaryByDefault() throws Exception {
        mockMvc.perform(post("/announcements/1/eligibility").param("childCount", "2"))
                .andExpect(status().isOk())
                .andExpect(view().name("announcements/eligibility-result"))
                .andExpect(model().attributeExists("result", "explanationAvailable", "form"))
                .andExpect(model().attributeDoesNotExist("explanation"));

        verify(engine).evaluate(any(), any());
        verify(explanationService, never()).explain(any());
    }

    @Test
    @DisplayName("explain=true (AI 요약 보기 버튼) → 그때만 LLM 요약을 호출한다")
    void summarizesOnDemand() throws Exception {
        mockMvc.perform(post("/announcements/1/eligibility")
                        .param("childCount", "2").param("explain", "true"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("explanation"));

        verify(explanationService).explain(any());
    }
}
