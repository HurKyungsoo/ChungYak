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
}
