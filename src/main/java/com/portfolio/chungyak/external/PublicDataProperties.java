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
        /** 무순위·잔여세대 공고·주택형 (같은 서비스·같은 키) — 비우면 수집하지 않는다 */
        private String remainderDetailPath;
        private String remainderModelPath;
        /** 무순위는 최신순이라 앞 몇 페이지만 — 2020년부터 1,700여 건이 쌓여 있다 */
        private int remainderMaxPages = 3;
        /** 공고문 PDF 원문 가져오기(#38, 공식 API 밖) — 끄면 청약홈 웹페이지에 접근하지 않는다 */
        private boolean noticePdfEnabled = true;
        /** 공고 페이지·PDF 요청 사이 최소 간격(ms) */
        private long noticeRequestIntervalMs = 1000;
        /** 특별공급 신청현황 — ApplyhomeInfoCmpetRtSvc/v1/getAPTSpsplyReqstStus (#33, 별도 활용신청 필요) */
        private String specialSupplyStatusUrl;
        /** 당첨가점·경쟁률 (#34, 같은 서비스·같은 키) — 비우면 수집하지 않는다 */
        private String competitionScoreUrl;
        private String competitionRateUrl;
        /** 최신순 — 매일 앞 몇 페이지만 다시 받는다(표가 비어 있으면 전체). 페이지당 행 수 */
        private int competitionSyncPages = 3;
        private int competitionPerPage = 1000;
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
