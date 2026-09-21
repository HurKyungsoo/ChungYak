package com.portfolio.chungyak.web;

import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.domain.HouseDetailType;
import com.portfolio.chungyak.domain.SpecialSupplyType;
import com.portfolio.chungyak.domain.UnitType;
import com.portfolio.chungyak.rag.DocumentQaService;
import com.portfolio.chungyak.rule.ApplicantProfile;
import com.portfolio.chungyak.rule.EligibilityEngine;
import com.portfolio.chungyak.rule.EligibilityEngine.MatchResult;
import com.portfolio.chungyak.rule.EligibilityEngine.UnitMatch;
import com.portfolio.chungyak.rule.GeneralSupplyLotteryCalculator;
import com.portfolio.chungyak.service.AnnouncementQueryService;
import com.portfolio.chungyak.web.form.EligibilityForm;
import com.portfolio.chungyak.web.view.AnnouncementCompareRow;
import com.portfolio.chungyak.web.view.AnnouncementListRow;
import com.portfolio.chungyak.web.view.CalendarView;
import com.portfolio.chungyak.web.view.Dday;
import com.portfolio.chungyak.web.view.PhoneNumbers;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

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
    private final GeneralSupplyLotteryCalculator lotteryCalculator;
    private final EligibilityEngine eligibilityEngine;
    private final MatchResultStore matchResultStore;

    @GetMapping("/announcements")
    public String list(@RequestParam(required = false) String region,
                       @RequestParam(required = false) String detailType,
                       @RequestParam(required = false) String q,
                       Model model) {
        HouseDetailType parsedDetailType = parseDetailType(detailType);
        List<Announcement> announcements = queryService.findOpenOrUpcoming(region, parsedDetailType, q);

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
        model.addAttribute("selectedKeyword", q);
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

    /** 접수 시작·마감일을 월 캘린더로. 지난 공고는 조회 단계에서 이미 빠지므로 과거 달은 비어 보인다. */
    @GetMapping("/announcements/calendar")
    public String calendar(@RequestParam(required = false) String month, Model model) {
        LocalDate today = queryService.today();
        YearMonth ym = parseMonth(month, today);
        List<Announcement> announcements = queryService.findOpenOrUpcoming(null, null, null);
        model.addAttribute("calendar", CalendarView.of(announcements, ym, today));
        return "announcements/calendar";
    }

    /** 잘못된 형식이면 이번 달로 — 400 을 내지 않는다. */
    private YearMonth parseMonth(String raw, LocalDate today) {
        if (raw == null || raw.isBlank()) return YearMonth.from(today);
        try {
            return YearMonth.parse(raw);
        } catch (DateTimeParseException e) {
            return YearMonth.from(today);
        }
    }

    @GetMapping("/announcements/{id}")
    public String detail(@PathVariable Long id, Model model) {
        return renderDetail(id, model);
    }

    /**
     * 찜한 공고 비교 — 스크랩은 이 브라우저의 localStorage 에만 있어 서버는 무엇이 찜됐는지
     * 모른다. 화면이 JS 로 찜한 id 를 모아 이 쿼리스트링으로 요청하면, 그 id들만 조회해서
     * 나란히 보여줄 뿐이다. 너무 많이 넘어와도 카드가 난립하지 않게 최대 6개로 자른다.
     */
    @GetMapping("/announcements/compare")
    public String compare(@RequestParam(required = false) String ids,
                          @RequestParam(required = false) String token,
                          Model model) {
        List<Long> parsedIds = parseIds(ids);
        LocalDate today = queryService.today();

        // token 이 있으면 저장해 둔 조건으로 공고마다 실제 판정까지 해서 보여준다.
        // 판정은 EligibilityEngine 만 한다 — 여기서 자격을 따지지 않는다.
        Optional<ApplicantProfile> profile = Optional.ofNullable(token)
                .flatMap(matchResultStore::get)
                .map(EligibilityForm::toProfile);

        List<AnnouncementCompareRow> rows = parsedIds.stream()
                .map(queryService::findDetail)
                .flatMap(Optional::stream)
                .map(a -> AnnouncementCompareRow.of(a, queryService.statusOf(a), today,
                        profile.map(p -> toMatchInfo(eligibilityEngine.evaluate(p, a))).orElse(null)))
                .toList();

        model.addAttribute("rows", rows);
        model.addAttribute("requestedCount", parsedIds.size());
        model.addAttribute("profileApplied", profile.isPresent());
        model.addAttribute("compareToken", token);
        return "announcements/compare";
    }

    /**
     * 브라우저에 저장된 조건을 받아 비교 화면에 적용한다.
     *
     * 조건에는 소득·자산이 들어 있어 쿼리스트링에 남기면 안 되므로, 폼을 저장소에 넣고
     * 토큰만 URL 에 실어 리다이렉트한다({@link EligibilityController} 와 같은 PRG).
     */
    @PostMapping("/announcements/compare")
    public String compareWithProfile(@RequestParam(required = false) String ids,
                                     @ModelAttribute("form") EligibilityForm form) {
        String token = matchResultStore.put(form);
        String idsParam = ids == null ? "" : ids;
        return "redirect:/announcements/compare?ids="
                + URLEncoder.encode(idsParam, StandardCharsets.UTF_8) + "&token=" + token;
    }

    /** MatchResult -> 화면 모델. 규칙 엔진이 낸 결과를 옮겨 담기만 한다. */
    private AnnouncementCompareRow.MatchInfo toMatchInfo(MatchResult result) {
        List<String> typeLabels = result.matches().stream()
                .flatMap(m -> m.applicableTypes().stream())
                .map(SpecialSupplyType::getLabel)
                .distinct()
                .toList();
        UnitMatch best = result.bestMatch();
        int allocated = result.matches().stream().mapToInt(UnitMatch::totalAllocated).sum();

        return new AnnouncementCompareRow.MatchInfo(
                result.hasAnyMatch(),
                typeLabels,
                result.matches().size(),
                best != null && best.allocationCountKnown(),
                allocated);
    }

    /** 콤마로 구분된 id 목록. 형식이 이상해도 걸러낼 뿐 400 을 내지 않는다. */
    private List<Long> parseIds(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(this::parseLongSafely)
                .filter(Objects::nonNull)
                .distinct()
                .limit(6)
                .toList();
    }

    private Long parseLongSafely(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
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
        // 문의처는 하이픈 없는 숫자로 들어온다 — 읽을 수 있게 끊어서 보여주고,
        // 거는 건 원본 그대로 tel: 로 넘긴다(다이얼러가 하이픈도 알아서 처리한다).
        model.addAttribute("inquiryTelDisplay", PhoneNumbers.format(announcement.getInquiryTel()));

        // 일반공급 가점제/추첨제(B2b)는 민영주택에만 있는 개념이다 — 국민주택은 저축액·
        // 납입횟수 순으로 정하지 가점/추첨 구분이 없다.
        if (announcement.getHouseDetailType() == HouseDetailType.PRIVATE) {
            boolean regulated = announcement.getRegulationFlags() != null
                    && announcement.getRegulationFlags().isRegulatedArea();
            Map<Long, GeneralSupplyLotteryCalculator.Result> lottery = announcement.getUnitTypes().stream()
                    .collect(Collectors.toMap(UnitType::getId,
                            u -> lotteryCalculator.calculate(u.getTypeName(), u.getGeneralSupplyCount(), regulated)));
            model.addAttribute("lotteryByUnitType", lottery);
        }
        return "announcements/detail";
    }
}
