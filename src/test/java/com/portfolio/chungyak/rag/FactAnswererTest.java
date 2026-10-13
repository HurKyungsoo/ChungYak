package com.portfolio.chungyak.rag;

import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.domain.HouseDetailType;
import com.portfolio.chungyak.domain.HouseType;
import com.portfolio.chungyak.domain.RegulationFlags;
import com.portfolio.chungyak.domain.SpecialSupplyType;
import com.portfolio.chungyak.domain.SupplyBreakdown;
import com.portfolio.chungyak.domain.UnitType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** 공고문 Q&A 결정론 답변 (#39) — 답할 것과 <b>답하지 말아야 할 것</b>(LLM 경로로 넘길 것)을 함께 고정한다. */
class FactAnswererTest {

    private static final Optional<NoticeSummaryTable> TABLE = NoticeSummaryTable.parse(
            "재당첨제한 전매제한 거주의무기간 분양가상한제 택지유형\n없음 6개월 없음 미적용 민간택지\n");

    private static Announcement apt() {
        Announcement a = Announcement.builder()
                .externalId("APPLYHOME-1").houseManageNo("1").pblancNo("1").houseName("연제갤러리자이")
                .houseType(HouseType.APT).houseDetailType(HouseDetailType.PRIVATE).regionName("부산")
                .specialReceptBeginDate(LocalDate.of(2026, 10, 19)).specialReceptEndDate(LocalDate.of(2026, 10, 19))
                .receptBeginDate(LocalDate.of(2026, 10, 20)).receptEndDate(LocalDate.of(2026, 10, 21))
                .winnerAnnounceDate(LocalDate.of(2026, 10, 27)).moveInYearMonth("202807").totalSupplyCount(459)
                .regulationFlags(RegulationFlags.builder().build()).build();
        a.addUnitType(UnitType.builder().modelNo("01").typeName("084.9587A ").topAmount(91560)
                .supplyBreakdown(SupplyBreakdown.builder().build()).build());
        return a;
    }

    private static Optional<String> ask(String q) {
        return FactAnswerer.answer(apt(), TABLE, q).map(FactAnswerer.Fact::text);
    }

    /** 답해야 하는 질문 — 답 문장(없으면 실패) */
    private static String answered(String q) {
        return ask(q).orElseThrow(() -> new AssertionError("답하지 않음: " + q));
    }

    @Test
    @DisplayName("공고문 요약표 항목 — 전매·재당첨·거주의무·분양가상한제, 여러 개면 모두")
    void summaryTableItems() {
        assertThat(answered("전매제한 기간은?")).contains("전매제한: 6개월");
        assertThat(answered("재당첨 제한 있어요?")).contains("재당첨제한: 없음");
        assertThat(answered("분양가 상한제 적용돼요?")).contains("분양가상한제: 미적용");
        assertThat(answered("전매제한이랑 거주의무 알려줘")).contains("전매제한: 6개월\n거주의무기간: 없음");
        assertThat(FactAnswerer.answer(apt(), TABLE, "전매").get().source()).isEqualTo("공고문 첫 페이지 요약표");
    }

    @Test
    @DisplayName("API 값 — 발표일·입주 예정·접수 일정·공급 세대·주택형별 최고 분양가")
    void apiFacts() {
        assertThat(answered("당첨자 발표일은 언제인가요?")).contains("2026년 10월 27일(화)");
        assertThat(answered("입주 예정일은 언제예요?")).contains("2028년 7월");
        assertThat(answered("청약 접수 기간은?")).contains("특별공급 접수: 2026년 10월 19일(월)")
                .contains("청약 접수: 2026년 10월 20일(화) ~ 2026년 10월 21일(수)");
        assertThat(answered("몇 세대 공급해요?")).contains("총 459세대");
        assertThat(answered("분양가 얼마예요?")).contains("084.9587A: 9억 1,560만원");
    }

    @Test
    @DisplayName("답하지 않고 LLM 으로 넘길 것 — 자격·서류·방법, '입주자 모집공고일', 계약금·보증금, 단서 없는 주제어")
    void doesNotRoute() {
        assertThat(ask("신청 자격이 어떻게 되나요?")).isEmpty();
        assertThat(ask("당첨자 발표 후 제출 서류는?")).isEmpty();
        assertThat(ask("청약 신청 방법 알려줘")).isEmpty();
        assertThat(ask("입주자 모집공고일은 언제인가요?")).isEmpty();
        assertThat(ask("입주지정기간은 언제예요?")).isEmpty();
        assertThat(ask("계약금은 얼마인가요?")).isEmpty();
        assertThat(ask("분양가 대비 계약금 비율은?")).isEmpty();
        assertThat(ask("발표 났어요?")).isEmpty();   // 언제를 묻지 않는다
        assertThat(ask("반려동물 키워도 되나요?")).isEmpty();
    }

    @Test
    @DisplayName("값이 아니라 '무엇이 포함되나'·특정 유형을 묻는 질문은 넘긴다 — #39 로컬 시험·eval 에서 오답으로 답하던 것")
    void doesNotAnswerAdjacentQuestions() {
        assertThat(ask("발코니 확장비가 분양가에 포함돼 있나요?")).isEmpty();
        assertThat(ask("분양가에 취득세 포함인가요?")).isEmpty();
        assertThat(ask("발코니 확장 금액은 분양가랑 별도예요?")).isEmpty();
        assertThat(ask("신혼부부 특별공급은 몇 세대예요?")).isEmpty();
        assertThat(ask("84A 타입은 몇 세대예요?")).isEmpty();
        assertThat(ask("일반공급 세대수는?")).isEmpty();
        // 공고 전체를 묻는 건 그대로 답한다
        assertThat(answered("총 몇 세대예요?")).contains("총 459세대");
    }

    @Test
    @DisplayName("특공 유형별 세대수 — 주택형별 배정을 더한다. LH(세대수 미상)·배정 0 이면 넘긴다")
    void specialSupplyByType() {
        Announcement a = apt();
        a.addUnitType(UnitType.builder().modelNo("02").typeName("084.9052B").topAmount(89160)
                .supplyBreakdown(SupplyBreakdown.builder().newlywed(16).firstTime(7).build()).build());
        a.addUnitType(UnitType.builder().modelNo("03").typeName("084.8780C").topAmount(90250)
                .supplyBreakdown(SupplyBreakdown.builder().newlywed(5).build()).build());

        assertThat(FactAnswerer.answer(a, Optional.empty(), "신혼부부 특별공급은 몇 세대예요?").map(FactAnswerer.Fact::text))
                .contains("신혼부부 특별공급: 총 21세대 (084.9052B 16 · 084.8780C 5)");
        assertThat(FactAnswerer.answer(a, Optional.empty(), "생애최초랑 신혼부부 물량 알려줘").map(FactAnswerer.Fact::text).orElseThrow())
                .contains("신혼부부 특별공급: 총 21세대").contains("생애최초 특별공급: 총 7세대");
        // 배정 0 — 없다고 단정하지 않고 넘긴다
        assertThat(FactAnswerer.answer(a, Optional.empty(), "다자녀 몇 세대예요?")).isEmpty();
        // 신혼희망타운은 청약홈이 유형별 세대수를 주지 않는다
        assertThat(FactAnswerer.answer(a, Optional.empty(), "신혼희망타운 몇 세대예요?")).isEmpty();

        Announcement lh = apt();
        lh.addUnitType(UnitType.builder().modelNo("02").typeName("59A")
                .supplyBreakdown(SupplyBreakdown.ofPresentTypes(Set.of(SpecialSupplyType.NEWLYWED))).build());
        assertThat(FactAnswerer.answer(lh, Optional.empty(), "신혼부부 몇 세대예요?")).isEmpty();
    }

    @Test
    @DisplayName("값이 없으면 단정하지 않고 넘긴다 — 요약표 없는 공고의 전매제한, 발표일 없는 공고")
    void missingValueFallsThrough() {
        assertThat(FactAnswerer.answer(apt(), Optional.empty(), "전매제한 기간은?")).isEmpty();
        Announcement noDate = Announcement.builder().externalId("X").houseName("X")
                .regulationFlags(RegulationFlags.builder().build()).build();
        assertThat(FactAnswerer.answer(noDate, Optional.empty(), "당첨자 발표일은 언제?")).isEmpty();
    }
}
