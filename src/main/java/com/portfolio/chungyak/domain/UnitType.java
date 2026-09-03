package com.portfolio.chungyak.domain;

import jakarta.persistence.*;
import lombok.*;

/**
 * 주택형 (평형).
 *
 * 한 공고 안에 여러 주택형이 있고, 특별공급 배정은 주택형마다 다르다.
 * 실데이터 예: "올 뉴 챔피언스시티 1차"는 084.9730A ~ 084.9809E 다섯 타입이고
 * 신혼부부 배정이 타입별로 47/47/80/80/46 세대로 갈린다.
 * 그래서 판정도 공고 단위가 아니라 주택형 단위로 해야 의미가 있다.
 */
@Entity
@Table(
    name = "unit_type",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_unit_type", columnNames = {"announcement_id", "modelNo"}),
    indexes = @Index(name = "idx_unit_type_announcement", columnList = "announcement_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UnitType {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "announcement_id")
    private Announcement announcement;

    /** 모델번호 (01, 02, ...) */
    @Column(nullable = false, length = 10)
    private String modelNo;

    /** 주택형 표기 ("084.9730A") */
    @Column(nullable = false, length = 50)
    private String typeName;

    /** 공급면적 (제곱미터) */
    @Column(length = 30)
    private String supplyArea;

    /** 일반공급 세대수 */
    @Builder.Default
    private int generalSupplyCount = 0;

    /** 특별공급 세대수 합계 (API 가 주는 값 — 개별 합과 다를 수 있어 그대로 보관) */
    @Builder.Default
    private int specialSupplyCount = 0;

    @Embedded
    private SupplyBreakdown supplyBreakdown;

    /** 분양최고금액 (만원). API 가 문자열로 주고 없는 경우도 있어 Integer. */
    private Integer topAmount;

    void assignAnnouncement(Announcement announcement) {
        this.announcement = announcement;
    }

    public int totalSupplyCount() {
        return generalSupplyCount + specialSupplyCount;
    }

    /** 특별공급 비중 (%) — "이 타입은 특공이 유리한가" 판단용 */
    public int specialSupplyRatio() {
        int total = totalSupplyCount();
        return total == 0 ? 0 : specialSupplyCount * 100 / total;
    }
}
