package com.portfolio.chungyak.web;

import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.domain.HouseDetailType;
import com.portfolio.chungyak.domain.HouseType;
import com.portfolio.chungyak.rag.DocumentQaService;
import com.portfolio.chungyak.rule.GeneralSupplyLotteryCalculator;
import com.portfolio.chungyak.service.AnnouncementQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * 찜한 공고 비교(/announcements/compare) — 스크랩은 localStorage 에만 있으니 서버는
 * 넘어온 id 들만 조회한다. 이상한 입력(빈 값·문자·중복·너무 많음)에도 400 을 내지 않고
 * 걸러내기만 하는지가 핵심이라 그 파싱 경계를 검증한다.
 */
class AnnouncementControllerTest {

    private AnnouncementQueryService queryService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        queryService = mock(AnnouncementQueryService.class);
        DocumentQaService qaService = mock(DocumentQaService.class);
        GeneralSupplyLotteryCalculator lotteryCalculator = mock(GeneralSupplyLotteryCalculator.class);
        when(queryService.today()).thenReturn(LocalDate.of(2026, 9, 18));
        when(queryService.statusOf(any())).thenReturn("접수중");

        mockMvc = MockMvcBuilders.standaloneSetup(
                new AnnouncementController(queryService, qaService, lotteryCalculator)).build();
    }

    private static Announcement announcement(long id) {
        return Announcement.builder()
                .id(id).externalId("A" + id).houseManageNo("1").pblancNo("1")
                .houseName("공고 " + id)
                .houseType(HouseType.APT).houseDetailType(HouseDetailType.PRIVATE)
                .build();
    }

    @Test
    @DisplayName("ids 가 없으면 조회 없이 빈 목록을 보여준다")
    void noIdsShowsEmptyList() throws Exception {
        mockMvc.perform(get("/announcements/compare"))
                .andExpect(status().isOk())
                .andExpect(view().name("announcements/compare"))
                .andExpect(model().attribute("requestedCount", 0));

        verify(queryService, org.mockito.Mockito.never()).findDetail(anyLong());
    }

    @Test
    @DisplayName("정상 id 들을 콤마로 넘기면 각각 조회해서 순서대로 보여준다")
    void parsesCommaSeparatedIds() throws Exception {
        when(queryService.findDetail(1L)).thenReturn(Optional.of(announcement(1L)));
        when(queryService.findDetail(2L)).thenReturn(Optional.of(announcement(2L)));

        mockMvc.perform(get("/announcements/compare").param("ids", "1,2"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("requestedCount", 2));

        verify(queryService).findDetail(1L);
        verify(queryService).findDetail(2L);
    }

    @Test
    @DisplayName("공백·문자·중복이 섞여 있어도 400 없이 걸러낸다")
    void skipsInvalidAndDuplicateIds() throws Exception {
        when(queryService.findDetail(1L)).thenReturn(Optional.of(announcement(1L)));

        mockMvc.perform(get("/announcements/compare").param("ids", " 1 , abc, 1,, 1"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("requestedCount", 1));

        verify(queryService).findDetail(1L);
    }

    @Test
    @DisplayName("존재하지 않는 id 는 조회는 하되 결과 목록에서 빠진다")
    void unknownIdIsSkippedFromRows() throws Exception {
        when(queryService.findDetail(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/announcements/compare").param("ids", "99"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("requestedCount", 1));

        verify(queryService).findDetail(99L);
    }

    @Test
    @DisplayName("6개를 넘겨도 앞의 6개만 조회한다")
    void limitsToSixIds() throws Exception {
        for (long i = 1; i <= 6; i++) {
            when(queryService.findDetail(i)).thenReturn(Optional.of(announcement(i)));
        }

        mockMvc.perform(get("/announcements/compare").param("ids", "1,2,3,4,5,6,7,8"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("requestedCount", 6));

        verify(queryService, org.mockito.Mockito.never()).findDetail(7L);
        verify(queryService, org.mockito.Mockito.never()).findDetail(8L);
    }
}
