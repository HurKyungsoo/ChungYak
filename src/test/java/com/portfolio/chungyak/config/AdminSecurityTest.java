package com.portfolio.chungyak.config;

import com.anthropic.client.AnthropicClient;
import com.portfolio.chungyak.llm.GroqChatClient;
import com.portfolio.chungyak.rag.embedding.EmbeddingClient;
import com.portfolio.chungyak.service.AnnouncementSyncScheduler;
import com.portfolio.chungyak.service.AnnouncementSyncScheduler.SyncReport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * /api/admin/** 가 ROLE_ADMIN 뒤에 있고, 공개 화면은 그대로 열려 있는지 검증.
 * (전체 컨텍스트 로딩 겸용 — 앱 배선이 깨지면 여기서 먼저 걸린다.)
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:sec-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "security.admin.username=admin",
        "security.admin.password=test-secret"
})
@TestPropertySource(locations = "classpath:ai-off.properties")
@AutoConfigureMockMvc
class AdminSecurityTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ApplicationContext context;

    @MockBean
    private AnnouncementSyncScheduler scheduler;

    @Test
    @DisplayName("인증 없이 /api/admin/sync → 401")
    void adminApiRequiresAuth() throws Exception {
        mvc.perform(post("/api/admin/sync"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("비밀번호 틀리면 → 401")
    void adminApiRejectsWrongPassword() throws Exception {
        mvc.perform(post("/api/admin/sync").with(httpBasic("admin", "nope")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("올바른 admin 자격이면 → 200, SyncReport 반환")
    void adminApiAllowsCorrectCredentials() throws Exception {
        when(scheduler.runSync()).thenReturn(new SyncReport(1, 10, 10, 0));

        mvc.perform(post("/api/admin/sync").with(httpBasic("admin", "test-secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.received").value(10));
    }

    @Test
    @DisplayName("공개 화면(/announcements)은 인증 없이 200")
    void publicScreensStayOpen() throws Exception {
        mvc.perform(get("/announcements"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("/actuator/health 와 liveness 프로브는 인증 없이 200")
    void healthIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("/actuator/env 같은 다른 actuator 는 열려 있지 않다 (2xx 아님)")
    void otherActuatorBlocked() throws Exception {
        mvc.perform(get("/actuator/env"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("테스트 컨텍스트에는 외부 AI 클라이언트 빈이 없다 — 셸에 키가 있어도 (#23)")
    void aiClientsOffInTests() {
        assertThat(context.getBeanNamesForType(AnthropicClient.class)).isEmpty();
        assertThat(context.getBeanNamesForType(EmbeddingClient.class)).isEmpty();
        assertThat(context.getBeanNamesForType(GroqChatClient.class)).isEmpty();
    }
}
