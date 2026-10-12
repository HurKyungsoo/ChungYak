package com.portfolio.chungyak.web.view;

import com.portfolio.chungyak.web.form.EligibilityForm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProfileChipsTest {

    @Test
    @DisplayName("입력한 값만 칩으로 — 금액은 억·만원")
    void chipsFromFilledFields() {
        EligibilityForm f = new EligibilityForm();
        f.setMarried(true);
        f.setMonthsSinceMarriage(52);
        f.setHasNewborn(true);
        f.setHouseless(true);
        f.setAccountMonths(30);
        f.setHouseholdSize(3);
        f.setMonthlyHouseholdIncome(5_000_000);
        f.setTotalAssets(250_000_000L);

        assertThat(ProfileChips.of(f)).containsExactly(
                "혼인 52개월", "2세 이하 자녀", "무주택", "통장 30개월", "3인 가구", "월소득 500만원", "총자산 2억 5,000만원");
    }

    @Test
    @DisplayName("비운 칸은 칩을 만들지 않는다 — 무주택 여부만은 체크박스라 늘 나온다")
    void emptyFieldsMakeNoChips() {
        assertThat(ProfileChips.of(new EligibilityForm())).containsExactly("주택 소유");
    }
}
