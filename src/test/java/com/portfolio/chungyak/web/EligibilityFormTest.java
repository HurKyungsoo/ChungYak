package com.portfolio.chungyak.web;

import com.portfolio.chungyak.llm.ExtractedProfile;
import com.portfolio.chungyak.rule.ApplicantProfile;
import com.portfolio.chungyak.web.form.EligibilityForm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 폼 -> ApplicantProfile 매핑 검증.
 *
 * 이 변환은 규칙 엔진 바로 앞에 있으므로 결정론적이어야 한다.
 * (계산·판단이 끼면 그 순간 판정이 rule 패키지 밖으로 샌다.)
 */
class EligibilityFormTest {

    @Test
    @DisplayName("입력한 값이 그대로 프로필로 옮겨진다")
    void mapsFieldsVerbatim() {
        EligibilityForm form = new EligibilityForm();
        form.setMarried(true);
        form.setMonthsSinceMarriage(36);
        form.setChildCount(2);
        form.setHasNewborn(true);
        form.setHouseless(true);
        form.setAccountMonths(12);
        form.setEverOwnedHouse(false);
        form.setSupportingOldParents(true);
        form.setHouseholdHead(true);

        ApplicantProfile profile = form.toProfile();

        assertThat(profile.isMarried()).isTrue();
        assertThat(profile.getMonthsSinceMarriage()).isEqualTo(36);
        assertThat(profile.getChildCount()).isEqualTo(2);
        assertThat(profile.isHasNewborn()).isTrue();
        assertThat(profile.isHouseless()).isTrue();
        assertThat(profile.getAccountMonths()).isEqualTo(12);
        assertThat(profile.isEverOwnedHouse()).isFalse();
        assertThat(profile.isSupportingOldParents()).isTrue();
        assertThat(profile.isHouseholdHead()).isTrue();
    }

    @Test
    @DisplayName("비워 둔 숫자 항목은 null 로 남는다 — 판정 엔진이 '입력 부족'으로 처리한다")
    void blankNumbersStayNull() {
        EligibilityForm form = new EligibilityForm();
        form.setMarried(true);

        ApplicantProfile profile = form.toProfile();

        assertThat(profile.getMonthsSinceMarriage()).isNull();
        assertThat(profile.getAccountMonths()).isNull();
        assertThat(profile.getChildCount()).isZero();
    }

    @Test
    @DisplayName("음수 자녀 수는 0 으로 보정한다")
    void negativeChildCountClampedToZero() {
        EligibilityForm form = new EligibilityForm();
        form.setChildCount(-3);

        assertThat(form.toProfile().getChildCount()).isZero();
    }

    @Test
    @DisplayName("소득·자산·거주기간 필드도 그대로 프로필로 옮겨진다")
    void mapsIncomeAndAssetFields() {
        EligibilityForm form = new EligibilityForm();
        form.setMonthlyHouseholdIncome(6_000_000);
        form.setHouseholdSize(3);
        form.setDualIncome(true);
        form.setTotalAssets(300_000_000L);
        form.setCarValue(20_000_000);
        form.setResidenceMonthsInRegion(24);

        ApplicantProfile profile = form.toProfile();

        assertThat(profile.getMonthlyHouseholdIncome()).isEqualTo(6_000_000);
        assertThat(profile.getHouseholdSize()).isEqualTo(3);
        assertThat(profile.isDualIncome()).isTrue();
        assertThat(profile.getTotalAssets()).isEqualTo(300_000_000L);
        assertThat(profile.getCarValue()).isEqualTo(20_000_000);
        assertThat(profile.getResidenceMonthsInRegion()).isEqualTo(24);
    }

    @Test
    @DisplayName("재당첨 관련 필드도 그대로 프로필로 옮겨진다")
    void mapsReWinFields() {
        EligibilityForm form = new EligibilityForm();
        form.setEverWonSpecialSupply(true);
        form.setMonthsSinceLastWin(40);
        form.setPastWinInSpeculationArea(true);

        ApplicantProfile profile = form.toProfile();

        assertThat(profile.isEverWonSpecialSupply()).isTrue();
        assertThat(profile.getMonthsSinceLastWin()).isEqualTo(40);
        assertThat(profile.isPastWinInSpeculationArea()).isTrue();
    }

    @Test
    @DisplayName("소득·자산을 비워 두면 null — 판정에서 '해당 요건 확인 불가'로 처리된다")
    void blankIncomeStaysNull() {
        ApplicantProfile profile = new EligibilityForm().toProfile();

        assertThat(profile.getMonthlyHouseholdIncome()).isNull();
        assertThat(profile.getHouseholdSize()).isNull();
        assertThat(profile.getTotalAssets()).isNull();
        assertThat(profile.getCarValue()).isNull();
    }

    @Test
    @DisplayName("LLM 추출값 중 null 이 아닌 것만 폼에 채운다 — null 필드는 건드리지 않는다")
    void applyExtractedFillsOnlyKnownFields() {
        EligibilityForm form = new EligibilityForm();
        // married/childCount 만 알아냈고 나머지는 모름(null)
        ExtractedProfile extracted = new ExtractedProfile(
                true, null, 2, null, null, null, null, null, null);

        form.applyExtracted(extracted);

        assertThat(form.isMarried()).isTrue();
        assertThat(form.getChildCount()).isEqualTo(2);
        // 채워지지 않은 항목은 기본값 그대로 (사용자가 직접 선택)
        assertThat(form.getMonthsSinceMarriage()).isNull();
        assertThat(form.getAccountMonths()).isNull();
        assertThat(form.isHouseless()).isFalse();
        assertThat(form.isHouseholdHead()).isFalse();
    }

    @Test
    @DisplayName("음수 자녀 수는 추출값에서도 0 으로 보정한다")
    void applyExtractedClampsNegativeChildCount() {
        EligibilityForm form = new EligibilityForm();
        form.applyExtracted(new ExtractedProfile(
                null, null, -1, null, null, null, null, null, null));

        assertThat(form.getChildCount()).isZero();
    }
}
