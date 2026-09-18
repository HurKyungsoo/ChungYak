package com.portfolio.chungyak.web;

import com.portfolio.chungyak.rule.ApplicantProfile;
import com.portfolio.chungyak.service.QuickCheckService;
import com.portfolio.chungyak.web.view.QuickCheckView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 3문항 빠른 진단 — 랜딩에서 소득·자산을 묻기 전에 먼저 보여주는 맛보기.
 *
 * 판정은 {@link QuickCheckService} -> {@code EligibilityEngine} 안에서 끝난다. 여기서 자격을
 * 따지지 않는다.
 *
 * 다른 판정 화면과 달리 PRG(Post-Redirect-Get)를 쓰지 않고 GET 쿼리스트링을 그대로 쓴다 —
 * 여기서 받는 값은 혼인·자녀 수·무주택 세 가지뿐이라 소득·자산처럼 URL 에 남으면 곤란한
 * 정보가 없고, 대신 결과를 새로고침·북마크·공유할 수 있다.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class QuickCheckController {

    private final QuickCheckService quickCheckService;

    @GetMapping("/quick")
    public String check(@RequestParam(defaultValue = "false") boolean married,
                        @RequestParam(required = false) Integer monthsSinceMarriage,
                        @RequestParam(defaultValue = "0") int childCount,
                        @RequestParam(defaultValue = "false") boolean houseless,
                        Model model) {
        ApplicantProfile profile = ApplicantProfile.builder()
                .married(married)
                .monthsSinceMarriage(monthsSinceMarriage)
                .childCount(Math.max(0, childCount))
                .houseless(houseless)
                .build();

        QuickCheckView view = quickCheckService.check(profile);
        log.info("빠른 진단 — 가능성 {}종, 탈락 확정 {}종", view.possible().size(), view.ruledOut().size());

        model.addAttribute("result", view);
        model.addAttribute("married", married);
        model.addAttribute("monthsSinceMarriage", monthsSinceMarriage);
        model.addAttribute("childCount", childCount);
        model.addAttribute("houseless", houseless);
        return "quick/result";
    }
}
