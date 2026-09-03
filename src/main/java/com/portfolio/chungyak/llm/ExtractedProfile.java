package com.portfolio.chungyak.llm;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.ArrayList;
import java.util.List;

/**
 * 자연어 질문에서 LLM 이 추출한 신청자 조건.
 *
 * 모든 필드가 nullable 이다. 이게 핵심이다 —
 * 문장에서 <b>분명히</b> 확인되는 값만 채우고, 언급이 없거나 애매하면 null 로 남긴다.
 * null 인 필드는 화면에서 "확인이 필요합니다" 로 사용자에게 되묻는다.
 * LLM 이 애매한 값을 추측해서 채우면 판정 근거가 무너진다(CLAUDE.md 절대 규칙).
 *
 * 이 레코드는 {@link com.portfolio.chungyak.rule.ApplicantProfile} 이 아니다.
 * 규칙 엔진에는 사용자가 폼에서 확인·수정한 값만 들어간다.
 */
@JsonClassDescription(
        "청약 신청자의 조건. 사용자가 쓴 문장에서 명시적으로 드러나는 값만 채운다. "
        + "추측 금지 — 문장에 근거가 없거나 모호하면 그 필드는 반드시 null.")
public record ExtractedProfile(

        @JsonPropertyDescription("혼인 중이면 true, 미혼/이혼/사별이면 false. "
                + "'신혼', '결혼했다', '남편/아내'가 나오면 true. "
                + "혼인 여부를 알 수 없으면 null.")
        Boolean married,

        @JsonPropertyDescription("혼인신고일로부터 지난 개월 수(정수). "
                + "'결혼한 지 3년' -> 36, '작년에 결혼' -> 대략 12~18 범위이면 추측하지 말고 null. "
                + "'신혼인 것 같다'처럼 기간이 불명확하면 null.")
        Integer monthsSinceMarriage,

        @JsonPropertyDescription("미성년(만 19세 미만) 자녀 수(정수). "
                + "'아이 둘' -> 2, '자녀 없음' -> 0. 자녀 수 언급이 없으면 null.")
        Integer childCount,

        @JsonPropertyDescription("만 2세 이하 자녀(태아·입양 포함)가 있으면 true, 없으면 false. "
                + "'신생아', '돌 안 된 아기', '임신 중'이면 true. 언급 없으면 null.")
        Boolean hasNewborn,

        @JsonPropertyDescription("현재 세대 전원이 무주택이면 true, 주택을 소유 중이면 false. "
                + "'집 없다', '전세 산다' -> true. '내 집 있다' -> false. 언급 없으면 null.")
        Boolean houseless,

        @JsonPropertyDescription("청약통장 가입 기간(개월, 정수). "
                + "'청약통장 2년' -> 24. 가입은 했지만 기간을 모르면 null. 언급 없으면 null.")
        Integer accountMonths,

        @JsonPropertyDescription("과거에 주택을 소유한 적이 한 번이라도 있으면 true, "
                + "세대 전원이 무주택 이력이면 false. "
                + "'집을 사본 적 없다' -> false. '예전에 집이 있었다' -> true. 언급 없으면 null.")
        Boolean everOwnedHouse,

        @JsonPropertyDescription("만 65세 이상 직계존속을 3년 이상 계속 부양 중이면 true, 아니면 false. "
                + "'부모님 모시고 산다'만으로는 나이·기간을 알 수 없으면 null.")
        Boolean supportingOldParents,

        @JsonPropertyDescription("세대주이면 true, 세대원이면 false. "
                + "'세대주'라고 명시되면 true. 언급 없으면 null.")
        Boolean householdHead
) {

    /** 하나라도 추출된 값이 있는지 — 전부 null 이면 추출이 사실상 실패한 것. */
    public boolean hasAnyValue() {
        return married != null || monthsSinceMarriage != null || childCount != null
                || hasNewborn != null || houseless != null || accountMonths != null
                || everOwnedHouse != null || supportingOldParents != null || householdHead != null;
    }

    /**
     * LLM 이 채우지 못한(null) 필드의 사람이 읽을 라벨 목록.
     * 화면에서 "이 항목들은 답변에서 확인되지 않았습니다 — 직접 선택하세요" 로 되묻는다.
     * 순수 함수 — 단위테스트가 여기를 검증한다.
     */
    public List<String> unknownFieldLabels() {
        List<String> unknown = new ArrayList<>();
        if (married == null) unknown.add("혼인 여부");
        if (monthsSinceMarriage == null) unknown.add("혼인 기간(개월)");
        if (childCount == null) unknown.add("미성년 자녀 수");
        if (hasNewborn == null) unknown.add("2세 이하 자녀(신생아) 유무");
        if (houseless == null) unknown.add("무주택 여부");
        if (accountMonths == null) unknown.add("청약통장 가입 기간(개월)");
        if (everOwnedHouse == null) unknown.add("과거 주택 소유 이력");
        if (supportingOldParents == null) unknown.add("노부모 부양 여부");
        if (householdHead == null) unknown.add("세대주 여부");
        return unknown;
    }
}
