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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 자격 판정 컨트롤러 — PRG(Post-Redirect-Get). POST 는 폼을 저장하고 GET 결과 URL 로
 * 리다이렉트만 하며, 실제 판정은 그 GET({@link EligibilityController#result})이 한다.
 * AI 요약은 여전히 explain=true 일 때만(그 GET 시점에) 호출된다.
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
                queryService, engine, assembler, extraction, explanationService, incomeReference,
                new EligibilityResultStore())).build();
    }

    @Test
    @DisplayName("판정 POST 는 렌더링하지 않고 GET 결과 URL 로 리다이렉트한다 (새로고침·북마크 대응)")
    void postRedirectsToGetResultUrl() throws Exception {
        mockMvc.perform(post("/announcements/1/eligibility").param("childCount", "2"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/announcements/1/eligibility/result/*"));

        verify(engine, never()).evaluate(any(), any());   // 판정은 아직 GET 에서 안 함
    }

    @Test
    @DisplayName("리다이렉트된 GET 결과 URL 은 판정을 렌더링한다 (explain 없음 → LLM 요약 호출 안 함)")
    void getResultJudgesWithoutSummaryByDefault() throws Exception {
        MvcResult redirect = mockMvc.perform(post("/announcements/1/eligibility").param("childCount", "2"))
                .andReturn();
        String location = redirect.getResponse().getRedirectedUrl();

        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(view().name("announcements/eligibility-result"))
                .andExpect(model().attributeExists("result", "explanationAvailable", "form"))
                .andExpect(model().attributeDoesNotExist("explanation"));

        verify(engine).evaluate(any(), any());
        verify(explanationService, never()).explain(any());
    }

    @Test
    @DisplayName("같은 결과 URL 을 새로고침(다시 GET)해도 매번 정상 렌더링된다 (재제출 경고 없음)")
    void refreshingResultUrlWorksRepeatedly() throws Exception {
        MvcResult redirect = mockMvc.perform(post("/announcements/1/eligibility").param("childCount", "2"))
                .andReturn();
        String location = redirect.getResponse().getRedirectedUrl();

        mockMvc.perform(get(location)).andExpect(status().isOk());
        mockMvc.perform(get(location)).andExpect(status().isOk());   // 새로고침

        verify(engine, times(2)).evaluate(any(), any());
    }

    @Test
    @DisplayName("explain=true (AI 요약 보기 버튼) 로 저장된 폼을 GET 하면 그때 LLM 요약을 호출한다")
    void summarizesOnDemandOnGet() throws Exception {
        MvcResult redirect = mockMvc.perform(post("/announcements/1/eligibility")
                        .param("childCount", "2").param("explain", "true"))
                .andReturn();

        mockMvc.perform(get(redirect.getResponse().getRedirectedUrl()))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("explanation"));

        verify(explanationService).explain(any());
    }

    @Test
    @DisplayName("존재하지 않거나 만료된 토큰으로 GET 하면 폼 화면으로 돌려보낸다")
    void unknownTokenRedirectsBackToForm() throws Exception {
        mockMvc.perform(get("/announcements/1/eligibility/result/does-not-exist"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/announcements/1/eligibility"));

        verify(engine, never()).evaluate(any(), any());
    }
}
