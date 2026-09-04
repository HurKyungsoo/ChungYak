package com.portfolio.chungyak.external;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "publicdata")
public class PublicDataProperties {

    private String serviceKey;
    private Applyhome applyhome = new Applyhome();
    private Lh lh = new Lh();
    private Sync sync = new Sync();

    /** 수집 상태 모니터링 임계값 (SyncHealthIndicator) */
    @Getter @Setter
    public static class Sync {
        /** 이 건수 미만으로 수집되면 이상으로 본다 (API 키·응답 문제 의심) */
        private int minExpectedRecords = 1000;
        /** 마지막 성공 수집이 이 시간을 넘기면 health 를 DOWN (매일 04시 배치 기준 30) */
        private long staleAfterHours = 30;
    }

    @Getter @Setter
    public static class Applyhome {
        private String baseUrl;
        private String detailPath;
        private String modelPath;
        private int perPage = 100;
        private int maxPages = 30;
    }

    @Getter @Setter
    public static class Lh {
        /** 목록 API 활용신청 승인 전까지 기본 비활성 — 켜기 전엔 LhClient 가 빈 리스트를 반환 */
        private boolean enabled = false;
        private String noticeListUrl;
        private String detailUrl;
        private String supplyUrl;
        private int perPage = 100;
        private int maxPages = 30;
        /** 목록 API 필수 파라미터인 공고게시일 범위를 오늘 기준 몇 달 전부터 볼지 */
        private int lookbackMonths = 6;
    }
}
