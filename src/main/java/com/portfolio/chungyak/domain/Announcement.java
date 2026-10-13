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

    /** 분양/임대/신혼희망타운 — 목록 탭·배지용. 판정에는 쓰지 않는다. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private SupplyCategory supplyCategory;

    /** LH 세부 공급유형명 (AIS_TP_CD_NM: 국민임대·행복주택(신혼희망)·매입임대 …). 청약홈은 null. */
    @Column(length = 30)
    private String supplyTypeName;

    /** 입주자모집공고문 PDF 내려받기 링크 (LH 상세 dsAhflInfo). 없으면 noticeUrl 로 안내한다. */
    @Column(length = 500)
    private String noticePdfUrl;

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

    /**
     * 재수집 시 변경분만 반영.
     *
     * 주소·총 세대수·접수 시작일·당첨자 발표일·공고문 PDF 는 <b>null 이면 기존 값을 유지</b>한다.
     * LH 는 이 값들을 목록이 아니라 상세 조회로만 채우는데, 기존 공고의 재수집은 목록만 받으므로
     * (상세는 신규·백필 때만 호출) 그대로 대입하면 채워 둔 값이 매일 null 로 지워진다.
     * null = "원천이 이번엔 안 줬다"이지 "값이 없어졌다"가 아니다.
     */
    public void updateFromExternal(String houseName, String regionName, String address,
                                   Integer totalSupplyCount, LocalDate receptBeginDate,
                                   LocalDate receptEndDate, LocalDate winnerAnnounceDate,
                                   RegulationFlags regulationFlags,
                                   SupplyCategory supplyCategory, String supplyTypeName,
                                   String noticePdfUrl) {
        this.houseName = houseName;
        this.regionName = regionName;
        this.receptEndDate = receptEndDate;
        if (address != null) this.address = address;
        if (totalSupplyCount != null) this.totalSupplyCount = totalSupplyCount;
        if (receptBeginDate != null) this.receptBeginDate = receptBeginDate;
        if (winnerAnnounceDate != null) this.winnerAnnounceDate = winnerAnnounceDate;
        if (regulationFlags != null) this.regulationFlags = regulationFlags;
        if (supplyCategory != null) this.supplyCategory = supplyCategory;
        if (supplyTypeName != null) this.supplyTypeName = supplyTypeName;
        if (noticePdfUrl != null) this.noticePdfUrl = noticePdfUrl;
        fillTotalSupplyCountFromUnitTypes();
    }

    /**
     * 원천이 총 공급세대수를 주지 않으면(LH) 주택형 세대수 합계로 채운다.
     * LH 목록 응답의 {@code ALL_CNT} 는 세대수가 아니라 조회 전체 건수다(2026-10-12 라이브 확인 —
     * 페이지가 달라도 모든 행이 같은 값) — 그래서 원천 값이 아니라 이 합계를 쓴다.
     * 합계가 0 이면(예비입주자 모집 등 주택형이 없거나 세대수 0) 모른다는 뜻으로 null 을 유지한다.
     * 기존 공고는 재수집 때 주택형을 다시 받지 않지만 이미 붙어 있는 주택형으로 채워진다.
     */
    public void fillTotalSupplyCountFromUnitTypes() {
        if (totalSupplyCount != null) return;
        int sum = unitTypes.stream().mapToInt(UnitType::totalSupplyCount).sum();
        if (sum > 0) this.totalSupplyCount = sum;
    }

    /** 무순위·잔여세대 공고인지 — {@link SupplyCategory#REMAINDER} */
    public boolean isRemainder() {
        return supplyCategory == SupplyCategory.REMAINDER;
    }

    /**
     * 특별공급 자격 판정(규칙 엔진) 대상인지. 무순위·잔여세대는 특별공급 구조가 없어 엔진에 넣으면
     * "신청 가능한 유형 없음" 같은 틀린 결론이 나온다 — 판정·매칭·알림·목록 배지에서 모두 뺀다.
     */
    public boolean isEligibilityTarget() {
        return !isRemainder();
    }

    /** LH 공고인지 — 청약홈과 화면 구성이 다르다(규제·특공 세대수 정보가 원천에 없다). */
    public boolean isLh() {
        return externalId != null && externalId.startsWith("LH-");
    }

    /**
     * 예비입주자(대기자) 모집인지. 당장 입주할 세대를 뽑는 게 아니라 빈 집이 날 때 순번대로
     * 입주할 대기자를 모은다. LH 는 이 구분을 코드로 주지 않지만 제목에 일관되게 들어간다
     * (2026-10-12 라이브: 주택 공고 313건 중 239건).
     */
    public boolean isWaitlist() {
        return houseName != null && houseName.contains("예비입주자");
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
