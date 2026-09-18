package com.portfolio.chungyak.alert;

import com.portfolio.chungyak.config.AppProperties;
import com.portfolio.chungyak.domain.AlertSubscription;
import com.portfolio.chungyak.domain.AlertSubscription.Status;
import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.domain.HouseDetailType;
import com.portfolio.chungyak.domain.HouseType;
import com.portfolio.chungyak.domain.SupplyBreakdown;
import com.portfolio.chungyak.domain.UnitType;
import com.portfolio.chungyak.repository.AlertSubscriptionRepository;
import com.portfolio.chungyak.repository.AnnouncementRepository;
import com.portfolio.chungyak.rule.EligibilityEngine;
import com.portfolio.chungyak.rule.RuleTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 마감 임박 알림 매칭·발송 — {@link NewAnnouncementAlertServiceTest} 와 같은 검증 방식.
 * "정확히 D-{daysBefore}인 공고만 본다"가 이 서비스의 핵심이라 그 경계를 명시적으로 테스트한다.
 */
class DeadlineReminderServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            LocalDate.of(2026, 9, 18).atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
    private static final int DAYS_BEFORE = 3;

    private AnnouncementRepository announcementRepository;
    private AlertSubscriptionRepository subscriptionRepository;
    private AlertMailer mailer;
    private DeadlineReminderService service;

    @BeforeEach
    void setUp() {
        announcementRepository = mock(AnnouncementRepository.class);
        subscriptionRepository = mock(AlertSubscriptionRepository.class);
        mailer = mock(AlertMailer.class);
        EligibilityEngine engine = new EligibilityEngine(RuleTestSupport.allRules());
        AppProperties appProperties = new AppProperties("http://localhost:8080");
        DeadlineReminderProperties properties = new DeadlineReminderProperties(DAYS_BEFORE);
        service = new DeadlineReminderService(
                announcementRepository, subscriptionRepository, engine, mailer, appProperties, properties, CLOCK);
    }

    private static Announcement announcement(long id, int newlywedAllocation) {
        Announcement a = Announcement.builder()
                .id(id)
                .externalId("A" + id).houseManageNo("1").pblancNo("1")
                .houseName("테스트 공고 " + id)
                .houseType(HouseType.APT).houseDetailType(HouseDetailType.PRIVATE)
                .regionName("서울")
                .receptEndDate(LocalDate.now(CLOCK).plusDays(DAYS_BEFORE))
                .build();
        a.addUnitType(UnitType.builder()
                .modelNo("01").typeName("084A")
                .supplyBreakdown(SupplyBreakdown.builder().newlywed(newlywedAllocation).build())
                .build());
        return a;
    }

    private static AlertSubscription.AlertSubscriptionBuilder confirmedNewlywedSubscriber() {
        return AlertSubscription.builder()
                .email("user@example.com")
                .referenceAnnouncementId(1L)
                .status(Status.CONFIRMED)
                .confirmToken("c").unsubscribeToken("u")
                .createdAt(Instant.now())
                .married(true).monthsSinceMarriage(36)
                .houseless(true).accountMonths(12)
                .monthlyHouseholdIncome(5_000_000).householdSize(3)
                .totalAssets(200_000_000L).carValue(15_000_000)
                .accountDeposit(15_000_000).residenceMonthsInRegion(36);
    }

    @Test
    @DisplayName("정확히 D-N 인 날짜의 공고만 조회한다")
    void queriesExactTargetDate() {
        when(announcementRepository.findByReceptEndDateWithUnitTypes(LocalDate.now(CLOCK).plusDays(DAYS_BEFORE)))
                .thenReturn(List.of());

        service.remindClosingSoon();

        verify(announcementRepository).findByReceptEndDateWithUnitTypes(LocalDate.now(CLOCK).plusDays(DAYS_BEFORE));
    }

    @Test
    @DisplayName("마감 임박 공고가 없으면 구독 조회도 안 하고 아무것도 안 보낸다")
    void noClosingSoonAnnouncementsSkipsEntirely() {
        when(announcementRepository.findByReceptEndDateWithUnitTypes(any())).thenReturn(List.of());

        service.remindClosingSoon();

        verify(subscriptionRepository, never()).findAllByStatus(any());
        verify(mailer, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("확인된 구독자가 마감 임박 공고에 실제로 자격이 되면 메일을 보낸다")
    void sendsMailWhenSubscriberQualifies() {
        Announcement closingSoon = announcement(1L, 47);   // 신혼부부 47세대 배정
        when(announcementRepository.findByReceptEndDateWithUnitTypes(any())).thenReturn(List.of(closingSoon));
        when(subscriptionRepository.findAllByStatus(Status.CONFIRMED))
                .thenReturn(List.of(confirmedNewlywedSubscriber().build()));

        service.remindClosingSoon();

        verify(mailer).send(eq("user@example.com"), anyString(), anyString());
    }

    @Test
    @DisplayName("자격이 안 되면(무주택 아님) 메일을 보내지 않는다")
    void doesNotSendWhenNotEligible() {
        Announcement closingSoon = announcement(1L, 47);
        when(announcementRepository.findByReceptEndDateWithUnitTypes(any())).thenReturn(List.of(closingSoon));
        AlertSubscription notHouseless = confirmedNewlywedSubscriber().houseless(false).build();
        when(subscriptionRepository.findAllByStatus(Status.CONFIRMED)).thenReturn(List.of(notHouseless));

        service.remindClosingSoon();

        verify(mailer, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("자격은 되지만 그 공고에 물량이 없으면 메일을 보내지 않는다")
    void doesNotSendWhenNoAllocation() {
        Announcement closingSoon = announcement(1L, 0);   // 신혼부부 물량 0
        when(announcementRepository.findByReceptEndDateWithUnitTypes(any())).thenReturn(List.of(closingSoon));
        when(subscriptionRepository.findAllByStatus(Status.CONFIRMED))
                .thenReturn(List.of(confirmedNewlywedSubscriber().build()));

        service.remindClosingSoon();

        verify(mailer, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("메일 본문에 공고명·마감일·링크·해지 링크가 들어간다")
    void mailBodyContainsAnnouncementDeadlineAndLinks() {
        Announcement closingSoon = announcement(42L, 5);
        when(announcementRepository.findByReceptEndDateWithUnitTypes(any())).thenReturn(List.of(closingSoon));
        AlertSubscription subscriber = confirmedNewlywedSubscriber().unsubscribeToken("un-token-123").build();
        when(subscriptionRepository.findAllByStatus(Status.CONFIRMED)).thenReturn(List.of(subscriber));

        service.remindClosingSoon();

        var bodyCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        var subjectCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(mailer).send(eq("user@example.com"), subjectCaptor.capture(), bodyCaptor.capture());
        assertThat(subjectCaptor.getValue()).contains("D-3");
        assertThat(bodyCaptor.getValue())
                .contains("테스트 공고 42")
                .contains("/announcements/42")
                .contains(closingSoon.getReceptEndDate().toString())
                .contains("un-token-123");
    }
}
