package com.portfolio.chungyak.external;

import com.portfolio.chungyak.domain.SpecialSupplyType;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * LH 상세 API 의 {@code dsSplScdl.HS_SBSC_ACP_TRG_CD_NM}(청약접수대상 구분) 문자열을
 * {@link SpecialSupplyType} 으로 정규화한다.
 *
 * 라벨이 유형·시기마다 제각각이라(라이브로 확인) 부분일치로 맞춘다:
 *  - 분양주택: "다자녀특별(85㎡이하)", "신혼부부특별", "생애최초특별",
 *              "노부모부양특별(85㎡이하)", "기관추천", "신생아특별", "신생아 우선공급"
 *  - 신혼희망타운: "예비신혼부부", "신혼부부"
 *  - 국민임대류: "일반1순위", "일반2순위" (특별공급 아님 → 무시)
 *
 * 도메인은 이 enum 만 알고, 원문 파싱은 여기서 끝낸다. (CLAUDE.md 경계 규칙)
 */
@Component
public class LhSpecialSupplyMapper {

    /** 부분일치 규칙. 위에서부터 먼저 맞는 것을 쓴다(신생아 → 신혼 순서 주의는 없음: 겹치지 않음). */
    private record Rule(String keyword, SpecialSupplyType type) {}

    private static final List<Rule> RULES = List.of(
            new Rule("다자녀", SpecialSupplyType.MULTI_CHILD),
            new Rule("생애최초", SpecialSupplyType.FIRST_TIME),
            new Rule("노부모", SpecialSupplyType.OLD_PARENTS),
            new Rule("기관추천", SpecialSupplyType.INSTITUTION_RECOMMEND),
            new Rule("신생아", SpecialSupplyType.NEWBORN),
            new Rule("신혼", SpecialSupplyType.NEWLYWED),   // 신혼부부특별 / 예비신혼부부 / 신혼부부
            new Rule("청년", SpecialSupplyType.YOUTH),
            new Rule("이전기관", SpecialSupplyType.TRANSFER_INSTITUTION)
    );

    /** 한 라벨 → 유형. 일반공급 등 특별공급이 아니면 비어 있음. */
    public Optional<SpecialSupplyType> map(String acceptanceTargetName) {
        if (acceptanceTargetName == null) return Optional.empty();
        String s = acceptanceTargetName.replaceAll("\\s+", "");
        if (s.isEmpty() || s.contains("일반")) return Optional.empty();
        return RULES.stream()
                .filter(r -> s.contains(r.keyword()))
                .map(Rule::type)
                .findFirst();
    }

    /** 라벨 목록 → 특별공급 유형 집합 */
    public Set<SpecialSupplyType> mapAll(List<String> acceptanceTargetNames) {
        Set<SpecialSupplyType> result = EnumSet.noneOf(SpecialSupplyType.class);
        for (String name : acceptanceTargetNames) {
            map(name).ifPresent(result::add);
        }
        return result;
    }
}
