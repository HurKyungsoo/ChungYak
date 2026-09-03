package com.portfolio.chungyak.web;

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
}
