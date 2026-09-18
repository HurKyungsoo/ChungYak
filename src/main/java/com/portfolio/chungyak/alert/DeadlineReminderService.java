package com.portfolio.chungyak.alert;

import com.portfolio.chungyak.config.AppProperties;
import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.domain.AlertSubscription;
import com.portfolio.chungyak.domain.AlertSubscription.Status;
import com.portfolio.chungyak.domain.SpecialSupplyType;
import com.portfolio.chungyak.repository.AlertSubscriptionRepository;
import com.portfolio.chungyak.repository.AnnouncementRepository;
import com.portfolio.chungyak.rule.ApplicantProfile;
import com.portfolio.chungyak.rule.EligibilityEngine;
import com.portfolio.chungyak.rule.EligibilityEngine.MatchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * "마감 임박 알림" — 접수 마감이 {@link DeadlineReminderProperties#daysBefore()}일 남은 공고를
 * 구독자 조건으로 다시 판정해서, 실제 신청 가능한데 놓치기 쉬운 공고를 한 번 더 알려준다.
 *
 * {@link com.portfolio.chungyak.web.view.Dday} 배지는 화면에만 있고 알림은 "새 공고"(C2)뿐이라,
 * 이미 알고 있던 공고라도 마감이 임박했다는 사실 자체를 놓칠 수 있었다.
 *
 * ★ 절대 규칙 경계: {@link NewAnnouncementAlertService} 와 같은 패턴 — 판정은 {@link
 * EligibilityEngine} 만 쓰고, 이 서비스는 그 결과를 나열할 뿐 새로 만들어내지 않는다.
 * LLM 은 이 배치에 관여하지 않는다.
 *
 * 매일 정확히 "마감 D-{daysBefore}"인 날에만 대상이 되므로 같은 공고로 중복 발송되지 않는다
 * (배치가 하루를 건너뛰면 그날의 알림만 조용히 빠진다 — C2 새 공고 알림과 같은 수준의
 * 단순화다. 재시도·이력 테이블은 이 규모에서 과잉이라 두지 않는다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeadlineReminderService {

    private final AnnouncementRepository announcementRepository;
    private final AlertSubscriptionRepository subscriptionRepository;
    private final EligibilityEngine eligibilityEngine;
    private final AlertMailer mailer;
    private final AppProperties appProperties;
    private final DeadlineReminderProperties properties;
    private final Clock clock;

    @Transactional(readOnly = true)
    public void remindClosingSoon() {
        LocalDate target = LocalDate.now(clock).plusDays(properties.daysBefore());
        List<Announcement> closingSoon = announcementRepository.findByReceptEndDateWithUnitTypes(target);
        if (closingSoon.isEmpty()) {
            return;
        }

        List<AlertSubscription> subscriptions = subscriptionRepository.findAllByStatus(Status.CONFIRMED);
        if (subscriptions.isEmpty()) {
            return;
        }

        log.info("마감 임박 알림 매칭 시작 — D-{} 마감 공고 {}건 × 확인된 구독 {}건",
                properties.daysBefore(), closingSoon.size(), subscriptions.size());

        for (AlertSubscription subscription : subscriptions) {
            List<Match> matches = matchesFor(subscription, closingSoon);
            if (!matches.isEmpty()) {
                sendDigest(subscription, matches);
            }
        }
    }

    private List<Match> matchesFor(AlertSubscription subscription, List<Announcement> announcements) {
        ApplicantProfile profile = AlertSubscriptionProfiles.toProfile(subscription);
        List<Match> matches = new ArrayList<>();
        for (Announcement announcement : announcements) {
            MatchResult result = eligibilityEngine.evaluate(profile, announcement);
            if (!result.hasAnyMatch()) {
                continue;
            }
            List<String> typeLabels = result.matches().stream()
                    .flatMap(m -> m.applicableTypes().stream())
                    .map(SpecialSupplyType::getLabel)
                    .distinct()
                    .toList();
            matches.add(new Match(announcement, typeLabels));
        }
        return matches;
    }

    private void sendDigest(AlertSubscription subscription, List<Match> matches) {
        int daysBefore = properties.daysBefore();
        String subject = "[청약나침반] 마감 D-" + daysBefore + " 임박 — 자격 되는 공고 " + matches.size() + "건";

        StringBuilder body = new StringBuilder();
        body.append("저장해 두신 조건으로 신청 가능한 공고의 접수 마감이 ")
                .append(daysBefore).append("일 남았습니다 (규칙 기준 자동 판정, AI 추측 아님).\n\n");
        for (Match match : matches) {
            body.append("· ").append(match.announcement().getHouseName())
                    .append(" — ").append(String.join(", ", match.typeLabels()))
                    .append(" (마감 ").append(match.announcement().getReceptEndDate()).append(")\n")
                    .append("  ").append(appProperties.baseUrl())
                    .append("/announcements/").append(match.announcement().getId()).append('\n');
        }
        body.append("\n정확한 신청 자격은 각 공고 페이지에서 다시 한번 확인해 주세요.\n\n")
                .append("이 알림을 그만 받으려면: ")
                .append(appProperties.baseUrl()).append("/alerts/unsubscribe?token=")
                .append(subscription.getUnsubscribeToken());

        mailer.send(subscription.getEmail(), subject, body.toString());
        log.info("마감 임박 알림 발송 — {}건, 구독자 도메인 {}", matches.size(), emailDomain(subscription.getEmail()));
    }

    /** 로그에 이메일 전체를 남기지 않는다 — 도메인만. */
    private static String emailDomain(String email) {
        int at = email == null ? -1 : email.indexOf('@');
        return at >= 0 ? email.substring(at) : "(알수없음)";
    }

    private record Match(Announcement announcement, List<String> typeLabels) {}
}
