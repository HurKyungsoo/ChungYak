package com.portfolio.chungyak.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 특별공급 판정을 하면 안 되는 공고 — 문장은 2026-11-10 LH 공고문 원문 그대로(줄바꿈 포함).
 * 운영 공고 23건에서 사람이 읽은 분류와 같게 나오는 것을 확인했다(임대 추가모집 3건·신혼희망타운 잔여세대 1건만 판정 유지).
 */
class NoticeWaiversTest {

    @Test
    @DisplayName("공고문이 소득·자산을 불문한다고 밝히면 참 — 여러 표기")
    void detectsWaiver() {
        // 고양장항 신혼희망타운 잔여세대 추가입주자모집 / 고양지축 B-1 공공분양 잔여세대 추가입주자모집
        assertThat(NoticeWaivers.waivesIncomeAndAssets(
                "■ 금회 추가모집은 청약저축 가입여부, 소득·자산 요건, 과거당첨사실 여부를 불문하며, 당첨 및 계약체결 시에도")).isTrue();
        assertThat(NoticeWaivers.waivesIncomeAndAssets(
                "■ 금회 추가모집은 입주자저축 가입여부, 소득 및 자산요건,\n  과거당첨 여부를 불문하며")).isTrue();
        // 인천가정2 B2 공공분양 잔여세대
        assertThat(NoticeWaivers.waivesIncomeAndAssets(
                "■ 거주지역, 청약저축 가입여부, 과거당첨 사실여부, 주택소유여부, 소득 및 자산 요건을 불문하며")).isTrue();
        // 울산다운2 A-9 신혼희망타운 선착순 — 사이에 다른 요건이 길게 끼어 있다
        assertThat(NoticeWaivers.waivesIncomeAndAssets(
                "다만, 소득 및 자산요건, 거주지역, 신청자의 청약저축 가입여부, 과거당첨사실여부를 불문합니다.")).isTrue();
        assertThat(NoticeWaivers.waivesIncomeAndAssets(
                "• 입주자저축 가입여부, 과거당첨사실여부, 소득·자산기준 충족여부는 불문하며")).isTrue();
    }

    @Test
    @DisplayName("소득·자산을 보라는 안내나 부정 표현은 거짓 — 같은 제목 형식의 신혼희망타운 잔여세대가 그렇다")
    void ignoresRequirementMentions() {
        // 남양주왕숙 A-2 신혼희망타운 잔여세대 추가입주자모집 — 소득·자산을 그대로 본다
        assertThat(NoticeWaivers.waivesIncomeAndAssets(
                "본 공고문의 신청자격(무주택세대구성원여부, 거주지역, 재당첨제한여부, 주택소유여부 및 소득·자산 등), "
                        + "유의사항 등을 사전에 정확하게 확인하시기 바라며")).isFalse();
        assertThat(NoticeWaivers.waivesIncomeAndAssets("소득·자산 요건은 불문하지 않습니다")).isFalse();
        assertThat(NoticeWaivers.waivesIncomeAndAssets(null)).isFalse();
    }

    @Test
    @DisplayName("LH 일반매각·선착순 제목")
    void openSaleTitle() {
        assertThat(NoticeWaivers.isOpenSaleTitle("광주전남혁신 빛가람LH1단지 잔여세대 일반매각 공고(상시, 선착순 동호지정)")).isTrue();
        assertThat(NoticeWaivers.isOpenSaleTitle("울산다운2 A-9 신혼희망타운 선착순 동호지정 공고")).isTrue();
        assertThat(NoticeWaivers.isOpenSaleTitle("남양주왕숙 A-2블록 신혼희망타운[공공분양] 잔여세대 추가입주자모집공고")).isFalse();
    }

    @Test
    @DisplayName("판정 대상: 무순위·임대·LH 일반매각·선착순·소득자산 불문 공고는 빠지고, 청약홈 공고 제목의 '선착순'은 보지 않는다")
    void eligibilityTarget() {
        Announcement lhSale = Announcement.builder().externalId("LH-1").houseName("중동 경남아너스빌 잔여세대 일반매각 공고(선착순)").build();
        Announcement applyhome = Announcement.builder().externalId("2026000001").houseName("○○ 선착순 분양").build();
        Announcement waived = Announcement.builder().externalId("LH-2").houseName("고양지축 B-1블록 공공분양주택 잔여세대 추가입주자모집공고").build();
        waived.markRequirementsWaived(true);
        Announcement remainder = Announcement.builder().externalId("2026000002").houseName("t")
                .supplyCategory(SupplyCategory.REMAINDER).build();

        assertThat(lhSale.notJudgedReason()).isEqualTo(Announcement.NotJudgedReason.OPEN_SALE);
        assertThat(applyhome.isEligibilityTarget()).isTrue();
        assertThat(waived.notJudgedReason()).isEqualTo(Announcement.NotJudgedReason.REQUIREMENTS_WAIVED);
        assertThat(remainder.notJudgedReason()).isEqualTo(Announcement.NotJudgedReason.REMAINDER);
        Announcement rental = Announcement.builder().externalId("LH-4").houseName("광주두암4 영구임대주택 예비입주자모집")
                .supplyCategory(SupplyCategory.RENTAL).supplyTypeName("영구임대").build();
        Announcement hopeRental = Announcement.builder().externalId("LH-5").houseName("구리갈매역세권 A-1BL 신혼희망타운 행복주택")
                .supplyCategory(SupplyCategory.HOPE_TOWN).supplyTypeName("행복주택(신혼희망)").build();
        Announcement hopeSale = Announcement.builder().externalId("LH-6").houseName("안산신길2 A-6블록 신혼희망타운(공공분양)")
                .supplyCategory(SupplyCategory.HOPE_TOWN).supplyTypeName("공공분양(신혼희망)").build();
        assertThat(rental.notJudgedReason()).isEqualTo(Announcement.NotJudgedReason.RENTAL);
        assertThat(hopeRental.notJudgedReason()).isEqualTo(Announcement.NotJudgedReason.RENTAL);   // 상위 구분은 신혼희망이지만 임대
        assertThat(hopeSale.isEligibilityTarget()).isTrue();
        Announcement unchecked = Announcement.builder().externalId("LH-3").houseName("남양주왕숙 A-2블록 신혼희망타운 잔여세대 추가입주자모집공고").build();
        assertThat(unchecked.isEligibilityTarget()).isTrue();   // 공고문을 아직 못 봤으면(null) 예전처럼 판정한다
    }
}
