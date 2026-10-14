package com.portfolio.chungyak.rag;

import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.domain.HouseDetailType;
import com.portfolio.chungyak.domain.HouseType;
import com.portfolio.chungyak.domain.RegulationFlags;
import com.portfolio.chungyak.domain.SpecialSupplyType;
import com.portfolio.chungyak.domain.SupplyBreakdown;
import com.portfolio.chungyak.domain.SupplyCategory;
import com.portfolio.chungyak.domain.UnitType;
import com.portfolio.chungyak.rag.QuickQuestions.QuickQuestion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** 자주 묻는 질문 버튼 (#40) — 버튼이 의도한 경로(공식 데이터 FACT / AI)로 가는지 고정한다. */
class QuickQuestionsTest {

    private static final Optional<NoticeSummaryTable> TABLE = NoticeSummaryTable.parse(
            "재당첨제한 전매제한 거주의무기간 분양가상한제 택지유형\n없음 6개월 없음 미적용 민간택지\n");

    private static Announcement.AnnouncementBuilder base() {
        return Announcement.builder()
                .externalId("APPLYHOME-1").houseManageNo("1").pblancNo("1").houseName("연제갤러리자이")
                .houseType(HouseType.APT).houseDetailType(HouseDetailType.PRIVATE).regionName("부산")
                .supplyCategory(SupplyCategory.SALE)
                .receptBeginDate(LocalDate.of(2026, 10, 20)).receptEndDate(LocalDate.of(2026, 10, 21))
                .winnerAnnounceDate(LocalDate.of(2026, 10, 27)).moveInYearMonth("202807").totalSupplyCount(459)
                .regulationFlags(RegulationFlags.builder().build());
    }

    private static Announcement apt() {
        Announcement a = base().build();
        a.addUnitType(UnitType.builder().modelNo("01").typeName("084A").topAmount(91560)
                .supplyBreakdown(SupplyBreakdown.builder().newlywed(10).firstTime(5).newborn(20).etc(99).build()).build());
        return a;
    }

    private static List<String> texts(List<QuickQuestion> qs, boolean fact) {
        return qs.stream().filter(q -> q.fact() == fact).map(QuickQuestion::text).toList();
    }

    @Test
    @DisplayName("공식 데이터 버튼은 모두 FactAnswerer 가 답한다 — AI 버튼은 하나도 FACT 로 새지 않는다")
    void buttonsRouteAsLabelled() {
        for (Announcement a : List.of(apt(), lhRental(false), lhRental(true), remainder())) {
            for (QuickQuestion q : QuickQuestions.of(a, TABLE)) {
                assertThat(FactAnswerer.answer(a, TABLE, q.text()).isPresent())
                        .as("%s — %s", a.getHouseName(), q.text()).isEqualTo(q.fact());
            }
        }
    }

    @Test
    @DisplayName("청약홈 분양 — 일정·요약표·특공 상위 2유형(세대 많은 순, 이름 모를 유형 제외), AI 는 계약금·견본주택")
    void applyhomeSale() {
        List<QuickQuestion> qs = QuickQuestions.of(apt(), TABLE);
        assertThat(texts(qs, true)).containsExactly(
                QuickQuestions.WINNER, QuickQuestions.RECEPT, QuickQuestions.RESALE, QuickQuestions.RESIDE,
                "신생아 특별공급은 몇 세대예요?");   // 최대 5개 — 신혼부부·분양가·입주는 밀린다
        assertThat(texts(qs, false)).containsExactly(QuickQuestions.DEPOSIT, QuickQuestions.MODEL_HOUSE);
    }

    @Test
    @DisplayName("값이 없으면 버튼도 없다 — 요약표·발표일 없는 공고는 그 버튼을 빼고 다음 후보로 채운다")
    void skipsMissingValues() {
        Announcement a = base().winnerAnnounceDate(null).build();
        a.addUnitType(UnitType.builder().modelNo("01").typeName("084A").topAmount(91560)
                .supplyBreakdown(SupplyBreakdown.ofPresentTypes(Set.of(SpecialSupplyType.NEWLYWED))).build());
        assertThat(texts(QuickQuestions.of(a, Optional.empty()), true))
                .containsExactly(QuickQuestions.RECEPT, QuickQuestions.PRICE, QuickQuestions.MOVE_IN);
    }

    @Test
    @DisplayName("LH 임대 — 분양가·전매 버튼 없음, 예비입주자 공고는 '어떻게 뽑나요'")
    void lhRentalButtons() {
        assertThat(texts(QuickQuestions.of(lhRental(false), TABLE), false))
                .containsExactly(QuickQuestions.QUALIFY, QuickQuestions.DOCS_WHEN);
        List<QuickQuestion> waitlist = QuickQuestions.of(lhRental(true), TABLE);
        assertThat(texts(waitlist, false)).containsExactly(QuickQuestions.QUALIFY, QuickQuestions.WAITLIST);
        assertThat(texts(waitlist, true)).doesNotContain(QuickQuestions.PRICE, QuickQuestions.RESALE);
    }

    @Test
    @DisplayName("AI 버튼 문구는 검색 eval 질문과 같다 — 검색이 정답 발췌를 찾는지 매 빌드에서 잰다")
    void aiButtonsAreEvalCases() throws IOException {
        Set<String> evalQuestions = NoticeQaRetrievalEvalTest.readCases().stream()
                .map(NoticeQaRetrievalEvalTest.Case::question).collect(Collectors.toSet());
        for (Announcement a : List.of(apt(), lhRental(false), lhRental(true), remainder())) {
            assertThat(evalQuestions).containsAll(texts(QuickQuestions.of(a, TABLE), false));
        }
    }

    private static Announcement lhRental(boolean waitlist) {
        return base().externalId("LH-2901").houseName(waitlist ? "진천 예비입주자 모집" : "진천 10년 공공임대")
                .supplyCategory(SupplyCategory.RENTAL).build();
    }

    private static Announcement remainder() {
        Announcement a = base().houseName("무순위 단지").supplyCategory(SupplyCategory.REMAINDER).build();
        a.addUnitType(UnitType.builder().modelNo("01").typeName("084A").topAmount(91560)
                .supplyBreakdown(SupplyBreakdown.builder().build()).build());
        return a;
    }
}
