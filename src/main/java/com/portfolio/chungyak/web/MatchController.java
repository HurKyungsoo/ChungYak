package com.portfolio.chungyak.web;

import com.portfolio.chungyak.rule.ApplicantProfile;
import com.portfolio.chungyak.service.AnnouncementMatchService;
import com.portfolio.chungyak.web.form.EligibilityForm;
import com.portfolio.chungyak.web.view.MatchRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

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
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class MatchController {

    private final MatchResultStore resultStore;
    private final AnnouncementMatchService matchService;

    @GetMapping("/match")
    public String form(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new EligibilityForm());
        }
        return "match/form";
    }

    @PostMapping("/match")
    public String submit(@ModelAttribute("form") EligibilityForm form) {
        String token = resultStore.put(form);
        return "redirect:/match/result/" + token;
    }

    @GetMapping("/match/result/{token}")
    public String result(@PathVariable String token, Model model) {
        var stored = resultStore.get(token);
        if (stored.isEmpty()) {
            return "redirect:/match";
        }

        EligibilityForm form = stored.get();
        ApplicantProfile profile = form.toProfile();
        List<MatchRow> rows = matchService.findMatching(profile);

        log.info("공고 찾기 실행 — 매칭 {}건", rows.size());

        model.addAttribute("rows", rows);
        model.addAttribute("form", form);
        model.addAttribute("resultToken", token);
        return "match/result";
    }
}
