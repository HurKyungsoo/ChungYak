package com.portfolio.chungyak.web;

import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.domain.HouseDetailType;
import com.portfolio.chungyak.domain.HouseType;
import com.portfolio.chungyak.domain.RegulationFlags;
import com.portfolio.chungyak.domain.SupplyBreakdown;
import com.portfolio.chungyak.domain.SupplyCategory;
import com.portfolio.chungyak.domain.UnitType;
import com.portfolio.chungyak.repository.AnnouncementRepository;
import com.portfolio.chungyak.service.AnnouncementSyncScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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
@AutoConfigureMockMvc
@Transactional
class ScreenRenderTest {

    @Autowired private MockMvc mvc;
    @Autowired private AnnouncementRepository repository;
    @MockBean private AnnouncementSyncScheduler scheduler;

    private Long aptId;
    private Long lhId;

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
    }

    /** 200 + 문서 끝까지(렌더링 도중 끊기면 </html> 이 없다) */
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
        render("/announcements/compare?ids=" + aptId + "," + lhId);
        render("/match");
        render("/general-supply");
        render("/quick?married=true&monthsSinceMarriage=40&childCount=1&houseless=true");
        render("/announcements/" + aptId + "/eligibility");
    }

    @Test
    @DisplayName("공고 상세 — 청약홈(벤토·유형별 배정·표)과 LH(공고문 묶음)")
    void detailScreens() throws Exception {
        String apt = render("/announcements/" + aptId);
        assertThat(apt).contains("특별공급 유형별 배정").contains("bento-card").contains("청약 일정");
        assertThat(apt).contains("data-type=\"NEWLYWED\"")                        // 자격 표시를 칠할 타일
                .contains("이 공고에 없는 유형").contains("다자녀가구, 노부모부양, 청년");

        String lh = render("/announcements/" + lhId);
        assertThat(lh).contains("공고문에서 확인하세요").contains("공고문 PDF").contains("LH 공고 페이지");
        assertThat(lh).doesNotContain("청약통장 6개월");   // LH 는 규제·통장 줄이 없다
    }

    @Test
    @DisplayName("판정 결과 — 근거 카드가 끝까지 그려지고 사유의 내 값은 굵게(th:utext)")
    void verdictResultRenders() throws Exception {
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
        // 타일 자격 표시용 — 신혼부부는 충족, 다자녀(자녀 0명)는 미충족으로 엔진 결과가 실린다
        assertThat(body).containsPattern("data-eligible=\"[^\"]*NEWLYWED")
                .containsPattern("data-ineligible=\"[^\"]*MULTI_CHILD");
    }
}
