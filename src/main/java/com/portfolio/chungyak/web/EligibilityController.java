package com.portfolio.chungyak.web;

import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.llm.ExplanationService;
import com.portfolio.chungyak.llm.ProfileExtractionResult;
import com.portfolio.chungyak.llm.ProfileExtractionService;
import com.portfolio.chungyak.rule.ApplicantProfile;
import com.portfolio.chungyak.rule.EligibilityEngine;
import com.portfolio.chungyak.rule.EligibilityEngine.MatchResult;
import com.portfolio.chungyak.rule.IncomeReference;
import com.portfolio.chungyak.service.AnnouncementQueryService;
import com.portfolio.chungyak.web.form.EligibilityForm;
import com.portfolio.chungyak.web.view.EligibilityResultAssembler;
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

/**
 * 자격 판정 화면 — 이 프로젝트의 핵심.
 *
 * 컨트롤러가 하는 일:
 *   1) 폼 -> ApplicantProfile (EligibilityForm.toProfile, 결정론적 매핑)
 *   2) EligibilityEngine.evaluate 호출
 *   3) 결과를 화면 모델로 재배열 (EligibilityResultAssembler)
 * 판정은 전부 2번의 rule 패키지 안에서 끝난다. 여기서 자격을 따지지 않는다.
 *
 * 자연어 입력(/extract)은 LLM 으로 폼을 <b>채워주기만</b> 한다.
 * 채운 값은 사용자가 확인·수정한 뒤 위 1~3 플로우로 그대로 들어간다.
 *
 * 판정 결과 화면에는 LLM 요약(ExplanationService)이 얹힌다 — 이미 확정된 근거를
 * 문장으로 재구성한 것이고, 모순 검사를 통과하지 못하면 결정론적 요약으로 대체된다.
 *
 * LLM 은 앞(추출)·뒤(요약) 어느 쪽에서도 판정에 관여하지 않는다(CLAUDE.md 절대 규칙).
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class EligibilityController {

    private final AnnouncementQueryService queryService;
    private final EligibilityEngine eligibilityEngine;
    private final EligibilityResultAssembler resultAssembler;
    private final ProfileExtractionService profileExtractionService;
    private final ExplanationService explanationService;
    private final IncomeReference incomeReference;

    @GetMapping("/announcements/{id}/eligibility")
    public String form(@PathVariable Long id, Model model) {
        Announcement announcement = loadAnnouncement(id);
        model.addAttribute("announcement", announcement);
        model.addAttribute("status", queryService.statusOf(announcement));
        model.addAttribute("extractionAvailable", profileExtractionService.isAvailable());
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new EligibilityForm());
        }
        return "announcements/eligibility-form";
    }

    /**
     * 자연어 문장에서 폼 값을 뽑아 채운 뒤 같은 폼 화면을 다시 보여준다.
     * 판정은 하지 않는다 — 사용자가 값을 확인하고 "판정하기"를 눌러야 evaluate 로 간다.
     */
    @PostMapping("/announcements/{id}/eligibility/extract")
    public String extract(@PathVariable Long id,
                          @RequestParam("naturalText") String naturalText,
                          Model model) {
        Announcement announcement = loadAnnouncement(id);
        ProfileExtractionResult result = profileExtractionService.extract(naturalText);

        EligibilityForm form = new EligibilityForm();
        if (result.isExtracted()) {
            form.applyExtracted(result.profile());
        }

        log.info("자연어 폼 채우기 — 공고 #{}, 상태={}, 미확인 필드 {}개",
                announcement.getId(), result.status(), result.unknownFieldLabels().size());

        model.addAttribute("announcement", announcement);
        model.addAttribute("status", queryService.statusOf(announcement));
        model.addAttribute("extractionAvailable", profileExtractionService.isAvailable());
        model.addAttribute("form", form);
        model.addAttribute("extraction", result);
        model.addAttribute("naturalText", naturalText);
        return "announcements/eligibility-form";
    }

    /**
     * @param explain true 면 판정 근거를 LLM 요약까지 한다. 기본은 false —
     *   요약은 결과 화면의 "AI 요약 보기" 버튼(같은 폼 재제출)으로 온디맨드 호출한다.
     *   매 판정마다 LLM 을 태우지 않기 위한 것. (판정 자체는 explain 값과 무관하게 결정론)
     */
    @PostMapping("/announcements/{id}/eligibility")
    public String evaluate(@PathVariable Long id,
                           @ModelAttribute("form") EligibilityForm form,
                           @RequestParam(name = "explain", defaultValue = "false") boolean explain,
                           Model model) {
        Announcement announcement = loadAnnouncement(id);
        ApplicantProfile profile = form.toProfile();

        log.info("자격 판정 실행 — 공고 #{} '{}' (주택형 {}개, 규제지역={}), "
                        + "입력: 혼인={}({}개월) 자녀={} 신생아={} 무주택={} 통장={}개월 "
                        + "생애최초이력={} 노부모부양={} 세대주={}",
                announcement.getId(), announcement.getHouseName(),
                announcement.getUnitTypes().size(),
                announcement.getRegulationFlags() != null
                        && announcement.getRegulationFlags().isRegulatedArea(),
                profile.isMarried(), profile.getMonthsSinceMarriage(), profile.getChildCount(),
                profile.isHasNewborn(), profile.isHouseless(), profile.getAccountMonths(),
                profile.isEverOwnedHouse(), profile.isSupportingOldParents(), profile.isHouseholdHead());

        MatchResult result = eligibilityEngine.evaluate(profile, announcement);

        log.info("자격 판정 결과 — 공고 #{}: 신청가능 주택형 {}개, 자격되지만 물량없음 {}종",
                announcement.getId(), result.matches().size(),
                result.qualifiedButUnavailable().size());

        // 뒷단 LLM: 확정된 판정 근거를 문장으로 재구성만 한다 (판정에는 관여하지 않음).
        // "AI 요약 보기" 를 눌렀을 때만 호출 — 매 판정마다 태우지 않는다.
        if (explain) {
            var explanation = explanationService.explain(result);
            log.info("판정 요약 — 공고 #{}: {}", announcement.getId(), explanation.status());
            model.addAttribute("explanation", explanation);
        }

        model.addAttribute("announcement", announcement);
        model.addAttribute("status", queryService.statusOf(announcement));
        model.addAttribute("result", resultAssembler.assemble(result));
        model.addAttribute("form", form);   // "AI 요약 보기" 버튼이 폼을 그대로 재제출할 수 있도록
        model.addAttribute("explanationAvailable", explanationService.isAvailable());
        model.addAttribute("incomePercent", incomeReference.percentOf(
                profile.getMonthlyHouseholdIncome(), profile.getHouseholdSize()));
        model.addAttribute("incomeBasisYear", incomeReference.basisYear());
        return "announcements/eligibility-result";
    }

    private Announcement loadAnnouncement(Long id) {
        return queryService.findDetail(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "공고를 찾을 수 없습니다."));
    }
}
