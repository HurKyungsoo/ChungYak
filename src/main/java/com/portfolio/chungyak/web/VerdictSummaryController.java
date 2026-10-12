package com.portfolio.chungyak.web;

import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.rule.EligibilityEngine;
import com.portfolio.chungyak.service.AnnouncementQueryService;
import com.portfolio.chungyak.web.form.EligibilityForm;
import com.portfolio.chungyak.web.view.EligibilityResultAssembler;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;

/**
 * 공고 상세의 "내 판정 요약" 카드 (목업 Main) — 조각 HTML 만 돌려준다.
 *
 * 사용자 조건은 이 브라우저(localStorage)에만 있어 서버는 모른다. 상세 화면이 저장된 조건을
 * POST 로 보내면(소득·자산이 URL 에 남지 않게) 그 자리에서 판정해 요약 카드만 그려 준다.
 * 판정은 {@link EligibilityEngine} 만 하고, 여기서는 결과를 화면 모델로 옮길 뿐이다. 저장하지 않는다.
 */
@Controller
@RequiredArgsConstructor
public class VerdictSummaryController {

    private final AnnouncementQueryService queryService;
    private final EligibilityEngine eligibilityEngine;
    private final EligibilityResultAssembler resultAssembler;

    @PostMapping("/announcements/{id}/verdict-summary")
    public String summary(@PathVariable Long id, @ModelAttribute("form") EligibilityForm form, Model model) {
        Announcement announcement = queryService.findDetail(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "공고를 찾을 수 없습니다."));
        model.addAttribute("announcement", announcement);
        model.addAttribute("result", resultAssembler.assemble(eligibilityEngine.evaluate(form.toProfile(), announcement)));
        return "announcements/verdict-summary :: card";
    }
}
