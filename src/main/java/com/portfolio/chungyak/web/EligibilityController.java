package com.portfolio.chungyak.web;

import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.rule.ApplicantProfile;
import com.portfolio.chungyak.rule.EligibilityEngine;
import com.portfolio.chungyak.rule.EligibilityEngine.MatchResult;
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
import org.springframework.web.server.ResponseStatusException;

/**
 * 자격 판정 화면 — 이 프로젝트의 핵심.
 *
 * 컨트롤러가 하는 일:
 *   1) 폼 -> ApplicantProfile (EligibilityForm.toProfile, 결정론적 매핑)
 *   2) EligibilityEngine.evaluate 호출
 *   3) 결과를 화면 모델로 재배열 (EligibilityResultAssembler)
 * 판정은 전부 2번의 rule 패키지 안에서 끝난다. 여기서 자격을 따지지 않는다.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class EligibilityController {

    private final AnnouncementQueryService queryService;
    private final EligibilityEngine eligibilityEngine;
    private final EligibilityResultAssembler resultAssembler;

    @GetMapping("/announcements/{id}/eligibility")
    public String form(@PathVariable Long id, Model model) {
        Announcement announcement = loadAnnouncement(id);
        model.addAttribute("announcement", announcement);
        model.addAttribute("status", queryService.statusOf(announcement));
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new EligibilityForm());
        }
        return "announcements/eligibility-form";
    }

    @PostMapping("/announcements/{id}/eligibility")
    public String evaluate(@PathVariable Long id,
                           @ModelAttribute("form") EligibilityForm form,
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

        model.addAttribute("announcement", announcement);
        model.addAttribute("status", queryService.statusOf(announcement));
        model.addAttribute("result", resultAssembler.assemble(result));
        return "announcements/eligibility-result";
    }

    private Announcement loadAnnouncement(Long id) {
        return queryService.findDetail(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "공고를 찾을 수 없습니다."));
    }
}
