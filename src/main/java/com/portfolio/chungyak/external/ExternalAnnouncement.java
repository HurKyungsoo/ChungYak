package com.portfolio.chungyak.external;

import com.portfolio.chungyak.domain.HouseDetailType;
import com.portfolio.chungyak.domain.HouseType;
import com.portfolio.chungyak.domain.RegulationFlags;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 소스 중립 공고 모델.
 *
 * 청약홈(odcloud)과 LH(data.go.kr)는 게이트웨이도 응답 구조도 완전히 다르다.
 * 청약홈은 {data:[...], totalCount:n}, LH 는 최상위가 배열이고 그 안에
 * dsSch/dsSplScdl/dsEtcInfo 같은 데이터셋이 나뉘어 담긴다.
 *
 * 그 차이를 각 클라이언트가 흡수하고, 서비스 계층은 이 타입만 안다.
 */
@Getter
@Builder
public class ExternalAnnouncement {

    private String externalId;
    private String houseManageNo;
    private String pblancNo;
    private String houseName;
    private HouseType houseType;
    private HouseDetailType houseDetailType;
    private String regionName;
    private String regionCode;
    private String address;
    private String zipCode;
    private Integer totalSupplyCount;
    private LocalDate noticeDate;
    private LocalDate receptBeginDate;
    private LocalDate receptEndDate;
    private LocalDate specialReceptBeginDate;
    private LocalDate specialReceptEndDate;
    private LocalDate winnerAnnounceDate;
    private RegulationFlags regulationFlags;
    private String noticeUrl;
    private String homepageUrl;
    private String inquiryTel;
    private String developerName;
    private String constructorName;
    private String moveInYearMonth;

    /** LH 소스에서만 채워진다 — 벡터 검색 대상이 될 비정형 텍스트 */
    private String noticeContent;

    @Builder.Default
    private List<ExternalUnitType> unitTypes = new ArrayList<>();

    public boolean isValid() {
        return externalId != null && !externalId.isBlank()
                && houseName != null && !houseName.isBlank()
                && pblancNo != null && !pblancNo.isBlank();
    }
}
