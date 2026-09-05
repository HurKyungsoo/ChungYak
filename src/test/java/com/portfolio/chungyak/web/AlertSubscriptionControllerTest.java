package com.portfolio.chungyak.web;

import com.portfolio.chungyak.alert.AlertMailer;
import com.portfolio.chungyak.config.AppProperties;
import com.portfolio.chungyak.domain.AlertSubscription;
import com.portfolio.chungyak.domain.AlertSubscription.Status;
import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.domain.HouseDetailType;
import com.portfolio.chungyak.domain.HouseType;
import com.portfolio.chungyak.repository.AlertSubscriptionRepository;
import com.portfolio.chungyak.service.AnnouncementQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 새 공고 알림 구독 — 이메일 확인 전엔 PENDING(배치 대상 아님), 해지는 즉시 삭제.
 */
class AlertSubscriptionControllerTest {

    private AlertSubscriptionRepository repository;
    private AlertMailer mailer;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        repository = mock(AlertSubscriptionRepository.class);
        AnnouncementQueryService queryService = mock(AnnouncementQueryService.class);
        mailer = mock(AlertMailer.class);
        AppProperties appProperties = new AppProperties("http://localhost:8080");
        Clock clock = Clock.fixed(Instant.parse("2026-09-05T00:00:00Z"), ZoneOffset.UTC);

        Announcement a = Announcement.builder()
                .id(1L).externalId("T").houseManageNo("1").pblancNo("1").houseName("테스트")
                .houseType(HouseType.APT).houseDetailType(HouseDetailType.PRIVATE)
                .build();
        when(queryService.findDetail(1L)).thenReturn(Optional.of(a));

        when(repository.save(any(AlertSubscription.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc = MockMvcBuilders.standaloneSetup(
                new AlertSubscriptionController(repository, queryService, mailer, appProperties, clock)).build();
    }

    @Test
    @DisplayName("이메일이 올바르면 PENDING 구독을 저장하고 확인 메일을 보낸 뒤 결과 화면으로 돌아간다")
    void subscribeSavesPendingAndSendsConfirmMail() throws Exception {
        mockMvc.perform(post("/announcements/1/eligibility/alerts/subscribe")
                        .param("email", "user@example.com")
                        .param("resultToken", "tok-123")
                        .param("married", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/announcements/1/eligibility/result/tok-123"));

        ArgumentCaptor<AlertSubscription> captor = ArgumentCaptor.forClass(AlertSubscription.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("user@example.com");
        assertThat(captor.getValue().getStatus()).isEqualTo(Status.PENDING);
        assertThat(captor.getValue().isMarried()).isTrue();

        verify(mailer).send(eq("user@example.com"), anyString(), contains("/alerts/confirm?token="));
    }

    @Test
    @DisplayName("resultToken 이 없으면 폼 화면으로 돌아간다")
    void subscribeWithoutResultTokenRedirectsToForm() throws Exception {
        mockMvc.perform(post("/announcements/1/eligibility/alerts/subscribe")
                        .param("email", "user@example.com"))
                .andExpect(redirectedUrl("/announcements/1/eligibility"));
    }

    @Test
    @DisplayName("이메일 형식이 잘못되면 저장하지 않고 되돌려보낸다")
    void invalidEmailIsRejected() throws Exception {
        mockMvc.perform(post("/announcements/1/eligibility/alerts/subscribe")
                        .param("email", "not-an-email"))
                .andExpect(status().is3xxRedirection());

        verify(repository, never()).save(any());
        verify(mailer, never()).send(any(), any(), any());
    }

    @Test
    @DisplayName("유효한 토큰으로 확인하면 CONFIRMED 로 바뀐다")
    void confirmActivatesSubscription() throws Exception {
        AlertSubscription pending = AlertSubscription.builder()
                .email("user@example.com").referenceAnnouncementId(1L)
                .confirmToken("ctok").unsubscribeToken("utok")
                .createdAt(Instant.now()).build();
        when(repository.findByConfirmToken("ctok")).thenReturn(Optional.of(pending));
        when(repository.save(any(AlertSubscription.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(get("/alerts/confirm").param("token", "ctok"))
                .andExpect(status().isOk())
                .andExpect(view().name("alerts/notice"))
                .andExpect(model().attribute("success", true));

        assertThat(pending.isConfirmed()).isTrue();
        verify(repository).save(pending);
    }

    @Test
    @DisplayName("존재하지 않는 확인 토큰은 실패 안내를 보여준다")
    void confirmWithUnknownTokenShowsFailure() throws Exception {
        when(repository.findByConfirmToken("nope")).thenReturn(Optional.empty());

        mockMvc.perform(get("/alerts/confirm").param("token", "nope"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("success", false));
    }

    @Test
    @DisplayName("해지 토큰으로 요청하면 구독을 완전히 삭제한다")
    void unsubscribeDeletesSubscription() throws Exception {
        AlertSubscription confirmed = AlertSubscription.builder()
                .email("user@example.com").referenceAnnouncementId(1L)
                .confirmToken("ctok").unsubscribeToken("utok")
                .status(Status.CONFIRMED).createdAt(Instant.now()).build();
        when(repository.findByUnsubscribeToken("utok")).thenReturn(Optional.of(confirmed));

        mockMvc.perform(get("/alerts/unsubscribe").param("token", "utok"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("success", true));

        verify(repository).delete(confirmed);
    }
}
