package com.portfolio.chungyak.web;

import com.portfolio.chungyak.service.AnnouncementSyncScheduler;
import com.portfolio.chungyak.service.AnnouncementSyncScheduler.SyncAlreadyRunningException;
import com.portfolio.chungyak.service.AnnouncementSyncScheduler.SyncReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 관리자 동기화 API 검증.
 *
 * - 정상: 스케줄러의 SyncReport 를 그대로 반환 (pagesFetched/received/created/updated)
 * - 중복 실행: SyncAlreadyRunningException -> 409
 */
class AdminSyncControllerTest {

    private AnnouncementSyncScheduler scheduler;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        scheduler = Mockito.mock(AnnouncementSyncScheduler.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminSyncController(scheduler)).build();
    }

    @Test
    @DisplayName("POST /api/admin/sync — SyncReport 를 그대로 반환한다")
    void returnsSyncReport() throws Exception {
        when(scheduler.runSync()).thenReturn(new SyncReport(29, 2861, 2861, 0));

        mockMvc.perform(post("/api/admin/sync"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagesFetched").value(29))
                .andExpect(jsonPath("$.received").value(2861))
                .andExpect(jsonPath("$.created").value(2861))
                .andExpect(jsonPath("$.updated").value(0));
    }

    @Test
    @DisplayName("이미 동기화 중이면 409 를 반환한다")
    void conflictWhenAlreadyRunning() throws Exception {
        when(scheduler.runSync()).thenThrow(new SyncAlreadyRunningException());

        mockMvc.perform(post("/api/admin/sync"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("SYNC_ALREADY_RUNNING"));
    }
}
