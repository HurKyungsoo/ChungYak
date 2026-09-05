package com.portfolio.chungyak.alert;

import com.portfolio.chungyak.config.AppProperties;
import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.domain.AlertSubscription;
import com.portfolio.chungyak.domain.AlertSubscription.Status;
import com.portfolio.chungyak.domain.SpecialSupplyType;
import com.portfolio.chungyak.repository.AlertSubscriptionRepository;
import com.portfolio.chungyak.rule.ApplicantProfile;
import com.portfolio.chungyak.rule.EligibilityEngine;
import com.portfolio.chungyak.rule.EligibilityEngine.MatchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * "새 공고 알림"(C2) — 매일 수집 배치가 끝난 뒤, <b>이번에 새로 생긴 공고만</b>
 * 구독자 조건으로 다시 판정해서 실제 자격이 되면 메일을 보낸다.
 *
 * ★ 절대 규칙 경계: 판정은 여기서도 {@link EligibilityEngine} 만 쓴다. 이 서비스는
 * 판정 결과를 나열할 뿐 새로 만들어내지 않고, 메일 문구도 결정론적으로 조립한다 —
 * LLM 은 이 배치 어디에도 관여하지 않는다.
 *
 * "새 공고"만 검사하는 이유: 매일 전체 2,800여 건을 모든 구독자 조건으로 다시 판정하면
 * 비용이 구독자 수 × 전체 공고 수로 늘어난다. 반면 하루에 새로 올라오는 공고는 수십 건
 * 수준이라 "새 공고 알림"이라는 이름 그대로 신규 건만 보면 충분하고 훨씬 저렴하다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NewAnnouncementAlertService {

    private final AlertSubscriptionRepository subscriptionRepository;
    private final EligibilityEngine eligibilityEngine;
    private final AlertMailer mailer;
    private final AppProperties appProperties;

    public void notifyMatchingSubscribers(List<Announcement> newAnnouncements) {
        if (newAnnouncements.isEmpty()) {
            return;
        }
        List<AlertSubscription> subscriptions = subscriptionRepository.findAllByStatus(Status.CONFIRMED);
        if (subscriptions.isEmpty()) {
            return;
        }

        log.info("새 공고 알림 매칭 시작 — 신규 공고 {}건 × 확인된 구독 {}건",
                newAnnouncements.size(), subscriptions.size());

        for (AlertSubscription subscription : subscriptions) {
            List<Match> matches = matchesFor(subscription, newAnnouncements);
            if (!matches.isEmpty()) {
                sendDigest(subscription, matches);
            }
        }
    }

    private List<Match> matchesFor(AlertSubscription subscription, List<Announcement> announcements) {
        ApplicantProfile profile = toProfile(subscription);
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
        String subject = "[청약나침반] 자격 되는 새 공고 " + matches.size() + "건이 떴습니다";

        StringBuilder body = new StringBuilder();
        body.append("저장해 두신 조건으로 신청 가능한 새 공고를 찾았습니다 (규칙 기준 자동 판정, AI 추측 아님).\n\n");
        for (Match match : matches) {
            body.append("· ").append(match.announcement().getHouseName())
                    .append(" — ").append(String.join(", ", match.typeLabels())).append('\n')
                    .append("  ").append(appProperties.baseUrl())
                    .append("/announcements/").append(match.announcement().getId()).append('\n');
        }
        body.append("\n정확한 신청 자격은 각 공고 페이지에서 다시 한번 확인해 주세요.\n\n")
                .append("이 알림을 그만 받으려면: ")
                .append(appProperties.baseUrl()).append("/alerts/unsubscribe?token=")
                .append(subscription.getUnsubscribeToken());

        mailer.send(subscription.getEmail(), subject, body.toString());
        log.info("새 공고 알림 발송 — {}건, 구독자 도메인 {}", matches.size(), emailDomain(subscription.getEmail()));
    }

    /** {@code EligibilityForm.toProfile()} 과 같은 매핑 — 저장해 둔 조건을 규칙 엔진 입력으로. */
    private ApplicantProfile toProfile(AlertSubscription s) {
        return ApplicantProfile.builder()
                .married(s.isMarried())
                .monthsSinceMarriage(s.getMonthsSinceMarriage())
                .childCount(Math.max(0, s.getChildCount()))
                .hasNewborn(s.isHasNewborn())
                .hasChildUnderSix(s.isHasChildUnderSix())
                .houseless(s.isHouseless())
                .accountMonths(s.getAccountMonths())
                .accountPaymentCount(s.getAccountPaymentCount())
                .accountDeposit(s.getAccountDeposit())
                .everOwnedHouse(s.isEverOwnedHouse())
                .supportingOldParents(s.isSupportingOldParents())
                .householdHead(s.isHouseholdHead())
                .residenceMonthsInRegion(s.getResidenceMonthsInRegion())
                .monthlyHouseholdIncome(s.getMonthlyHouseholdIncome())
                .householdSize(s.getHouseholdSize())
                .dualIncome(s.isDualIncome())
                .totalAssets(s.getTotalAssets())
                .carValue(s.getCarValue())
                .everWonSpecialSupply(s.isEverWonSpecialSupply())
                .monthsSinceLastWin(s.getMonthsSinceLastWin())
                .pastWinInSpeculationArea(s.isPastWinInSpeculationArea())
                .build();
    }

    /** 로그에 이메일 전체를 남기지 않는다 — 도메인만. */
    private static String emailDomain(String email) {
        int at = email == null ? -1 : email.indexOf('@');
        return at >= 0 ? email.substring(at) : "(알수없음)";
    }

    private record Match(Announcement announcement, List<String> typeLabels) {}
}
