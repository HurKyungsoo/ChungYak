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
import com.portfolio.chungyak.rule.EligibilityEngine;
import com.portfolio.chungyak.rule.RuleTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
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
 * 새 공고 알림 매칭·발송 — 실제 {@link EligibilityEngine}(RuleTestSupport) 으로 판정하고,
 * 메일 발송(AlertMailer)만 목으로 검증한다. 저장된 조건을 재산정보까지 실제로 판정에
 * 쓰는지가 이 서비스의 핵심이라 규칙 엔진은 진짜를 쓴다.
 */
class NewAnnouncementAlertServiceTest {

    private AlertSubscriptionRepository repository;
    private AlertMailer mailer;
    private NewAnnouncementAlertService service;

    @BeforeEach
    void setUp() {
        repository = mock(AlertSubscriptionRepository.class);
        mailer = mock(AlertMailer.class);
        EligibilityEngine engine = new EligibilityEngine(RuleTestSupport.allRules());
        AppProperties appProperties = new AppProperties("http://localhost:8080");
        service = new NewAnnouncementAlertService(repository, engine, mailer, appProperties);
    }

    private static Announcement announcement(long id, int newlywedAllocation) {
        Announcement a = Announcement.builder()
                .id(id)
                .externalId("A" + id).houseManageNo("1").pblancNo("1")
                .houseName("테스트 공고 " + id)
                .houseType(HouseType.APT).houseDetailType(HouseDetailType.PRIVATE)
                .regionName("서울")
                .build();
        a.addUnitType(UnitType.builder()
                .modelNo("01").typeName("084A")
                .supplyBreakdown(SupplyBreakdown.builder().newlywed(newlywedAllocation).build())
                .build());
        return a;
    }

    /** 신혼부부 자격을 충족하는 구독 조건(RuleTestSupport.passingIncomeAndAssets 기준). */
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
    @DisplayName("새 공고가 없으면 구독 조회도 안 하고 아무것도 안 보낸다")
    void noNewAnnouncementsSkipsEntirely() {
        service.notifyMatchingSubscribers(List.of());

        verify(repository, never()).findAllByStatus(any());
        verify(mailer, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("확인된 구독자가 새 공고에 실제로 자격이 되면 메일을 보낸다")
    void sendsMailWhenSubscriberQualifies() {
        when(repository.findAllByStatus(Status.CONFIRMED))
                .thenReturn(List.of(confirmedNewlywedSubscriber().build()));
        Announcement newAnnouncement = announcement(1L, 47);   // 신혼부부 47세대 배정

        service.notifyMatchingSubscribers(List.of(newAnnouncement));

        verify(mailer).send(eq("user@example.com"), anyString(), anyString());
    }

    @Test
    @DisplayName("자격이 안 되면(무주택 아님) 메일을 보내지 않는다")
    void doesNotSendWhenNotEligible() {
        AlertSubscription notHouseless = confirmedNewlywedSubscriber().houseless(false).build();
        when(repository.findAllByStatus(Status.CONFIRMED)).thenReturn(List.of(notHouseless));

        service.notifyMatchingSubscribers(List.of(announcement(1L, 47)));

        verify(mailer, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("자격은 되지만 그 공고에 물량이 없으면 메일을 보내지 않는다")
    void doesNotSendWhenNoAllocation() {
        when(repository.findAllByStatus(Status.CONFIRMED))
                .thenReturn(List.of(confirmedNewlywedSubscriber().build()));

        service.notifyMatchingSubscribers(List.of(announcement(1L, 0)));   // 신혼부부 물량 0

        verify(mailer, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("메일 본문에 공고명과 링크, 해지 링크가 들어간다")
    void mailBodyContainsAnnouncementAndUnsubscribeLink() {
        AlertSubscription subscriber = confirmedNewlywedSubscriber().unsubscribeToken("un-token-123").build();
        when(repository.findAllByStatus(Status.CONFIRMED)).thenReturn(List.of(subscriber));

        service.notifyMatchingSubscribers(List.of(announcement(42L, 5)));

        var bodyCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(mailer).send(eq("user@example.com"), anyString(), bodyCaptor.capture());
        assertThat(bodyCaptor.getValue())
                .contains("테스트 공고 42")
                .contains("/announcements/42")
                .contains("un-token-123");
    }
}
