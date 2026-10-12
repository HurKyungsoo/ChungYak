package com.portfolio.chungyak.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 추출 결과 검증 보조 — 년·개월 표기, 원문 숫자 대조 경고. 값을 고치지 않고 경고만 한다. */
class ExtractionReadBackTest {

    private static ExtractedProfile profile(Integer marriageMonths, Integer accountMonths) {
        return new ExtractedProfile(true, marriageMonths, null, null, null, accountMonths, null, null, null);
    }

    @Test
    @DisplayName("개월 수는 년·개월을 함께 적는다 — 6 / 24 / 30")
    void monthsFormat() {
        assertThat(ExtractionReadBack.months(6)).isEqualTo("6개월");
        assertThat(ExtractionReadBack.months(24)).isEqualTo("24개월(2년)");
        assertThat(ExtractionReadBack.months(30)).isEqualTo("30개월(2년 6개월)");
    }

    @Test
    @DisplayName("문장 속 기간: 3년·2년 6개월·3년 반·4년차·8개월")
    void monthsInText() {
        assertThat(ExtractionReadBack.monthsIn("통장은 3년 됐어요")).containsExactly(36);
        assertThat(ExtractionReadBack.monthsIn("2년 6개월 전에")).contains(30);
        assertThat(ExtractionReadBack.monthsIn("결혼 3년 반")).contains(42);
        assertThat(ExtractionReadBack.monthsIn("결혼 4년차")).contains(48, 36);   // N년차는 둘 다 정당한 읽기
        assertThat(ExtractionReadBack.monthsIn("8개월 됐습니다")).contains(8);
        assertThat(ExtractionReadBack.monthsIn("재작년에 결혼했어요")).isEmpty();
    }

    @Test
    @DisplayName("한글 수사도 기간으로 읽는다 — 두 해·오 년·여섯 달. '이해해요' 는 2년이 아니다")
    void koreanNumerals() {
        assertThat(ExtractionReadBack.monthsIn("결혼한 지 두 해 됐고")).contains(24);
        assertThat(ExtractionReadBack.monthsIn("청약통장은 오 년 넣었어요")).contains(60);
        assertThat(ExtractionReadBack.monthsIn("한 달")).contains(1);
        assertThat(ExtractionReadBack.monthsIn("여섯 달 됐어요")).contains(6);
        assertThat(ExtractionReadBack.monthsIn("육 개월")).contains(6);
        assertThat(ExtractionReadBack.monthsIn("설명을 이해해요")).isEmpty();
        // 한글 수사로 쓴 문장을 맞게 읽었다면 경고하지 않는다 (라이브 테스트에서 불필요한 경고가 떴던 문장)
        assertThat(ExtractionReadBack.warnings("결혼한 지 두 해 됐고 청약통장은 오 년 넣었어요", profile(24, 60))).isEmpty();
    }

    @Test
    @DisplayName("eval 에서 나온 오독: 통장 3년 → 24개월 이면 경고(문장 36개월, 채워진 24개월)")
    void warnsOnMisreadYears() {
        var w = ExtractionReadBack.warnings("미혼이고 자녀도 없습니다. 무주택이고 청약통장은 3년 됐어요.", profile(null, 24));

        assertThat(w).hasSize(1);
        assertThat(w.get(0)).contains("36개월(3년)").contains("24개월(2년)").contains("청약통장 가입 기간");
    }

    @Test
    @DisplayName("맞게 읽었으면 경고 없음 — 3년→36, 5년 전→60, 3년 반→42, 4년차→36 또는 48")
    void noWarningWhenConsistent() {
        assertThat(ExtractionReadBack.warnings("결혼한 지 3년 됐고 청약통장은 5년 전에 만들었어요", profile(36, 60))).isEmpty();
        assertThat(ExtractionReadBack.warnings("결혼 3년 반이에요", profile(42, null))).isEmpty();
        assertThat(ExtractionReadBack.warnings("결혼 4년차 부부", profile(36, null))).isEmpty();
        assertThat(ExtractionReadBack.warnings("결혼 4년차 부부", profile(48, null))).isEmpty();
    }

    @Test
    @DisplayName("문장에 기간 숫자가 없는데 값이 채워졌으면 근거 없음 경고 (재작년 → 24)")
    void warnsWhenNoDurationInText() {
        var w = ExtractionReadBack.warnings("결혼은 재작년에 했어요.", profile(24, null));

        assertThat(w).hasSize(1);
        assertThat(w.get(0)).contains("숫자로 적혀 있지 않은데").contains("혼인 기간");
    }

    @Test
    @DisplayName("값이 null 이면 경고하지 않는다 (추측하지 않은 것)")
    void nullIsFine() {
        assertThat(ExtractionReadBack.warnings("결혼은 재작년에 했어요.", profile(null, null))).isEmpty();
    }

    @Test
    @DisplayName("이렇게 읽었어요: 채워진 값만, 년·개월 함께. 채운 칸 이름 목록도")
    void readBackAndFilled() {
        var p = new ExtractedProfile(true, 36, 2, null, true, 60, null, null, null);

        assertThat(ExtractionReadBack.readBack(p))
                .containsExactly("혼인 중", "혼인 기간 36개월(3년)", "미성년 자녀 2명", "무주택", "청약통장 가입 60개월(5년)");
        assertThat(ExtractionReadBack.filledFields(p))
                .containsExactly("married", "monthsSinceMarriage", "childCount", "houseless", "accountMonths");
    }

    @Test
    @DisplayName("결과 객체에 실려 나온다 — 원문을 넘기면 경고가, 안 넘기면 경고 없이")
    void resultCarriesWarnings() {
        var p = profile(null, 24);
        assertThat(ProfileExtractionResult.extracted(p, "통장 3년 됐어요").warnings()).hasSize(1);
        assertThat(ProfileExtractionResult.extracted(p).warnings()).isEmpty();
        assertThat(ProfileExtractionResult.extracted(p, "통장 2년 됐어요").readBack())
                .contains("청약통장 가입 24개월(2년)");
    }
}
