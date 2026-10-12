package com.portfolio.chungyak.repository;

import com.portfolio.chungyak.domain.AlertSubscription;
import com.portfolio.chungyak.domain.AlertSubscription.Status;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AlertSubscriptionRepository extends JpaRepository<AlertSubscription, Long> {

    Optional<AlertSubscription> findByConfirmToken(String confirmToken);

    Optional<AlertSubscription> findByUnsubscribeToken(String unsubscribeToken);

    List<AlertSubscription> findAllByStatus(Status status);

    /** 확인하지 않은 채 오래된 신청 — 개인정보 처리방침의 "확인 전 신청은 7일 후 삭제" */
    long deleteByStatusAndCreatedAtBefore(Status status, java.time.Instant before);
}
