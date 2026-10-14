package com.portfolio.chungyak.web;

import com.portfolio.chungyak.rule.GeneralSupplyScore;
import com.portfolio.chungyak.rule.GeneralSupplyScoreCalculator;
import com.portfolio.chungyak.service.CompetitionStatsService;
import com.portfolio.chungyak.web.form.GeneralSupplyForm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * 일반공급 청약가점 계산 화면.
 *
 * 공고와 무관하다 — 가점은 신청자 자신의 값으로만 정해진다.
 * 계산은 전부 {@link GeneralSupplyScoreCalculator}(rule 패키지, 순수 함수)에서 끝난다.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class GeneralSupplyController {

    private final GeneralSupplyScoreCalculator calculator;
    /** 지역 최근 당첨 기록과 비교(#34) — 참고 정보. 가점 계산(rule)과 따로 돈다 */
    private final CompetitionStatsService competitionStats;
    private final java.time.Clock clock;

    @GetMapping("/general-supply")
    public String form(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new GeneralSupplyForm());
        }
        model.addAttribute("regions", CompetitionStatsService.REGIONS);
        model.addAttribute("bands", CompetitionStatsService.BANDS);
        return "general-supply/form";
    }

    @PostMapping("/general-supply")
    public String calculate(@ModelAttribute("form") GeneralSupplyForm form, Model model) {
        GeneralSupplyScore score = calculator.calculate(form.toInput());

        log.info("일반공급 가점 계산 — 총점={}, 미산정 {}개",
                score.total(), score.missingInputs().size());

        model.addAttribute("score", score);
        if (score.isComplete()) {
            model.addAttribute("comparison", competitionStats
                    .compareScore(form.getRegion(), form.getBand(), score.total(), java.time.LocalDate.now(clock))
                    .orElse(null));
        }
        model.addAttribute("comparisonAsked", form.getRegion() != null && !form.getRegion().isBlank());
        return "general-supply/result";
    }
}
