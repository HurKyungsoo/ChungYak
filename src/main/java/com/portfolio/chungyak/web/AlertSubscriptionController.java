package com.portfolio.chungyak.web;

import com.portfolio.chungyak.alert.AlertMailer;
import com.portfolio.chungyak.config.AppProperties;
import com.portfolio.chungyak.domain.AlertSubscription;
import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.repository.AlertSubscriptionRepository;
import com.portfolio.chungyak.service.AnnouncementQueryService;
import com.portfolio.chungyak.web.form.EligibilityForm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Clock;
import java.util.regex.Pattern;

/**
 * "새 공고 알림"(C2) 구독 신청·확인·해지.
 *
 * 판정 폼에서 이미 입력한 조건(email 만 추가)을 그대로 저장한다. 실제 판정·발송은
 * {@link com.portfolio.chungyak.alert.NewAnnouncementAlertService} 가 배치로 한다 —
 * 여기서는 자격을 따지지 않는다(요청만 받고 저장만 한다).
 *
 * 이메일 확인 전엔 {@link AlertSubscription.Status#PENDING} — 배치가 건너뛴다.
 * 확인 링크를 눌러야 실제로 알림이 나간다(남의 이메일로 가입시키는 걸 막기 위함).
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class AlertSubscriptionController {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final AlertSubscriptionRepository subscriptionRepository;
    private final AnnouncementQueryService queryService;
    private final AlertMailer mailer;
    private final AppProperties appProperties;
    private final Clock clock;

    @PostMapping("/announcements/{id}/eligibility/alerts/subscribe")
    public String subscribe(@PathVariable Long id,
                            @ModelAttribute("form") EligibilityForm form,
                            @RequestParam("email") String email,
                            @RequestParam(name = "resultToken", required = false) String resultToken,
                            RedirectAttributes redirectAttributes) {
        Announcement announcement = queryService.findDetail(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "공고를 찾을 수 없습니다."));
        String backTo = resultToken != null && !resultToken.isBlank()
                ? "redirect:/announcements/" + id + "/eligibility/result/" + resultToken
                : "redirect:/announcements/" + id + "/eligibility";

        String trimmedEmail = email == null ? "" : email.strip();
        if (!EMAIL_PATTERN.matcher(trimmedEmail).matches()) {
            redirectAttributes.addFlashAttribute("alertMessage", "이메일 형식이 올바르지 않습니다.");
            redirectAttributes.addFlashAttribute("alertMessageBad", true);
            return backTo;
        }

        AlertSubscription subscription = AlertSubscription.builder()
                .email(trimmedEmail)
                .referenceAnnouncementId(announcement.getId())
                .confirmToken(AlertSubscription.newToken())
                .unsubscribeToken(AlertSubscription.newToken())
                .createdAt(clock.instant())
                .married(form.isMarried())
                .monthsSinceMarriage(form.getMonthsSinceMarriage())
                .childCount(form.getChildCount())
                .hasNewborn(form.isHasNewborn())
                .hasChildUnderSix(form.isHasChildUnderSix())
                .houseless(form.isHouseless())
                .accountMonths(form.getAccountMonths())
                .accountPaymentCount(form.getAccountPaymentCount())
                .accountDeposit(form.getAccountDeposit())
                .everOwnedHouse(form.isEverOwnedHouse())
                .supportingOldParents(form.isSupportingOldParents())
                .householdHead(form.isHouseholdHead())
                .residenceMonthsInRegion(form.getResidenceMonthsInRegion())
                .monthlyHouseholdIncome(form.getMonthlyHouseholdIncome())
                .householdSize(form.getHouseholdSize())
                .dualIncome(form.isDualIncome())
                .totalAssets(form.getTotalAssets())
                .carValue(form.getCarValue())
                .everWonSpecialSupply(form.isEverWonSpecialSupply())
                .monthsSinceLastWin(form.getMonthsSinceLastWin())
                .pastWinInSpeculationArea(form.isPastWinInSpeculationArea())
                .build();
        subscription = subscriptionRepository.save(subscription);

        sendConfirmationMail(subscription);
        log.info("새 공고 알림 구독 신청 — 공고 #{}, 확인 대기", announcement.getId());

        redirectAttributes.addFlashAttribute("alertMessage",
                "확인 메일을 보냈습니다. 메일함에서 링크를 눌러야 알림이 시작됩니다.");
        redirectAttributes.addFlashAttribute("alertMessageBad", false);
        return backTo;
    }

    @GetMapping("/alerts/confirm")
    public String confirm(@RequestParam("token") String token, Model model) {
        var found = subscriptionRepository.findByConfirmToken(token);
        if (found.isEmpty()) {
            model.addAttribute("success", false);
            model.addAttribute("title", "확인할 수 없는 링크입니다");
            model.addAttribute("message", "이미 확인됐거나 만료된 링크입니다.");
            return "alerts/notice";
        }
        AlertSubscription subscription = found.get();
        subscription.confirm();
        subscriptionRepository.save(subscription);

        model.addAttribute("success", true);
        model.addAttribute("title", "알림 신청이 확인됐습니다");
        model.addAttribute("message", "저장하신 조건에 맞는 새 공고가 뜨면 이메일로 알려드립니다.");
        return "alerts/notice";
    }

    @GetMapping("/alerts/unsubscribe")
    public String unsubscribe(@RequestParam("token") String token, Model model) {
        var found = subscriptionRepository.findByUnsubscribeToken(token);
        if (found.isPresent()) {
            subscriptionRepository.delete(found.get());   // 저장된 조건(소득·자산 포함) 자체를 지운다
        }

        model.addAttribute("success", true);
        model.addAttribute("title", "구독이 해지됐습니다");
        model.addAttribute("message", "저장돼 있던 조건도 함께 삭제했습니다. 더 이상 알림이 가지 않습니다.");
        return "alerts/notice";
    }

    private void sendConfirmationMail(AlertSubscription subscription) {
        String confirmUrl = appProperties.baseUrl() + "/alerts/confirm?token=" + subscription.getConfirmToken();
        String body = """
                청약나침반 새 공고 알림 신청이 접수됐습니다.

                아래 링크를 눌러야 알림이 시작됩니다 (본인 확인 목적):
                %s

                본인이 신청한 게 아니라면 이 메일을 무시하세요 — 아무 일도 일어나지 않습니다.
                """.formatted(confirmUrl);
        mailer.send(subscription.getEmail(), "[청약나침반] 새 공고 알림 확인", body);
    }
}
