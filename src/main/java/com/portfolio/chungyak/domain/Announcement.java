package com.portfolio.chungyak.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * 분양 공고.
 *
 * 청약홈은 주택관리번호(HOUSE_MANAGE_NO)와 공고번호(PBLANC_NO)를 함께 써서
 * 한 공고를 식별한다. 실데이터에서 두 값이 같은 경우가 대부분이지만
 * 명세상 별개 필드이므로 둘 다 보관하고, 대체키는 소스+두 값으로 만든다.
 */
@Entity
@Table(
    name = "announcement",
    uniqueConstraints = @UniqueConstraint(name = "uk_announcement_external", columnNames = "external_id"),
    indexes = {
        @Index(name = "idx_announcement_recept", columnList = "receptBeginDate, receptEndDate"),
        @Index(name = "idx_announcement_region", columnList = "regionName")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Announcement {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 소스 prefix + 주택관리번호 + 공고번호 */
    @Column(name = "external_id", nullable = false, length = 100)
    private String externalId;

    @Column(nullable = false, length = 20)
    private String houseManageNo;

    @Column(nullable = false, length = 20)
    private String pblancNo;

    @Column(nullable = false, length = 300)
    private String houseName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private HouseType houseType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HouseDetailType houseDetailType;

    /** 공급지역명. 주소 문자열보다 이쪽이 깔끔해서 필터링 기준으로 쓴다. */
    @Column(length = 50)
    private String regionName;

    @Column(length = 20)
    private String regionCode;

    @Column(length = 500)
    private String address;

    @Column(length = 10)
    private String zipCode;

    /** 총 공급세대수 */
    private Integer totalSupplyCount;

    /** 모집공고일 */
    private LocalDate noticeDate;

    /** 청약접수 시작/종료 */
    private LocalDate receptBeginDate;
    private LocalDate receptEndDate;

    /** 특별공급 접수 시작/종료. 없는 공고가 많아 null 허용. */
    private LocalDate specialReceptBeginDate;
    private LocalDate specialReceptEndDate;

    /** 당첨자발표일 */
    private LocalDate winnerAnnounceDate;

    @Embedded
    private RegulationFlags regulationFlags;

    @Column(length = 500)
    private String noticeUrl;

    @Column(length = 300)
    private String homepageUrl;

    @Column(length = 50)
    private String inquiryTel;

    @Column(length = 200)
    private String developerName;

    @Column(length = 300)
    private String constructorName;

    /** 입주예정월 (YYYYMM) */
    @Column(length = 10)
    private String moveInYearMonth;

    @Builder.Default
    @OneToMany(mappedBy = "announcement", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UnitType> unitTypes = new ArrayList<>();

    public void addUnitType(UnitType unitType) {
        this.unitTypes.add(unitType);
        unitType.assignAnnouncement(this);
    }

    /**
     * 이 공고의 어떤 주택형에도 배정이 없는 특별공급 유형은 뺀, 표에 보여줄 열 목록.
     * 주택형별 특별공급 세대수 표에서 아홉 열이 다 늘어서면 대부분 0이라 정보 밀도가 떨어진다 —
     * "0 세대는 물량이 없다는 뜻"이라는 안내는 그대로 두되, 애초에 이 공고에 전혀 없는
     * 유형(예: 청년은 공공주택에만 배정)은 열 자체를 만들지 않는다.
     * {@link EnumSet} 순회 순서 = enum 선언 순서라 기존 컬럼 순서와 같다.
     */
    public List<SpecialSupplyType> visibleSupplyTypes() {
        EnumSet<SpecialSupplyType> visible = EnumSet.noneOf(SpecialSupplyType.class);
        for (UnitType u : unitTypes) {
            if (u.getSupplyBreakdown() == null) continue;
            for (SpecialSupplyType type : SpecialSupplyType.values()) {
                if (u.getSupplyBreakdown().countOf(type) > 0) visible.add(type);
            }
        }
        return new ArrayList<>(visible);
    }

    /** 재수집 시 변경분만 반영 */
    public void updateFromExternal(String houseName, String regionName, String address,
                                   Integer totalSupplyCount, LocalDate receptBeginDate,
                                   LocalDate receptEndDate, LocalDate winnerAnnounceDate,
                                   RegulationFlags regulationFlags) {
        this.houseName = houseName;
        this.regionName = regionName;
        this.address = address;
        this.totalSupplyCount = totalSupplyCount;
        this.receptBeginDate = receptBeginDate;
        this.receptEndDate = receptEndDate;
        this.winnerAnnounceDate = winnerAnnounceDate;
        if (regulationFlags != null) this.regulationFlags = regulationFlags;
    }

    /** 접수 진행 중인지 */
    public boolean isOpen(LocalDate today) {
        return receptBeginDate != null && receptEndDate != null
                && !today.isBefore(receptBeginDate) && !today.isAfter(receptEndDate);
    }

    /** 접수가 끝났는지 */
    public boolean isClosed(LocalDate today) {
        return receptEndDate != null && today.isAfter(receptEndDate);
    }
}
