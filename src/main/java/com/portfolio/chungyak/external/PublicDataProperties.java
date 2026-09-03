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
        /** 목록 API 활용신청 대기 중이라 기본 비활성 */
        private boolean enabled = false;
        private String noticeListUrl;
        private String detailUrl;
        private String supplyUrl;
    }
}
