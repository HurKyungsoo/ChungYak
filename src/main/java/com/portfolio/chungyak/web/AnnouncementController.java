package com.portfolio.chungyak.web;

import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.domain.HouseDetailType;
import com.portfolio.chungyak.rag.DocumentQaService;
import com.portfolio.chungyak.service.AnnouncementQueryService;
import com.portfolio.chungyak.web.view.AnnouncementListRow;
import com.portfolio.chungyak.web.view.Dday;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

/**
 * 공고 목록·상세 화면.
 *
 * 저장소는 이미 있는 findOpenWithUnitTypes / findByIdWithUnitTypes 를 그대로 쓴다.
 * 컨트롤러에는 비즈니스 로직을 두지 않는다 — 조회·집계는 서비스와 뷰 모델이 한다.
 */
@Controller
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementQueryService queryService;
    private final DocumentQaService qaService;

    @GetMapping("/announcements")
    public String list(@RequestParam(required = false) String region,
                       @RequestParam(required = false) String detailType,
                       Model model) {
        HouseDetailType parsedDetailType = parseDetailType(detailType);
        List<Announcement> announcements = queryService.findOpenOrUpcoming(region, parsedDetailType);

        LocalDate today = queryService.today();
        List<AnnouncementListRow> rows = announcements.stream()
                .map(a -> AnnouncementListRow.of(a, queryService.statusOf(a), today))
                .toList();

        model.addAttribute("rows", rows);
        model.addAttribute("totalCount", queryService.totalCount());
        model.addAttribute("regions", queryService.availableRegions());
        model.addAttribute("detailTypes", HouseDetailType.values());
        model.addAttribute("selectedRegion", region);
        model.addAttribute("selectedDetailType", parsedDetailType);
        return "announcements/list";
    }

    /** 빈 값이나 알 수 없는 값은 "필터 없음"으로 본다 — 400 을 내지 않는다. */
    private HouseDetailType parseDetailType(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return HouseDetailType.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @GetMapping("/announcements/{id}")
    public String detail(@PathVariable Long id, Model model) {
        return renderDetail(id, model);
    }

    /**
     * 공고문 Q&A — 질문을 받아 공고문 발췌 근거로 답한다.
     * 판정이 아니라 정보 검색이다. 답변은 항상 근거 발췌와 함께 표시된다.
     */
    @PostMapping("/announcements/{id}/qa")
    public String ask(@PathVariable Long id,
                      @RequestParam("question") String question,
                      Model model) {
        model.addAttribute("qa", qaService.answer(id, question));
        model.addAttribute("question", question);
        return renderDetail(id, model);
    }

    private String renderDetail(Long id, Model model) {
        Announcement announcement = queryService.findDetail(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "공고를 찾을 수 없습니다."));

        String status = queryService.statusOf(announcement);
        model.addAttribute("announcement", announcement);
        model.addAttribute("status", status);
        model.addAttribute("dday", Dday.of(status, announcement.getReceptBeginDate(),
                announcement.getReceptEndDate(), queryService.today()));
        model.addAttribute("qaEnabled", qaService.isEnabled());
        model.addAttribute("qaIndexed", qaService.hasIndex(id));
        return "announcements/detail";
    }
}
