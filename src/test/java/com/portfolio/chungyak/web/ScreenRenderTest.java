package com.portfolio.chungyak.web;

import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.domain.HouseDetailType;
import com.portfolio.chungyak.domain.HouseType;
import com.portfolio.chungyak.domain.RegulationFlags;
import com.portfolio.chungyak.domain.SupplyBreakdown;
import com.portfolio.chungyak.domain.SupplyCategory;
import com.portfolio.chungyak.domain.UnitType;
import com.portfolio.chungyak.llm.ExtractedProfile;
import com.portfolio.chungyak.llm.ProfileExtractionResult;
import com.portfolio.chungyak.llm.ProfileExtractionService;
import com.portfolio.chungyak.repository.AnnouncementRepository;
import com.portfolio.chungyak.service.AnnouncementSyncScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 주요 화면을 <b>실제 Thymeleaf 로 그려</b> 템플릿이 깨지지 않는지 본다.
 *
 * 컨트롤러 단위 테스트(standaloneSetup)는 템플릿을 렌더링하지 않아서, th:utext 안의 T(...) 정적 호출
 * (Thymeleaf 3.1 이 금지)처럼 템플릿에서만 터지는 오류를 못 잡는다 — 그대로 push 하면 서버 화면이 깨진다.
 * 여기서는 화면을 끝까지 그려 200 + 문서 끝(&lt;/html&gt;)까지 나오는지 확인한다.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:render-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "security.admin.username=admin",
        "security.admin.password=test-secret"
})
@TestPropertySource(locations = "classpath:ai-off.properties")
@AutoConfigureMockMvc
@Transactional
class ScreenRenderTest {

    @Autowired private MockMvc mvc;
    @Autowired private AnnouncementRepository repository;
    @Autowired private com.portfolio.chungyak.repository.AnnouncementDocumentRepository documentRepository;
    @MockBean private AnnouncementSyncScheduler scheduler;
    @MockBean private ProfileExtractionService extractionService;
    @MockBean private com.portfolio.chungyak.service.SpecialSupplyApplicationService applicationService;   // 외부 API 를 부르지 않게   // 키 없이도 추출 결과 화면을 그려 본다

    private Long aptId;
    private Long lhId;
    private Long remainderId;

    @BeforeEach
    void seed() {
        LocalDate today = LocalDate.now();

        Announcement apt = Announcement.builder()
                .externalId("APT-RENDER-1").houseManageNo("1").pblancNo("1").houseName("렌더링 테스트 아파트")
                .houseType(HouseType.APT).houseDetailType(HouseDetailType.PRIVATE)
                .regionName("서울").address("서울특별시 어딘가 1").constructorName("(주)시공")
                .supplyCategory(SupplyCategory.SALE).totalSupplyCount(100)
                .noticeDate(today.minusDays(3)).receptBeginDate(today).receptEndDate(today.plusDays(2))
                .specialReceptBeginDate(today).specialReceptEndDate(today)
                .winnerAnnounceDate(today.plusDays(9)).moveInYearMonth("202912")
                .regulationFlags(RegulationFlags.builder().build()).build();
        apt.addUnitType(UnitType.builder().modelNo("01").typeName("084.9A").supplyArea("112.34")
                .generalSupplyCount(60).specialSupplyCount(40).topAmount(89300)
                .supplyBreakdown(SupplyBreakdown.builder().newlywed(20).newborn(10).firstTime(10).build()).build());
        aptId = repository.save(apt).getId();
        // 공고문 첫 페이지 "단지 주요정보" 표(#38) — 상세에 요약표로 그려져야 한다
        documentRepository.save(new com.portfolio.chungyak.domain.AnnouncementDocument(aptId, "APPLYHOME",
                "주택유형 해당지역 기타지역 규제지역여부\n재당첨제한 전매제한 거주의무기간 분양가상한제 택지유형\n"
                        + "없음 6개월 없음 미적용 민간택지\n본문", java.time.Instant.now()));

        Announcement lh = Announcement.builder()
                .externalId("LH-RENDER-1").houseManageNo("03").pblancNo("2").houseName("렌더링 행복주택 예비입주자 모집공고")
                .houseType(HouseType.UNKNOWN).houseDetailType(HouseDetailType.PUBLIC)
                .regionName("경기도").supplyCategory(SupplyCategory.RENTAL).supplyTypeName("행복주택")
                .noticeUrl("https://apply.lh.or.kr/x").noticePdfUrl("https://apply.lh.or.kr/y.pdf")
                .receptEndDate(today.plusDays(3)).totalSupplyCount(40)
                .regulationFlags(RegulationFlags.builder().build()).build();
        lh.addUnitType(UnitType.builder().modelNo("01").typeName("26A").supplyArea("38.50")
                .generalSupplyCount(40).supplyBreakdown(SupplyBreakdown.ofPresentTypes(java.util.Set.of())).build());
        lhId = repository.save(lh).getId();

        Announcement remainder = Announcement.builder()
                .externalId("APPLYHOME-RM-RENDER-1").houseManageNo("9").pblancNo("9").houseName("렌더링 무순위 단지")
                .houseType(HouseType.REMAINDER).houseDetailType(HouseDetailType.UNKNOWN)
                .supplyCategory(SupplyCategory.REMAINDER).regionName("서울").totalSupplyCount(10)
                .noticeUrl("https://www.applyhome.co.kr/x")
                .receptBeginDate(today.plusDays(1)).receptEndDate(today.plusDays(1)).winnerAnnounceDate(today.plusDays(4))
                .regulationFlags(RegulationFlags.builder().build()).build();
        remainder.addUnitType(UnitType.builder().modelNo("01").typeName("039.6950").generalSupplyCount(10)
                .topAmount(76112).supplyBreakdown(SupplyBreakdown.builder().build()).build());
        remainderId = repository.save(remainder).getId();
    }

    @Test
    @DisplayName("무순위 공고 — 상세는 판정 대상 아님 안내·공고문 버튼, 판정 버튼·특공 타일 없음 / 판정 화면은 상세로 / 목록은 '무순위·잔여'")
    void remainderScreens() throws Exception {
        String detail = render("/announcements/" + remainderId);
        assertThat(detail).contains("무순위 공고입니다").contains("특별공급 자격 판정 대상이 아닙니다")
                .contains("청약홈 공고문").contains("공급 세대")
                .doesNotContain("내 조건으로 자격 판정").doesNotContain(">특별공급</th>").doesNotContain("id=\"verdictSummarySlot\"");

        mvc.perform(get("/announcements/" + remainderId + "/eligibility"))
                .andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl("/announcements/" + remainderId));

        String home = render("/announcements");
        assertThat(home).contains("무순위·줍줍 1건 모아보기");   // 바로가기 칩
        // 히어로 버튼은 하나 (#46) — 캘린더·가점 계산은 머리 메뉴에만
        assertThat(home).doesNotContain("class=\"btn btn-ghost\">청약 캘린더</a>");
        String list = render("/announcements?category=REMAINDER");
        assertThat(list).contains("렌더링 무순위 단지").contains("무순위·잔여").contains("공급 10세대")
                .doesNotContain("특공 물량 없음").contains("← 전체 공고 보기");
    }

    /** 200 + 문서 끝까지(렌더링 도중 끊기면 </html> 이 없다) */
    // 공고문 Q&A 는 키가 없으면 꺼진다 — 화면만 그려 보려고 응답을 정해 둔다(기본값은 꺼짐 그대로)
    @MockBean private com.portfolio.chungyak.rag.DocumentQaService qaService;
    @MockBean private com.portfolio.chungyak.rag.NoticeQuestionService questionService;

    @Test
    @DisplayName("국토부 공식 답변 카드 — FAQ 질문·답 원문, 기준일 뒤 개정 경고(개정 이유), 비슷한 다른 문항, 출처 쪽수")
    void officialFaqCard() throws Exception {
        var faq = com.portfolio.chungyak.rag.OfficialFaq.load();
        var item = faq.item(146);   // 특별공급 횟수 — 2025-03-31 개정과 겹친다
        var match = new com.portfolio.chungyak.rag.OfficialFaqSearch.Match(item, 0.82, java.util.List.of(faq.item(147)),
                faq.amendmentsFor(item), faq.source(), faq.basisDate(), faq.url());
        var qa = new com.portfolio.chungyak.rag.DocumentQaService.QaAnswer(
                com.portfolio.chungyak.rag.DocumentQaService.Status.ANSWERED, "AI 답변", java.util.List.of());
        org.mockito.Mockito.when(qaService.isEnabled()).thenReturn(true);
        org.mockito.Mockito.when(qaService.hasIndex(org.mockito.ArgumentMatchers.anyLong())).thenReturn(true);
        org.mockito.Mockito.when(questionService.answer(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(qa);
        org.mockito.Mockito.when(questionService.rephraseSuggestion(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any())).thenReturn(java.util.Optional.empty());
        org.mockito.Mockito.when(questionService.officialAnswer(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any())).thenReturn(java.util.Optional.of(match));

        MvcResult r = mvc.perform(post("/announcements/" + aptId + "/qa").param("question", "결혼 전에 특공 받았는데 또 넣을 수 있어요?"))
                .andExpect(status().isOk()).andReturn();
        String body = r.getResponse().getContentAsString();
        assertThat(body.stripTrailing()).endsWith("</html>");
        assertThat(body).contains("국토부 공식 답변").contains("Q146.").contains("AI 를 거치지 않았습니다")
                .contains("이 답은 2024년 5월 기준입니다").contains("2025-03-31 시행 (국토교통부령 제1471호)")
                .contains("개정 이유(국가법령정보센터 원문)").contains("Q147.")
                .contains("2024 주택청약 FAQ").contains(item.page() + "쪽")
                .contains("AI 답변");
        // 공식 답이 AI 답보다 먼저
        assertThat(body.indexOf("국토부 공식 답변")).isLessThan(body.indexOf("AI 답변"));
    }

    private String render(String url) throws Exception {
        MvcResult r = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        String body = r.getResponse().getContentAsString();
        assertThat(body.stripTrailing()).as("%s 가 끝까지 렌더링돼야 한다", url).endsWith("</html>");
        return body;
    }

    @Test
    @DisplayName("목록·캘린더·구분 필터·빈 상태·비교·조건 찾기·가점 화면")
    void listAndPlainScreens() throws Exception {
        render("/announcements");
        render("/announcements?category=RENTAL");
        render("/announcements?q=존재하지않는검색어&region=서울&category=SALE");   // 빈 상태 + 필터 해제 링크
        render("/announcements/calendar");
        // 필터 + 날짜 펼치기 — 오늘(시드 공고의 접수 시작일) 패널에 공고가 나오고, 링크가 필터를 이어 간다
        String cal = render("/announcements/calendar?day=" + LocalDate.now() + "&region=서울&detailType=PRIVATE");
        assertThat(cal).contains("id=\"dayPanel\"").contains("렌더링 테스트 아파트")
                .contains("&amp;region=%EC%84%9C%EC%9A%B8&amp;detailType=PRIVATE")
                .doesNotContain("렌더링 행복주택");   // 경기도 LH 는 지역 필터로 빠진다
        render("/announcements/calendar?day=잘못된날짜&category=없는값");          // 400 대신 그냥 무시
        render("/announcements/compare?ids=" + aptId + "," + lhId);
        // 재당첨 제한은 접어 둔다 (#46) — 빈 폼이면 닫힌 채로, 칸은 폼 안에 그대로
        for (String url : new String[]{"/match", "/announcements/" + aptId + "/eligibility"}) {
            String form = render(url);
            assertThat(form).as(url).contains("<details class=\"set-collapse\" data-auto-open>")
                    .contains("name=\"monthsSinceLastWin\"");
        }
        render("/general-supply");
        // 처리방침 — 위탁 절은 켜진 기능만 그린다(개발 PC 는 AI 키가 있을 수 있어 내용은 단정하지 않는다)
        assertThat(render("/my-profile")).contains("내 조건").contains("profileDraft");
        assertThat(render("/privacy")).contains("개인정보 처리방침").contains("제3자 제공과 처리 위탁");
        render("/quick?married=true&monthsSinceMarriage=40&childCount=1&houseless=true");
        render("/announcements/" + aptId + "/eligibility");
    }

    @Test
    @DisplayName("공고 상세 — 청약홈(벤토·유형별 배정·표)과 LH(공고문 묶음)")
    void detailScreens() throws Exception {
        String apt = render("/announcements/" + aptId);
        assertThat(apt).contains("특별공급 유형별 배정").contains("bento-card").contains("청약 일정");
        assertThat(apt).contains("data-type=\"NEWLYWED\"")                        // 유형별 합계 타일
                .contains("이 공고에 없는 유형").contains("다자녀가구, 노부모부양, 청년");
        assertThat(apt).contains("/announcements/" + aptId + "/calendar.ics");   // 내 캘린더에 추가
        // 화면 단순화 (#46) — 표 설명은 접고, 내 자격은 판정 요약 한 곳(조건이 없으면 빈 자리)
        assertThat(apt).contains("주택형별 공급").contains("<summary>표 읽는 법</summary>")
                .contains("id=\"verdictSummarySlot\"").doesNotContain("is-eligible");
        // 공고문 요약표(#38) — 원문 표 그대로
        assertThat(apt).contains("공고문 요약표").contains("<dd>6개월</dd>").contains("<dd>민간택지</dd>");
        // 비규제 민영은 가점제 비율을 몰라 전 행이 "-" — 빈 열은 그리지 않는다(안내 문단은 남는다)
        assertThat(apt).doesNotContain(">가점제</th>").contains("비규제지역이라 정확한 비율은");

        String lh = render("/announcements/" + lhId);
        assertThat(lh).contains("공고문에서 확인하세요").contains("공고문 PDF").contains("LH 공고 페이지");
        assertThat(lh).doesNotContain("청약통장 6개월");   // LH 는 규제·통장 줄이 없다
    }

    @Test
    @DisplayName("내 캘린더에 추가 — text/calendar 첨부로 내려가고, 공고에 있는 날짜만 담긴다")
    void calendarIcs() throws Exception {
        MvcResult r = mvc.perform(get("/announcements/" + aptId + "/calendar.ics"))
                .andExpect(status().isOk()).andReturn();
        assertThat(r.getResponse().getContentType()).startsWith("text/calendar");
        assertThat(r.getResponse().getHeader("Content-Disposition")).contains("chungyak-" + aptId + ".ics");
        String ics = new String(r.getResponse().getContentAsByteArray(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n ", "");
        assertThat(ics).contains("[특별공급 접수] 렌더링 테스트 아파트", "[청약 접수] 렌더링 테스트 아파트",
                "[당첨자 발표] 렌더링 테스트 아파트", "/announcements/" + aptId);

        mvc.perform(get("/announcements/999999/calendar.ics")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("판정 결과 — 근거 카드가 끝까지 그려지고 사유의 내 값은 굵게(th:utext)")
    void verdictResultRenders() throws Exception {
        org.mockito.Mockito.when(applicationService.forAnnouncement(org.mockito.ArgumentMatchers.any())).thenReturn(
                new com.portfolio.chungyak.service.SpecialSupplyApplicationService.Summary(java.util.Map.of(
                        "FIRST_TIME", new com.portfolio.chungyak.external.SpecialSupplyApplication.Count(1, 2950))));
        MvcResult posted = mvc.perform(post("/announcements/" + aptId + "/eligibility")
                        .param("married", "true").param("monthsSinceMarriage", "100")
                        .param("houseless", "true").param("accountMonths", "3")
                        .param("accountDeposit", "3000000").param("householdSize", "3")
                        .param("monthlyHouseholdIncome", "5000000"))
                .andExpect(status().is3xxRedirection()).andReturn();
        String body = render(posted.getResponse().getRedirectedUrl());

        assertThat(body).contains("decision-grid").contains("내 조건").contains("자격 충족");
        // 혼인기간 100개월은 내 값 → 굵게, 괄호 안 84개월은 기준 → 그대로
        assertThat(body).contains("<strong>100개월</strong>").contains("(84개월)");
        // 맨 위 요약 카드(#21) — AI 없이 늘 그려지고, 유형 이름이 근거 카드로 이어진다
        assertThat(body).contains("summary-card").contains("href=\"#decision-");
        // 특공 신청 현황(#33) — 생애최초 카드에 청약홈 집계 신청 건수, 참고 안내
        assertThat(body).contains("신청 2,950건").contains("참고용입니다");
    }

    @Test
    @DisplayName("자연어 추출 결과 — '이렇게 읽었어요'·문장 속 기간과 어긋나는 경고·AI 가 채운 칸 목록 (두 화면 공통)")
    void extractionBanner() throws Exception {
        String text = "청약통장은 3년 됐어요";
        // 모델이 3년을 24개월로 읽은 상황
        ExtractedProfile misread = new ExtractedProfile(null, null, null, null, true, 24, null, null, null);
        org.mockito.Mockito.when(extractionService.isAvailable()).thenReturn(true);
        org.mockito.Mockito.when(extractionService.extract(text))
                .thenReturn(ProfileExtractionResult.extracted(misread, text));

        for (String url : new String[]{"/match/extract", "/announcements/" + aptId + "/eligibility/extract"}) {
            String body = mvc.perform(post(url).param("naturalText", text))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(body.stripTrailing()).as(url).endsWith("</html>");
            assertThat(body).as(url).contains("청약통장 가입 24개월(2년)")        // 이렇게 읽었어요
                    .contains("숫자를 한 번 더 확인해 주세요").contains("36개월(3년)")   // 원문 숫자와 대조 경고
                    .contains("data-filled=\"houseless,accountMonths\"");        // 이 칸들에 "AI 입력"
        }
    }

    @Autowired private com.portfolio.chungyak.mapper.CompetitionMapper competitionMapper;

    @Test
    @DisplayName("이 지역 최근 당첨 기록 (#34) — 같은 시도 지난 민영 공고 3건의 가점·경쟁률로 카드가 그려진다(H2 에서 매퍼 SQL)")
    void competitionHistoryCard() throws Exception {
        LocalDate today = LocalDate.now();
        String[] houses = {"P-1", "P-2", "P-3"};
        int[] lows = {41, 55, 63};
        for (int i = 0; i < houses.length; i++) {
            Announcement pastNotice = Announcement.builder()
                    .externalId("APT-PAST-" + i).houseManageNo(houses[i]).pblancNo(houses[i]).houseName("지난 공고 " + i)
                    .houseType(HouseType.APT).houseDetailType(HouseDetailType.PRIVATE).regionName("서울")
                    .address("서울특별시 강서구 " + i).supplyCategory(SupplyCategory.SALE).noticeDate(today.minusMonths(2 + i * 3))
                    .receptBeginDate(today.minusMonths(2 + i * 3)).receptEndDate(today.minusMonths(2 + i * 3))
                    .regulationFlags(RegulationFlags.builder().build()).build();
            pastNotice.addUnitType(UnitType.builder().modelNo("01").typeName("084.9A").supplyArea("112.34")
                    .generalSupplyCount(50).specialSupplyCount(20).topAmount(80_000 + i * 5_000)
                    .supplyBreakdown(SupplyBreakdown.builder().newlywed(20).build()).build());
            repository.save(pastNotice);
        }
        competitionMapper.insertScores(java.util.List.of(
                new com.portfolio.chungyak.external.CompetitionRecords.Score("P-1", "01", "084.9A", "01", lows[0], 50, 60),
                new com.portfolio.chungyak.external.CompetitionRecords.Score("P-2", "01", "084.9A", "01", lows[1], 58, 66),
                new com.portfolio.chungyak.external.CompetitionRecords.Score("P-3", "01", "084.9A", "01", lows[2], 65, 70),
                new com.portfolio.chungyak.external.CompetitionRecords.Score("P-3", "01", "084.9A", "02", 30, 35, 40)));   // 기타지역 — 안 쓴다
        competitionMapper.insertRates(java.util.List.of(
                new com.portfolio.chungyak.external.CompetitionRecords.Rate("P-1", "01", "084.9A", 1, "01", 10, 120, "12.00"),
                new com.portfolio.chungyak.external.CompetitionRecords.Rate("P-2", "01", "084.9A", 1, "01", 10, 3, "(△7)")));

        String apt = render("/announcements/" + aptId);

        assertThat(apt).contains("서울 최근 당첨 기록").contains("청약홈 당첨 통계").contains("최근 12개월 3건")
                .contains("41~63점").contains("중앙값 55점 · 가점 있는 3건").contains("미달 1건 / 3건").contains("경쟁 있던 1건 해당지역 중앙값 12.0 : 1")
                .contains("비슷한 최근 공고 (60~85㎡)").contains("지난 공고 0").contains("강서구").contains("12.0 : 1")
                .contains("당첨 가능성을 뜻하지 않습니다");

        // 가점 계산 결과의 지역 비교 — 같은 기록(서울 60~85㎡, 공고별 최저가점 41·55·63)과 내 가점 55점
        assertThat(render("/general-supply")).contains("지역 기록과 비교").contains("<option value=\"서울\">서울</option>");
        MvcResult r = mvc.perform(post("/general-supply")
                        .param("age", "40").param("married", "true").param("houselessMonths", "120")
                        .param("dependents", "1").param("accountMonths", "120")
                        .param("region", "서울").param("band", "60~85㎡"))
                .andExpect(status().isOk()).andReturn();
        String result = r.getResponse().getContentAsString();
        java.util.regex.Matcher total = java.util.regex.Pattern.compile("내 가점\\(<span>(\\d+)점</span>\\)").matcher(result);
        assertThat(total.find()).as("가점 비교 카드").isTrue();
        int mine = Integer.parseInt(total.group(1));
        long expected = java.util.stream.IntStream.of(lows).filter(v -> v <= mine).count();
        assertThat(result).contains("서울 60~85㎡ 최근 당첨 기록과 비교 (참고)").contains("<strong>3건</strong>")
                .contains("<strong>" + expected + "건</strong>입니다").contains("41~63점 (중앙값 55점)");
    }

    @Test
    @DisplayName("공고 상세 '내 판정 요약' 조각")
    void verdictSummaryFragment() throws Exception {
        MvcResult r = mvc.perform(post("/announcements/" + aptId + "/verdict-summary")
                        .param("married", "true").param("monthsSinceMarriage", "12")
                        .param("houseless", "true").param("householdHead", "true").param("accountMonths", "30")
                        .param("accountDeposit", "3000000").param("householdSize", "3")
                        .param("monthlyHouseholdIncome", "5000000").param("residenceMonthsInRegion", "30"))
                .andExpect(status().isOk()).andReturn();
        String body = r.getResponse().getContentAsString();
        assertThat(body).contains("내 판정 요약").contains("verdict-mini");
        // 내 자격은 이 카드 한 곳에서만 (#46) — 신혼부부는 넣을 수 있는 유형으로 엔진 결과가 실린다
        assertThat(body).contains("자격 충족").contains("미충족").containsPattern("<strong>[^<]*신혼부부[^<]*</strong>");
    }
}
