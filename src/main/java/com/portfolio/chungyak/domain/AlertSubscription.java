package com.portfolio.chungyak.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * "새 공고 알림" 구독 — 로그인 없이 이메일 하나로 판정 조건을 저장해 둔다.
 *
 * 저장하는 건 자격 판정 폼과 같은 조건(소득·자산 포함)이다 — 지역·유형만 보는 게 아니라
 * {@link com.portfolio.chungyak.rule.EligibilityEngine} 으로 실제 자격을 매일 다시 판정해서,
 * "이 공고에 조건이 맞는 물량이 있다"가 아니라 "당신이 실제로 신청 가능하다"를 알려준다
 * (CLAUDE.md 절대 규칙 — 이 배치도 규칙 엔진만 쓰고, 알림 문구는 결정론적으로 조립한다. LLM 관여 없음).
 *
 * 로그인이 없는 서비스에서 재산 정보를 이메일에 묶어 저장하는 것이므로:
 *  - 이메일 소유 확인 전엔 {@link Status#PENDING} — 확인 링크를 눌러야 배치 대상이 된다
 *    (모르는 사람 이메일로 가입시켜 스팸처럼 보내는 걸 막는다)
 *  - 구독 해지({@code unsubscribeToken})는 즉시 이 행을 삭제한다 — 남겨둘 이유가 없다
 *  - 관리자 API 를 포함해 이 조건을 다시 보여주는 화면은 없다(쓰기 전용, 배치만 읽는다)
 */
@Entity
@Table(name = "alert_subscription")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class AlertSubscription {

    public enum Status { PENDING, CONFIRMED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 254)
    private String email;

    /** 대상 공고 — 이 공고와 같은 지역·유형의 새 공고만 검사한다(전체 코퍼스를 매일 다 돌리지 않기 위함). */
    @Column(nullable = false)
    private Long referenceAnnouncementId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.PENDING;

    @Column(nullable = false, length = 36, unique = true)
    private String confirmToken;

    @Column(nullable = false, length = 36, unique = true)
    private String unsubscribeToken;

    @Column(nullable = false)
    private Instant createdAt;

    // === 자격 판정 폼과 같은 조건 (EligibilityForm 미러) ===
    private boolean married;
    private Integer monthsSinceMarriage;
    @Builder.Default private int childCount = 0;
    private boolean hasNewborn;
    private boolean hasChildUnderSix;
    private boolean houseless;
    private Integer accountMonths;
    private Integer accountPaymentCount;
    private Integer accountDeposit;
    private boolean everOwnedHouse;
    private boolean supportingOldParents;
    private boolean householdHead;
    private Integer residenceMonthsInRegion;
    private Integer monthlyHouseholdIncome;
    private Integer householdSize;
    private boolean dualIncome;
    private Long totalAssets;
    private Integer carValue;
    private boolean everWonSpecialSupply;
    private Integer monthsSinceLastWin;
    private boolean pastWinInSpeculationArea;

    public static String newToken() {
        return UUID.randomUUID().toString();
    }

    public void confirm() {
        this.status = Status.CONFIRMED;
    }

    public boolean isConfirmed() {
        return status == Status.CONFIRMED;
    }
}
