package com.portfolio.chungyak.web;

import com.portfolio.chungyak.rule.ApplicantProfile;
import com.portfolio.chungyak.service.AnnouncementMatchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * "내 조건으로 공고 찾기" — {@link EligibilityControllerTest} 와 같은 PRG 검증.
 * POST 는 렌더링하지 않고 토큰 GET 으로 리다이렉트만 하며, 실제 순회·판정은 그 GET 이 한다.
 */
class MatchControllerTest {

    private AnnouncementMatchService matchService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        matchService = mock(AnnouncementMatchService.class);
        when(matchService.findMatching(any())).thenReturn(List.of());

        mockMvc = MockMvcBuilders.standaloneSetup(
                new MatchController(new MatchResultStore(), matchService)).build();
    }

    @Test
    @DisplayName("조건 폼 GET 은 바로 렌더링된다")
    void formRenders() throws Exception {
        mockMvc.perform(get("/match"))
                .andExpect(status().isOk())
                .andExpect(view().name("match/form"))
                .andExpect(model().attributeExists("form"));
    }

    @Test
    @DisplayName("조건 POST 는 렌더링하지 않고 GET 결과 URL 로 리다이렉트한다")
    void postRedirectsToGetResultUrl() throws Exception {
        mockMvc.perform(post("/match").param("married", "true").param("houseless", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/match/result/*"));

        verify(matchService, never()).findMatching(any());   // 순회·판정은 아직 GET 에서 안 함
    }

    @Test
    @DisplayName("리다이렉트된 GET 결과 URL 이 실제 순회·판정을 실행하고 결과를 렌더링한다")
    void getResultRunsMatching() throws Exception {
        MvcResult redirect = mockMvc.perform(post("/match").param("houseless", "true")).andReturn();
        String location = redirect.getResponse().getRedirectedUrl();

        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(view().name("match/result"))
                .andExpect(model().attributeExists("rows", "form"));

        verify(matchService).findMatching(any(ApplicantProfile.class));
    }

    @Test
    @DisplayName("같은 결과 URL 을 새로고침해도 매번 정상 렌더링된다 (재제출 경고 없음)")
    void refreshingResultUrlWorksRepeatedly() throws Exception {
        MvcResult redirect = mockMvc.perform(post("/match").param("houseless", "true")).andReturn();
        String location = redirect.getResponse().getRedirectedUrl();

        mockMvc.perform(get(location)).andExpect(status().isOk());
        mockMvc.perform(get(location)).andExpect(status().isOk());

        verify(matchService, times(2)).findMatching(any());
    }

    @Test
    @DisplayName("존재하지 않거나 만료된 토큰으로 GET 하면 폼 화면으로 돌려보낸다")
    void unknownTokenRedirectsBackToForm() throws Exception {
        mockMvc.perform(get("/match/result/does-not-exist"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/match"));

        verify(matchService, never()).findMatching(any());
    }
}
