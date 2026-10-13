package com.portfolio.chungyak.service;

import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.domain.SpecialSupplyType;
import com.portfolio.chungyak.external.ApplyhomeClient;
import com.portfolio.chungyak.external.SpecialSupplyApplication;
import com.portfolio.chungyak.external.SpecialSupplyApplication.Count;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 특공 신청현황 합산·캐시 (#33) — 판정과 무관한 참고 정보. */
class SpecialSupplyApplicationServiceTest {

    @Test
    @DisplayName("주택형별 행을 유형 단위로 합산한다")
    void sumsAcrossUnitTypes() {
        var summary = SpecialSupplyApplicationService.summarize(List.of(
                new SpecialSupplyApplication("051B", Map.of(SpecialSupplyType.FIRST_TIME, new Count(1, 2950))),
                new SpecialSupplyApplication("059A", Map.of(SpecialSupplyType.FIRST_TIME, new Count(2, 150),
                        SpecialSupplyType.MULTI_CHILD, new Count(1, 5)))));

        assertThat(summary.byTypeName()).containsEntry("FIRST_TIME", new Count(3, 3100))
                .containsEntry("MULTI_CHILD", new Count(1, 5));
        assertThat(SpecialSupplyApplicationService.summarize(List.of()).isEmpty()).isTrue();
    }

    @Test
    @DisplayName("같은 공고는 캐시에서, LH 공고는 조회하지 않는다(이 API 범위 밖)")
    void cachesAndSkipsLh() {
        ApplyhomeClient client = mock(ApplyhomeClient.class);
        when(client.fetchSpecialSupplyApplications(any())).thenReturn(List.of(
                new SpecialSupplyApplication("051B", Map.of(SpecialSupplyType.NEWLYWED, new Count(1, 221)))));
        SpecialSupplyApplicationService service = new SpecialSupplyApplicationService(client);

        Announcement apt = Announcement.builder().externalId("2026000374").houseManageNo("2026000374").build();
        service.forAnnouncement(apt);
        assertThat(service.forAnnouncement(apt).byTypeName()).containsKey("NEWLYWED");
        verify(client, times(1)).fetchSpecialSupplyApplications("2026000374");

        Announcement lh = Announcement.builder().externalId("LH-1").houseManageNo("X").build();
        assertThat(service.forAnnouncement(lh).isEmpty()).isTrue();
        verify(client, never()).fetchSpecialSupplyApplications("X");
    }
}
