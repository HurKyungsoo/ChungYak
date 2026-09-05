package com.portfolio.chungyak.external;

import com.portfolio.chungyak.domain.HouseDetailType;
import com.portfolio.chungyak.domain.HouseType;
import com.portfolio.chungyak.domain.SpecialSupplyType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LH 파서 검증. 픽스처는 2026-09-04 라이브 응답에서 뽑았다.
 *
 * 활용신청 승인 전이라 통합 호출은 못 하고, 응답 봉투/필드 파싱만 검증한다:
 *  - 목록 봉투 [{dsSch},{dsList}] + 재조회 코드 → providerParams
 *  - 상세 dsSplScdl → 특별공급 유형 집합 (countsKnown=false)
 *  - 공급 dsList01~04 → 주택형(필드명 그룹 차이 흡수)
 */
class LhClientTest {

    private final PublicDataParser parser = new PublicDataParser();
    private final LhSpecialSupplyMapper mapper = new LhSpecialSupplyMapper();

    private LhClient client(PublicDataProperties properties) {
        return new LhClient(null, properties, parser, mapper, new PdfNoticeExtractor());
    }

    private PublicDataProperties props(boolean lhEnabled) {
        PublicDataProperties p = new PublicDataProperties();
        p.getLh().setEnabled(lhEnabled);
        return p;
    }

    @Nested
    @DisplayName("parseAnnouncements — 목록")
    class ListParsing {

        @Test
        @DisplayName("실제 봉투 [{dsSch},{dsList}] 파싱 + 재조회 코드를 providerParams 로")
        void parsesRealEnvelope() throws Exception {
            String json = """
                [
                  {"dsSch":[{"PG_SZ":"3","PAGE":"1"}]},
                  {"dsList":[
                    {
                      "PAN_ID":"0000061168",
                      "PAN_NM":"[정정공고]양주회천 A-26BL 공공분양주택 입주자모집공고",
                      "PAN_NT_ST_DT":"2026.09.03",
                      "CLSG_DT":"2026.09.17",
                      "CNP_CD_NM":"경기도",
                      "AIS_TP_CD":"05","UPP_AIS_TP_CD":"05",
                      "SPL_INF_TP_CD":"050","CCR_CNNT_SYS_DS_CD":"02",
                      "DTL_URL":"https://apply.lh.or.kr/lhapply/apply/wt/wrtanc/selectWrtancInfo.do?panId=0000061168&ccrCnntSysDsCd=02"
                    }
                  ]}
                ]
                """;

            List<ExternalAnnouncement> result =
                    client(props(true)).parseAnnouncements(json.getBytes(StandardCharsets.UTF_8));

            assertThat(result).hasSize(1);
            ExternalAnnouncement a = result.get(0);
            assertThat(a.getExternalId()).isEqualTo("LH-0000061168");
            assertThat(a.getHouseManageNo()).isEqualTo("02");
            assertThat(a.getRegionName()).isEqualTo("경기도");
            assertThat(a.getNoticeDate()).isEqualTo(LocalDate.of(2026, 9, 3));
            assertThat(a.getReceptEndDate()).isEqualTo(LocalDate.of(2026, 9, 17));
            assertThat(a.getHouseDetailType()).isEqualTo(HouseDetailType.PUBLIC);   // UPP_AIS_TP_CD=05 분양주택
            assertThat(a.getHouseType()).isEqualTo(HouseType.UNKNOWN);   // 05 는 신혼희망타운(39)이 아니다
            // LH 는 규제지역 정보를 안 주지만 임베디드 값은 null 이면 안 된다(저장 시 NOT NULL 위반)
            assertThat(a.getRegulationFlags()).isNotNull();
            assertThat(a.getRegulationFlags().isRegulatedArea()).isFalse();
            assertThat(a.getProviderParams())
                    .containsEntry("SPL_INF_TP_CD", "050")
                    .containsEntry("CCR_CNNT_SYS_DS_CD", "02")
                    .containsEntry("UPP_AIS_TP_CD", "05")
                    .containsEntry("AIS_TP_CD", "05");
        }

        @Test
        @DisplayName("토지(01)·상가는 주택이 아니라 houseDetailType=UNKNOWN")
        void landIsNotHousing() throws Exception {
            String json = """
                [{"dsList":[{"PAN_ID":"BN-1","PAN_NM":"부산 명지 토지","UPP_AIS_TP_CD":"01","SPL_INF_TP_CD":"010"}]}]
                """;
            List<ExternalAnnouncement> result =
                    client(props(true)).parseAnnouncements(json.getBytes(StandardCharsets.UTF_8));
            assertThat(result.get(0).getHouseDetailType()).isEqualTo(HouseDetailType.UNKNOWN);
        }

        @Test
        @DisplayName("UPP_AIS_TP_CD=39 는 신혼희망타운")
        void hopeTownIsDetectedByUppAisTpCd() throws Exception {
            String json = """
                [{"dsList":[{"PAN_ID":"HT-1","PAN_NM":"성남복정2 A1블록 신혼희망타운",
                             "UPP_AIS_TP_CD":"39","SPL_INF_TP_CD":"390"}]}]
                """;
            List<ExternalAnnouncement> result =
                    client(props(true)).parseAnnouncements(json.getBytes(StandardCharsets.UTF_8));
            ExternalAnnouncement a = result.get(0);
            assertThat(a.getHouseType()).isEqualTo(HouseType.NEWLYWED_HOPE_TOWN);
            assertThat(a.getHouseDetailType()).isEqualTo(HouseDetailType.PUBLIC);   // 신혼희망타운도 주택은 주택
        }

        @Test
        @DisplayName("헤더만 오거나 빈 본문이면 빈 리스트")
        void emptyCases() throws Exception {
            assertThat(client(props(true)).parseAnnouncements(
                    "[{\"dsSch\":[{\"PAGE\":\"1\"}]}]".getBytes(StandardCharsets.UTF_8))).isEmpty();
            assertThat(client(props(true)).parseAnnouncements(new byte[0])).isEmpty();
            assertThat(client(props(true)).parseAnnouncements(null)).isEmpty();
        }
    }

    @Nested
    @DisplayName("parseOfferedSpecialTypes — 상세 dsSplScdl")
    class DetailParsing {

        @Test
        @DisplayName("분양주택 dsSplScdl 의 청약대상 라벨을 특별공급 유형으로 정규화")
        void parsesSaleHousingTypes() throws Exception {
            String json = """
                [
                  {"dsSch":[{"PAN_ID":"0000061168"}]},
                  {"dsSplScdl":[
                    {"HS_SBSC_ACP_TRG_CD_NM":"다자녀특별(85㎡이하)"},
                    {"HS_SBSC_ACP_TRG_CD_NM":"신혼부부특별"},
                    {"HS_SBSC_ACP_TRG_CD_NM":"생애최초특별"},
                    {"HS_SBSC_ACP_TRG_CD_NM":"노부모부양특별(85㎡이하)"},
                    {"HS_SBSC_ACP_TRG_CD_NM":"기관추천"},
                    {"HS_SBSC_ACP_TRG_CD_NM":"신생아특별"},
                    {"HS_SBSC_ACP_TRG_CD_NM":"일반공급(우선)"},
                    {"HS_SBSC_ACP_TRG_CD_NM":"일반공급(추첨)"}
                  ]}
                ]
                """;

            Set<SpecialSupplyType> types = client(props(true))
                    .parseOfferedSpecialTypes(json.getBytes(StandardCharsets.UTF_8));

            assertThat(types).containsExactlyInAnyOrder(
                    SpecialSupplyType.MULTI_CHILD, SpecialSupplyType.NEWLYWED,
                    SpecialSupplyType.FIRST_TIME, SpecialSupplyType.OLD_PARENTS,
                    SpecialSupplyType.INSTITUTION_RECOMMEND, SpecialSupplyType.NEWBORN);
            // 일반공급은 특별공급이 아니므로 제외
        }

        @Test
        @DisplayName("신혼희망타운 라벨(예비신혼부부/신혼부부)은 우선 NEWLYWED 로 매핑된다 " +
                "(라벨 매퍼는 공고 맥락을 모른다 — 신혼희망타운 재분류는 reclassifyForHopeTown 이 따로 한다)")
        void parsesHopeTownTypes() throws Exception {
            String json = """
                [{"dsSplScdl":[
                  {"HS_SBSC_ACP_TRG_CD_NM":"예비신혼부부"},
                  {"HS_SBSC_ACP_TRG_CD_NM":"신혼부부"}
                ]}]
                """;
            assertThat(client(props(true)).parseOfferedSpecialTypes(json.getBytes(StandardCharsets.UTF_8)))
                    .containsExactly(SpecialSupplyType.NEWLYWED);
        }

        @Test
        @DisplayName("reclassifyForHopeTown — 신혼희망타운 공고면 NEWLYWED 를 NEWLYWED_HOPE_TOWN 으로 바꾼다")
        void reclassifiesNewlywedForHopeTownAnnouncements() {
            Set<SpecialSupplyType> raw = Set.of(SpecialSupplyType.NEWLYWED, SpecialSupplyType.MULTI_CHILD);

            assertThat(LhClient.reclassifyForHopeTown(raw, HouseType.NEWLYWED_HOPE_TOWN))
                    .containsExactlyInAnyOrder(SpecialSupplyType.NEWLYWED_HOPE_TOWN, SpecialSupplyType.MULTI_CHILD);
            // 신혼희망타운이 아니면 손대지 않는다
            assertThat(LhClient.reclassifyForHopeTown(raw, HouseType.UNKNOWN)).isEqualTo(raw);
            assertThat(LhClient.reclassifyForHopeTown(raw, HouseType.APT)).isEqualTo(raw);
            // NEWLYWED 가 없으면 신혼희망타운이어도 바꿀 게 없다
            Set<SpecialSupplyType> noNewlywed = Set.of(SpecialSupplyType.MULTI_CHILD);
            assertThat(LhClient.reclassifyForHopeTown(noNewlywed, HouseType.NEWLYWED_HOPE_TOWN)).isEqualTo(noNewlywed);
        }

        @Test
        @DisplayName("토지(dsSplScdl01/02)는 특별공급 유형 없음")
        void landHasNoSpecialTypes() throws Exception {
            String json = """
                [{"dsSplScdl01":[{"RNK":"1"}]},{"dsSplScdl02":[{"RNK":"1"}]}]
                """;
            assertThat(client(props(true)).parseOfferedSpecialTypes(json.getBytes(StandardCharsets.UTF_8)))
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("parseSupplyRows — 공급 dsList01~04")
    class SupplyParsing {

        @Test
        @DisplayName("분양주택 dsList01 을 주택형 행으로")
        void parsesSaleRows() throws Exception {
            String json = """
                [
                  {"dsSch":[{"PAN_ID":"0000061168"}]},
                  {
                    "dsList01Nm":[{"HTY_NM":"주택형"}],
                    "dsList02":[],
                    "dsList01":[
                      {"HTY_NM":"59.7400A","RSDN_DDO_AR":"59.74","SPL_AR":"82.6719","SIL_HSH_CNT":"262","TOT_HSH_CNT":"262","SIL_AMT":"353694000"},
                      {"HTY_NM":"84.8600A","RSDN_DDO_AR":"84.86","SPL_AR":"117.4346","SIL_HSH_CNT":"230","TOT_HSH_CNT":"230","SIL_AMT":"487731000"}
                    ],
                    "resHeader":[{"SS_CODE":"Y"}]
                  }
                ]
                """;

            List<com.fasterxml.jackson.databind.JsonNode> rows =
                    client(props(true)).parseSupplyRows(json.getBytes(StandardCharsets.UTF_8));

            assertThat(rows).hasSize(2);
            assertThat(rows.get(0).get("HTY_NM").asText()).isEqualTo("59.7400A");
        }

        @Test
        @DisplayName("행복주택은 필드명이 달라도(HTY_NNA/NOW_HSH_CNT) 흡수")
        void parsesHappyHouseRows() throws Exception {
            String json = """
                [{"dsList01":[
                  {"HTY_NNA":"25A(대학생,청년)","DDO_AR":"25.01","SPL_AR":"38.5041","NOW_HSH_CNT":"48","HSH_CNT":"242"}
                ]}]
                """;
            List<com.fasterxml.jackson.databind.JsonNode> rows =
                    client(props(true)).parseSupplyRows(json.getBytes(StandardCharsets.UTF_8));
            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).get("HTY_NNA").asText()).contains("청년");
        }
    }

    @Test
    @DisplayName("lh.enabled=false 면 호출 없이 빈 리스트 (RestClient=null 이어도 NPE 없음)")
    void disabledReturnsEmptyWithoutCall() {
        LhClient disabled = client(props(false));

        assertThat(disabled.fetchAnnouncements(1)).isEmpty();
        assertThat(disabled.fetchUnitTypes(ExternalAnnouncement.builder()
                .externalId("LH-1").houseName("x").pblancNo("1").build())).isEmpty();
        assertThat(disabled.fetchNoticeContent(ExternalAnnouncement.builder()
                .externalId("LH-1").houseName("x").pblancNo("1").build())).isEmpty();
    }

    @Nested
    @DisplayName("parseNoticeContent — 상세 dsEtcInfo.PAN_DTL_CTS")
    class NoticeContentParsing {

        private final LhClient c = client(props(true));

        @Test
        @DisplayName("PAN_DTL_CTS 키를 찾아 HTML 을 벗겨 평문으로")
        void extractsAndStripsHtml() throws Exception {
            String json = """
                [{"dsEtcInfo":[{"PAN_DTL_CTS":"<p>입주자모집공고</p><br/>1. 신청자격: 무주택세대구성원<br/>2. 잔여세대 신청은 마감일 이후 별도 안내합니다. 자세한 사항은 공고문을 확인하세요.&nbsp;문의: 1600-1004"}]}]
                """;
            var content = c.parseNoticeContent(json.getBytes(StandardCharsets.UTF_8));

            assertThat(content).isPresent();
            assertThat(content.get()).doesNotContain("<p>").doesNotContain("<br")
                    .contains("입주자모집공고").contains("잔여세대").contains("1600-1004");
        }

        @Test
        @DisplayName("PAN_DTL_CTS 가 없으면 응답에서 가장 긴 텍스트를 쓴다")
        void fallsBackToLongestText() throws Exception {
            String body = "가나다라마바사아자차카타파하 ".repeat(20);
            String json = "[{\"dsEtcInfo\":[{\"SOME_OTHER_FIELD\":\"" + body + "\",\"SHORT\":\"짧음\"}]}]";
            var content = c.parseNoticeContent(json.getBytes(StandardCharsets.UTF_8));

            assertThat(content).isPresent();
            assertThat(content.get()).contains("가나다라마바사");
        }

        @Test
        @DisplayName("본문이라 할 만한 텍스트가 없으면 empty")
        void emptyWhenNoBody() throws Exception {
            String json = "[{\"dsSplScdl\":[{\"RNK\":\"1\"}]}]";
            assertThat(c.parseNoticeContent(json.getBytes(StandardCharsets.UTF_8))).isEmpty();
            assertThat(c.parseNoticeContent(new byte[0])).isEmpty();
        }
    }

    @Nested
    @DisplayName("parseNoticePdfUrl — dsAhflInfo 첨부에서 공고문 PDF")
    class AttachmentParsing {

        private final LhClient c = client(props(true));

        /** 2026-09-04 실측 응답 형태. */
        private static final String DETAIL = """
            [{"dsAhflInfo":[
              {"AHFL_URL":"https://apply.lh.or.kr/lhapply/lhFile.do?fileid=1","SL_PAN_AHFL_DS_CD_NM":"기타 첨부파일","CMN_AHFL_NM":"(양식)위임장.hwpx"},
              {"AHFL_URL":"https://apply.lh.or.kr/lhapply/lhFile.do?fileid=2","SL_PAN_AHFL_DS_CD_NM":"공고문(hwp)","CMN_AHFL_NM":"입주자모집공고문(최종).hwpx"},
              {"AHFL_URL":"https://apply.lh.or.kr/lhapply/lhFile.do?fileid=3","SL_PAN_AHFL_DS_CD_NM":"공고문(PDF)","CMN_AHFL_NM":"입주자모집공고문(최종).pdf"},
              {"AHFL_URL":"https://apply.lh.or.kr/lhapply/lhFile.do?fileid=4","SL_PAN_AHFL_DS_CD_NM":"기타 첨부파일","CMN_AHFL_NM":"팸플릿.pdf"},
              {"AHFL_URL":"https://apply.lh.or.kr/lhapply/lhFile.do?fileid=5","SL_PAN_AHFL_DS_CD_NM":"기타 첨부파일","CMN_AHFL_NM":"잔여세대동호표.pdf"}
            ]}]
            """;

        @Test
        @DisplayName("공고문 PDF 첨부의 다운로드 URL 을 고른다 (hwp·팸플릿·동호표 아님)")
        void picksNoticePdf() {
            assertThat(c.parseNoticePdfUrl(DETAIL.getBytes(StandardCharsets.UTF_8)))
                    .contains("https://apply.lh.or.kr/lhapply/lhFile.do?fileid=3");
        }

        @Test
        @DisplayName("공고문 PDF 첨부가 없으면 empty")
        void emptyWhenNoNoticePdf() {
            String json = "[{\"dsAhflInfo\":[{\"AHFL_URL\":\"u\",\"SL_PAN_AHFL_DS_CD_NM\":\"공고문(hwp)\",\"CMN_AHFL_NM\":\"공고문.hwpx\"}]}]";
            assertThat(c.parseNoticePdfUrl(json.getBytes(StandardCharsets.UTF_8))).isEmpty();
            assertThat(c.parseNoticePdfUrl("[{\"dsSplScdl\":[]}]".getBytes(StandardCharsets.UTF_8))).isEmpty();
            assertThat(c.parseNoticePdfUrl(new byte[0])).isEmpty();
        }
    }
}
