package com.portfolio.chungyak.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** 당첨가점·경쟁률 응답 파싱 (#34) — 행은 2026-10-14 라이브 응답 모양 그대로. */
class ApplyhomeCompetitionClientTest {

    private final ApplyhomeCompetitionClient client =
            new ApplyhomeCompetitionClient(RestClient.create(), new PublicDataProperties(), new ObjectMapper());

    @Test
    @DisplayName("가점 — 문자열 숫자, \"0\"·빈 값은 0, 필수 필드 없는 행은 스킵")
    void scores() throws Exception {
        String json = """
                {"data":[
                  {"AVRG_SCORE":"27","HOUSE_MANAGE_NO":"2026000454","HOUSE_TY":"084.9890A","LWET_SCORE":"27","MODEL_NO":"01","PBLANC_NO":"2026000454","RESIDE_SECD":"02","RESIDE_SENM":"기타지역","TOP_SCORE":"31"},
                  {"AVRG_SCORE":"0","HOUSE_MANAGE_NO":"2026000454","HOUSE_TY":"084.9890A","LWET_SCORE":"0","MODEL_NO":"01","RESIDE_SECD":"01","TOP_SCORE":""},
                  {"AVRG_SCORE":"10","HOUSE_TY":"084.9890A","LWET_SCORE":"10","MODEL_NO":"01","RESIDE_SECD":"01"}
                ],"totalCount":29932}""";

        var page = client.parseScores(json.getBytes(StandardCharsets.UTF_8));

        assertThat(page.totalCount()).isEqualTo(29932);
        assertThat(page.rows()).hasSize(2);
        assertThat(page.rows().get(0).lowest()).isEqualTo(27);
        assertThat(page.rows().get(0).highest()).isEqualTo(31);
        assertThat(page.rows().get(1).lowest()).isZero();
        assertThat(page.rows().get(1).highest()).isZero();
    }

    @Test
    @DisplayName("경쟁률 — 원문 그대로(미달 \"(△30)\"·\"-\"·쉼표 숫자), 신청 건수는 숫자일 때만")
    void rates() throws Exception {
        String json = """
                {"data":[
                  {"CMPET_RATE":"(△30)","HOUSE_MANAGE_NO":"2026000498","HOUSE_TY":"084.9800A","MODEL_NO":"01","REQ_CNT":"6","RESIDE_SECD":"01","SUBSCRPT_RANK_CODE":1,"SUPLY_HSHLDCO":36},
                  {"CMPET_RATE":"7,474.67","HOUSE_MANAGE_NO":"2026000400","HOUSE_TY":"059.9800A","MODEL_NO":"02","REQ_CNT":"22,424","RESIDE_SECD":"01","SUBSCRPT_RANK_CODE":1,"SUPLY_HSHLDCO":3},
                  {"CMPET_RATE":"-","HOUSE_MANAGE_NO":"2026000400","HOUSE_TY":"059.9800A","MODEL_NO":"02","REQ_CNT":"-","RESIDE_SECD":"01","SUBSCRPT_RANK_CODE":2,"SUPLY_HSHLDCO":3}
                ],"totalCount":55288}""";

        var rows = client.parseRates(json.getBytes(StandardCharsets.UTF_8)).rows();

        assertThat(rows).extracting(CompetitionRecords.Rate::rateText).containsExactly("(△30)", "7,474.67", "-");
        assertThat(rows).extracting(CompetitionRecords.Rate::requests).containsExactly(6, 22424, null);
        assertThat(rows.get(2).rankCode()).isEqualTo(2);
    }

    @Test
    @DisplayName("URL·키가 없으면 꺼져 있고 빈 페이지 — 앱은 그대로 돈다")
    void disabledWithoutConfig() {
        assertThat(client.isEnabled()).isFalse();
        assertThat(client.fetchScores(1, 100).rows()).isEmpty();
    }
}
