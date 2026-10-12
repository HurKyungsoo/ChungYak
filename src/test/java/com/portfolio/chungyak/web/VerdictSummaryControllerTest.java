package com.portfolio.chungyak.web;

import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.domain.HouseDetailType;
import com.portfolio.chungyak.domain.HouseType;
import com.portfolio.chungyak.domain.RegulationFlags;
import com.portfolio.chungyak.domain.SupplyBreakdown;
import com.portfolio.chungyak.domain.UnitType;
import com.portfolio.chungyak.rule.EligibilityEngine;
import com.portfolio.chungyak.rule.RuleTestSupport;
import com.portfolio.chungyak.service.AnnouncementQueryService;
import com.portfolio.chungyak.web.view.EligibilityResultAssembler;
import com.portfolio.chungyak.web.view.EligibilityResultView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/** 공고 상세 "내 판정 요약" — 저장된 조건을 본문으로 받아 판정 조각만 그린다. */
class VerdictSummaryControllerTest {

    private AnnouncementQueryService queryService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        queryService = mock(AnnouncementQueryService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new VerdictSummaryController(queryService,
                new EligibilityEngine(RuleTestSupport.allRules()), new EligibilityResultAssembler(),
                mock(com.portfolio.chungyak.service.AnnouncementMatchService.class))).build();
    }

    @Test
    @DisplayName("조건을 POST 하면 규칙 엔진 결과로 요약 조각을 돌려준다")
    void rendersSummaryFragment() throws Exception {
        Announcement a = Announcement.builder().id(11L).externalId("APT-1-1").houseManageNo("1").pblancNo("1")
                .houseName("더샵").houseType(HouseType.APT).houseDetailType(HouseDetailType.PRIVATE)
                .regulationFlags(RegulationFlags.builder().build()).build();
        a.addUnitType(UnitType.builder().modelNo("01").typeName("84A")
                .supplyBreakdown(SupplyBreakdown.builder().newlywed(48).build()).build());
        when(queryService.findDetail(11L)).thenReturn(Optional.of(a));

        MvcResult r = mockMvc.perform(post("/announcements/11/verdict-summary")
                        .param("married", "true").param("monthsSinceMarriage", "52")
                        .param("houseless", "true").param("accountMonths", "30")
                        .param("monthlyHouseholdIncome", "5000000").param("householdSize", "3"))
                .andExpect(status().isOk())
                .andExpect(view().name("announcements/verdict-summary :: card"))
                .andReturn();

        EligibilityResultView result = (EligibilityResultView) r.getModelAndView().getModel().get("result");
        assertThat(result.allDecisions()).filteredOn(d -> d.typeName().equals("NEWLYWED"))
                .singleElement()   // 판정 자체(충족/미충족)는 EligibilityEngineTest 몫 — 여기선 옮겨 담기만 확인
                .satisfies(d -> assertThat(d.allocatedInAnnouncement()).isEqualTo(48));
    }

    @Test
    @DisplayName("없는 공고면 404")
    void unknownAnnouncement() throws Exception {
        when(queryService.findDetail(99L)).thenReturn(Optional.empty());
        mockMvc.perform(post("/announcements/99/verdict-summary")).andExpect(status().isNotFound());
    }
}
