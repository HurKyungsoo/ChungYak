package com.portfolio.chungyak.web;

import com.portfolio.chungyak.llm.ProfileExtractionResult;
import com.portfolio.chungyak.llm.ProfileExtractionService;
import com.portfolio.chungyak.rule.ApplicantProfile;
import com.portfolio.chungyak.service.AnnouncementMatchService;
import com.portfolio.chungyak.web.form.EligibilityForm;
import com.portfolio.chungyak.web.view.MatchBlockers;
import com.portfolio.chungyak.web.view.MatchRow;
import com.portfolio.chungyak.web.view.MatchRowFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * "내 조건으로 지금 지원 가능한 공고 찾기" — 공고를 먼저 고르는 대신 조건부터 넣는 역방향 판정.
 *
 * {@link EligibilityController} 와 같은 PRG 구조를 쓴다: POST 는 폼을 {@link MatchResultStore}
 * 에 넣고 토큰 GET 으로 리다이렉트만 하며, 실제 순회·판정은 그 GET({@link #result})이 한다 —
 * 결정론적이라 몇 번을 다시 계산해도 결과는 같고, URL 을 북마크·공유할 수 있다.
 *
 * 판정은 전부 {@link AnnouncementMatchService} -> {@code EligibilityEngine} 안에서 끝난다.
 * 여기서 자격을 따지지 않는다.
 *
 * 자연어 입력(/extract)은 {@link EligibilityController} 의 앞단과 같은 역할 — LLM 은 폼을
 * 채워주기만 하고, 채운 값은 사용자가 확인·수정한 뒤에야 위 순회·판정 흐름으로 들어간다.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class MatchController {

    private final MatchResultStore resultStore;
    private final AnnouncementMatchService matchService;
    private final ProfileExtractionService profileExtractionService;

    /**
     * 쿼리 파라미터가 있으면 폼에 미리 채운다 — 빠른 진단 결과의 "자세히 확인하기" 링크가
     * 세 가지 답(혼인·자녀·무주택)을 실어 보낸다. 공유·북마크된 진단 결과로 들어온 사람은
     * 브라우저에 저장된 조건이 없어서, 링크로 넘기지 않으면 같은 답을 다시 적어야 했다.
     * 잘못된 값은 BindingResult 가 받아 두고 기본값으로 둔다(폼 화면이 400 으로 막히지 않게).
     */
    @GetMapping("/match")
    public String form(@ModelAttribute("form") EligibilityForm form, BindingResult ignored, Model model) {
        model.addAttribute("extractionAvailable", profileExtractionService.isAvailable());
        return "match/form";
    }

    /**
     * 자연어 문장에서 폼 값을 뽑아 채운 뒤 같은 폼 화면을 다시 보여준다.
     * {@link EligibilityController#extract} 와 같은 패턴 — 순회·판정은 하지 않는다.
     */
    @PostMapping("/match/extract")
    public String extract(@RequestParam("naturalText") String naturalText, Model model) {
        ProfileExtractionResult result = profileExtractionService.extract(naturalText);

        EligibilityForm form = new EligibilityForm();
        if (result.isExtracted()) {
            form.applyExtracted(result.profile());
        }

        log.info("자연어 폼 채우기(공고 찾기) — 상태={}, 미확인 필드 {}개",
                result.status(), result.unknownFieldLabels().size());

        model.addAttribute("extractionAvailable", profileExtractionService.isAvailable());
        model.addAttribute("form", form);
        model.addAttribute("extraction", result);
        model.addAttribute("naturalText", naturalText);
        return "match/form";
    }

    @PostMapping("/match")
    public String submit(@ModelAttribute("form") EligibilityForm form) {
        String token = resultStore.put(form);
        return "redirect:/match/result/" + token;
    }

    /**
     * @param region/type/sort 판정이 끝난 결과를 보기 좋게 추리고 정렬할 뿐이다 —
     *   자격에 영향을 주지 않는다(같은 조건이면 매칭 결과 자체는 늘 같다).
     *   이상한 값이 와도 무시하고 기본값으로 본다(400 을 내지 않는다).
     */
    @GetMapping("/match/result/{token}")
    public String result(@PathVariable String token,
                         @RequestParam(required = false) String region,
                         @RequestParam(required = false) String type,
                         @RequestParam(required = false) String sort,
                         Model model) {
        var stored = resultStore.get(token);
        if (stored.isEmpty()) {
            return "redirect:/match";
        }

        EligibilityForm form = stored.get();
        ApplicantProfile profile = form.toProfile();
        List<MatchRow> matched = matchService.findMatching(profile);

        MatchRowFilter.Sort parsedSort = MatchRowFilter.Sort.from(sort);
        List<MatchRow> rows = MatchRowFilter.apply(matched, region, type, parsedSort);

        log.info("공고 찾기 실행 — 매칭 {}건 (표시 {}건)", matched.size(), rows.size());

        model.addAttribute("rows", rows);
        model.addAttribute("matchedCount", matched.size());
        model.addAttribute("fixableGroups", matchService.fixableGroups(profile));
        // 0건이면 무엇이 막았는지 — 집계는 서비스가, 여기는 언제 보일지만 정한다
        model.addAttribute("blockers", matched.isEmpty() ? matchService.blockers(profile) : MatchBlockers.empty());
        model.addAttribute("regions", MatchRowFilter.regionsOf(matched));
        model.addAttribute("detailTypes", MatchRowFilter.typesOf(matched));
        model.addAttribute("sorts", MatchRowFilter.Sort.values());
        model.addAttribute("selectedRegion", region);
        model.addAttribute("selectedType", type);
        model.addAttribute("selectedSort", parsedSort);
        model.addAttribute("form", form);
        model.addAttribute("resultToken", token);
        return "match/result";
    }
}
