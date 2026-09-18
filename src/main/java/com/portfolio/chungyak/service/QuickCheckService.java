package com.portfolio.chungyak.service;

import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.domain.SpecialSupplyType;
import com.portfolio.chungyak.rule.ApplicantProfile;
import com.portfolio.chungyak.rule.EligibilityDecision;
import com.portfolio.chungyak.rule.EligibilityEngine;
import com.portfolio.chungyak.rule.EligibilityEngine.MatchResult;
import com.portfolio.chungyak.web.view.QuickCheckView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * "3문항 빠른 진단" — 처음 온 사용자가 소득·자산까지 적기 전에 먼저 보는 맛보기.
 *
 * ★ 절대 규칙 경계: 판정은 여기서도 {@link EligibilityEngine} 만 쓴다. 이 서비스가 하는 일은
 * 엔진이 낸 결과를 유형 단위로 <b>모으기만</b> 하는 것이고, 자격을 새로 따지지 않는다.
 *
 * <b>이 진단은 "자격 있음"을 말하지 않는다.</b> 3문항만으로는 소득·자산·청약통장이 전부
 * {@code missingInputs} 라서 어떤 유형도 eligible 이 될 수 없다. 대신 엔진이 이미 확정한 두 가지만 쓴다:
 * <ul>
 *   <li><b>탈락 확정</b> — {@code failedReasons} 가 있는 유형. 입력이 더 들어와도 뒤집히지 않는다.</li>
 *   <li><b>가능성 있음</b> — 아직 실패 사유가 없고 확인만 남은 유형. 자격 인정이 아니라 "확인 필요"다.</li>
 * </ul>
 *
 * 다루는 유형을 {@link #QUICK_CHECKABLE_TYPES} 로 제한하는 이유: {@link ApplicantProfile} 의
 * boolean 필드는 기본값이 false 라, 묻지도 않은 항목(신생아·노부모부양 등)이 "아니오"로 읽혀
 * 탈락 확정으로 잘못 나온다. 3문항으로 실제 판단 근거가 생기는 유형만 보여주고, 나머지는
 * 정밀 판정으로 넘긴다.
 */
@Service
@RequiredArgsConstructor
public class QuickCheckService {

    /** 3문항(혼인·자녀 수·무주택)으로 근거가 생기는 유형만. 위 클래스 주석 참고. */
    private static final Set<SpecialSupplyType> QUICK_CHECKABLE_TYPES = Set.of(
            SpecialSupplyType.NEWLYWED,
            SpecialSupplyType.MULTI_CHILD,
            SpecialSupplyType.FIRST_TIME,
            SpecialSupplyType.NEWLYWED_HOPE_TOWN);

    private final AnnouncementQueryService queryService;
    private final EligibilityEngine eligibilityEngine;

    public QuickCheckView check(ApplicantProfile profile) {
        List<Announcement> announcements = queryService.findOpenOrUpcoming(null, null, null);
        if (announcements.isEmpty()) {
            return QuickCheckView.notEvaluated();
        }

        // 유형별로 "모든 공고에서 실패했는가"를 모은다 — 공고마다 규제지역·주택유형이 달라
        // 한 공고에서만 실패한 건 확정 탈락이 아니다.
        Map<SpecialSupplyType, List<EligibilityDecision>> byType = new LinkedHashMap<>();
        for (Announcement announcement : announcements) {
            MatchResult result = eligibilityEngine.evaluate(profile, announcement);
            result.decisions().forEach((type, decision) -> {
                if (QUICK_CHECKABLE_TYPES.contains(type)) {
                    byType.computeIfAbsent(type, t -> new ArrayList<>()).add(decision);
                }
            });
        }

        List<QuickCheckView.TypeRow> ruledOut = new ArrayList<>();
        List<QuickCheckView.TypeRow> possible = new ArrayList<>();
        Set<String> remainingInputs = new LinkedHashSet<>();

        for (SpecialSupplyType type : SpecialSupplyType.values()) {
            List<EligibilityDecision> decisions = byType.get(type);
            if (decisions == null || decisions.isEmpty()) {
                continue;
            }
            boolean failedEverywhere = decisions.stream().allMatch(d -> !d.getFailedReasons().isEmpty());

            if (failedEverywhere) {
                ruledOut.add(new QuickCheckView.TypeRow(type.getLabel(), firstFailedReason(decisions)));
            } else {
                possible.add(new QuickCheckView.TypeRow(type.getLabel(), null));
                decisions.stream()
                        .filter(d -> d.getFailedReasons().isEmpty())
                        .forEach(d -> remainingInputs.addAll(d.getMissingInputs()));
            }
        }

        return QuickCheckView.of(possible, ruledOut, List.copyOf(remainingInputs));
    }

    private static String firstFailedReason(List<EligibilityDecision> decisions) {
        return decisions.stream()
                .flatMap(d -> d.getFailedReasons().stream())
                .findFirst()
                .orElse(null);
    }
}
