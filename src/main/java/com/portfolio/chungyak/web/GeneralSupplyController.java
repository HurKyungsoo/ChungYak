package com.portfolio.chungyak.web;

import com.portfolio.chungyak.rule.GeneralSupplyScore;
import com.portfolio.chungyak.rule.GeneralSupplyScoreCalculator;
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

    @GetMapping("/general-supply")
    public String form(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new GeneralSupplyForm());
        }
        return "general-supply/form";
    }

    @PostMapping("/general-supply")
    public String calculate(@ModelAttribute("form") GeneralSupplyForm form, Model model) {
        GeneralSupplyScore score = calculator.calculate(form.toInput());

        log.info("일반공급 가점 계산 — 총점={}, 미산정 {}개",
                score.total(), score.missingInputs().size());

        model.addAttribute("score", score);
        return "general-supply/result";
    }
}
