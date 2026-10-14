package com.portfolio.chungyak.web;

import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.domain.HouseDetailType;
import com.portfolio.chungyak.domain.SpecialSupplyType;
import com.portfolio.chungyak.domain.SupplyCategory;
import com.portfolio.chungyak.domain.UnitType;
import com.portfolio.chungyak.rag.DocumentQaService;
import com.portfolio.chungyak.rag.QaTopicStats;
import com.portfolio.chungyak.rag.QuickQuestions;
import com.portfolio.chungyak.rag.NoticeQuestionService;
import com.portfolio.chungyak.rag.NoticeSummaryTable;
import com.portfolio.chungyak.repository.AnnouncementDocumentRepository;
import com.portfolio.chungyak.rule.ApplicantProfile;
import com.portfolio.chungyak.rule.EligibilityEngine.MatchResult;
import com.portfolio.chungyak.rule.EligibilityEngine.UnitMatch;
import com.portfolio.chungyak.rule.EligibilityEngine;
import com.portfolio.chungyak.rule.GeneralSupplyLotteryCalculator;
import com.portfolio.chungyak.service.AnnouncementQueryService;
import com.portfolio.chungyak.web.form.EligibilityForm;
import com.portfolio.chungyak.web.view.AnnouncementCompareRow;
import com.portfolio.chungyak.web.view.AnnouncementIcs;
import com.portfolio.chungyak.web.view.AnnouncementListRow;
import com.portfolio.chungyak.web.view.AnnouncementListSort;
import com.portfolio.chungyak.web.view.CalendarFilter;
import com.portfolio.chungyak.web.view.CalendarView;
import com.portfolio.chungyak.web.view.Dday;
import com.portfolio.chungyak.web.view.DetailHighlights;
import com.portfolio.chungyak.web.view.PhoneNumbers;
import com.portfolio.chungyak.web.view.Prices;
import com.portfolio.chungyak.web.view.ScheduleTimeline;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
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
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
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
    private final AnnouncementDocumentRepository documentRepository;
    private final NoticeQuestionService questionService;
    private final QaTopicStats quickQuestionOrder;   // 버튼을 많이 눌린 순으로(#40 3단계)

    @GetMapping("/announcements")
    public String list(@RequestParam(required = false) String region,
                       @RequestParam(required = false) String detailType,
                       @RequestParam(required = false) String q,
                       @RequestParam(required = false) String sort,
                       @RequestParam(required = false) String category,
                       Model model) {
        HouseDetailType parsedDetailType = parseDetailType(detailType);
        SupplyCategory parsedCategory = parseCategory(category);
        List<Announcement> announcements =
                queryService.findOpenOrUpcoming(region, parsedDetailType, q, parsedCategory);

        LocalDate today = queryService.today();
        AnnouncementListSort parsedSort = AnnouncementListSort.from(sort);
        List<AnnouncementListRow> rows = parsedSort.apply(announcements.stream()
                .map(a -> AnnouncementListRow.of(a, queryService.statusOf(a), today))
                .toList());

        model.addAttribute("rows", rows);
        model.addAttribute("sorts", AnnouncementListSort.values());
        model.addAttribute("selectedSort", parsedSort);
        model.addAttribute("totalCount", queryService.totalCount());
        model.addAttribute("regions", queryService.availableRegions());
        model.addAttribute("detailTypes", HouseDetailType.values());
        model.addAttribute("selectedRegion", region);
        model.addAttribute("selectedDetailType", parsedDetailType);
        model.addAttribute("selectedKeyword", q);
        model.addAttribute("categories", SupplyCategory.values());
        model.addAttribute("selectedCategory", parsedCategory);
        return "announcements/list";
    }

    /** 빈 값이나 알 수 없는 값은 "구분 없음"으로 본다 — 400 을 내지 않는다. */
    private SupplyCategory parseCategory(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return SupplyCategory.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
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

    /**
     * 접수 시작·마감일을 월 캘린더로. 지난 공고는 조회 단계에서 이미 빠지므로 과거 달은 비어 보인다.
     * 필터는 공고 목록과 같은 조건(지역·주택유형·분양/임대). {@code day} 는 그날 일정을 격자 아래에 다 펼친다.
     */
    @GetMapping("/announcements/calendar")
    public String calendar(@RequestParam(required = false) String month,
                           @RequestParam(required = false) String day,
                           @RequestParam(required = false) String region,
                           @RequestParam(required = false) String detailType,
                           @RequestParam(required = false) String category,
                           Model model) {
        LocalDate today = queryService.today();
        YearMonth ym = parseMonth(month, today);
        CalendarFilter filter = new CalendarFilter(region, parseDetailType(detailType), parseCategory(category));
        List<Announcement> announcements = queryService.findOpenOrUpcoming(
                filter.region(), filter.detailType(), null, filter.category());
        CalendarView calendar = CalendarView.of(announcements, ym, today);
        model.addAttribute("calendar", calendar);
        model.addAttribute("selectedDay", calendar.dayOf(parseDay(day)));
        model.addAttribute("filter", filter);
        model.addAttribute("regions", queryService.availableRegions());
        model.addAttribute("detailTypes", HouseDetailType.values());
        model.addAttribute("categories", SupplyCategory.values());
        return "announcements/calendar";
    }

    /** 잘못된 형식이면 펼치지 않는다 — 400 을 내지 않는다. */
    private LocalDate parseDay(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException e) {
            return null;
        }
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
     * "내 캘린더에 추가" — 공고 일정을 .ics 로. 날짜가 하나도 없는 공고는 404 (화면도 버튼을 숨긴다).
     * 파일 내용은 {@link AnnouncementIcs} 가 만든다.
     */
    @GetMapping("/announcements/{id}/calendar.ics")
    public ResponseEntity<byte[]> calendarIcs(@PathVariable Long id) {
        Announcement announcement = queryService.findDetail(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "공고를 찾을 수 없습니다."));
        if (AnnouncementIcs.events(announcement).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "공고에 일정이 없습니다.");
        }
        String detailUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/announcements/{id}").buildAndExpand(id).toUriString();
        byte[] body = AnnouncementIcs.render(announcement, detailUrl, ZonedDateTime.now(ZoneOffset.UTC));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/calendar; charset=utf-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"chungyak-" + id + ".ics\"")
                .body(body);
    }

    /**
     * 찜한 공고 비교 —스크랩은 이 브라우저의 localStorage 에만 있어 서버는 무엇이 찜됐는지
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
                        profile.filter(p -> a.isEligibilityTarget())   // 무순위는 판정하지 않는다
                                .map(p -> toMatchInfo(eligibilityEngine.evaluate(p, a))).orElse(null)))
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
                      @RequestParam(value = "via", required = false) String via,
                      Model model) {
        // 구조화 데이터 먼저, 아니면 AI(#39). via=quick = 자주 묻는 질문 버튼(#40 집계)
        var qa = questionService.answer(id, question, "quick".equals(via));
        model.addAttribute("qa", qa);
        model.addAttribute("question", question);
        model.addAttribute("qaRephrase", questionService.rephraseSuggestion(question, qa).orElse(null));
        return renderDetail(id, model);
    }

    private String renderDetail(Long id, Model model) {
        Announcement announcement = queryService.findDetail(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "공고를 찾을 수 없습니다."));

        String status = queryService.statusOf(announcement);
        model.addAttribute("announcement", announcement);
        model.addAttribute("status", status);
        // 같은 공고의 정정 전 본·정정본·취소공고 (#47) — 낡은 본이면 최신본으로, 최신본이면 이전 본으로 잇는다
        model.addAttribute("revisions", queryService.findRevisions(announcement));
        model.addAttribute("dday", Dday.of(status, announcement.getReceptBeginDate(),
                announcement.getReceptEndDate(), queryService.today()));
        model.addAttribute("timeline", ScheduleTimeline.of(announcement, queryService.today()));
        model.addAttribute("highlights", DetailHighlights.of(announcement));
        model.addAttribute("hasCalendarEvents", !AnnouncementIcs.events(announcement).isEmpty());
        model.addAttribute("qaEnabled", qaService.isEnabled());
        model.addAttribute("qaIndexed", qaService.hasIndex(id));
        model.addAttribute("qaSemantic", qaService.isSemantic());   // BM25 모드면 '의미' 점수(늘 0)를 숨긴다
        // 공고문 첫 페이지 "단지 주요정보" 표(#38) — 원문 표 그대로, 표가 없거나 칸이 어긋나면 null
        Optional<NoticeSummaryTable> noticeSummary = documentRepository.findByAnnouncementId(id)
                .flatMap(d -> NoticeSummaryTable.parse(d.getFirstPart()));
        model.addAttribute("noticeSummary", noticeSummary.orElse(null));
        // 자주 묻는 질문 버튼(#40) — 공식 데이터 버튼은 이 공고 값으로 답이 나오는 것만
        model.addAttribute("quickQuestions", quickQuestionOrder.sortByClicks(QuickQuestions.of(announcement, noticeSummary)));
        // 문의처는 하이픈 없는 숫자로 들어온다 — 읽을 수 있게 끊어서 보여주고,
        // 거는 건 원본 그대로 tel: 로 넘긴다(다이얼러가 하이픈도 알아서 처리한다).
        model.addAttribute("inquiryTelDisplay", PhoneNumbers.format(announcement.getInquiryTel()));

        // 분양가는 주택형마다 다르다 — 수집은 하고 있었지만 화면에 쓰인 적이 없었다.
        Map<Long, String> prices = announcement.getUnitTypes().stream()
                .filter(u -> Prices.formatManwon(u.getTopAmount()) != null)
                .collect(Collectors.toMap(UnitType::getId, u -> Prices.formatManwon(u.getTopAmount())));
        model.addAttribute("priceByUnitType", prices);

        // 평당가는 표에 열을 하나 더 붙이는 대신 분양가 칸 아래에 같이 적는다 —
        // 이 표는 유형 열까지 합쳐 10칸이 넘어 이미 가로로 밀린다.
        Map<Long, String> perPyeong = announcement.getUnitTypes().stream()
                .filter(u -> Prices.perPyeong(u.getTopAmount(), u.getSupplyArea()) != null)
                .collect(Collectors.toMap(UnitType::getId,
                        u -> Prices.formatManwon(Prices.perPyeong(u.getTopAmount(), u.getSupplyArea()))));
        model.addAttribute("perPyeongByUnitType", perPyeong);

        // 일반공급 가점제/추첨제(B2b)는 민영주택에만 있는 개념이다 — 국민주택은 저축액·
        // 납입횟수 순으로 정하지 가점/추첨 구분이 없다.
        if (announcement.getHouseDetailType() == HouseDetailType.PRIVATE) {
            boolean regulated = announcement.getRegulationFlags() != null
                    && announcement.getRegulationFlags().isRegulatedArea();
            Map<Long, GeneralSupplyLotteryCalculator.Result> lottery = announcement.getUnitTypes().stream()
                    .collect(Collectors.toMap(UnitType::getId,
                            u -> lotteryCalculator.calculate(u.getTypeName(), u.getGeneralSupplyCount(), regulated)));
            model.addAttribute("lotteryByUnitType", lottery);
            // 비규제 공고는 비율이 지자체 공고로 정해져 전 행이 "-" 다 — 빈 열 두 개는 표만 넓힌다.
            // 안내 문단(공고문 확인)은 그대로 두고 열만 뺀다.
            model.addAttribute("showLotteryCols",
                    lottery.values().stream().anyMatch(GeneralSupplyLotteryCalculator.Result::known));
        }
        return "announcements/detail";
    }
}
