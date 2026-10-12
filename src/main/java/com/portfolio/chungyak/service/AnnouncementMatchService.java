package com.portfolio.chungyak.service;

import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.rule.ApplicantProfile;
import com.portfolio.chungyak.rule.EligibilityEngine;
import com.portfolio.chungyak.rule.EligibilityEngine.MatchResult;
import com.portfolio.chungyak.web.view.FixableGroup;
import com.portfolio.chungyak.web.view.ListBadge;
import com.portfolio.chungyak.web.view.MatchRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * "내 조건으로 지금 지원 가능한 공고 찾기" — 공고를 먼저 고르지 않고 조건부터 넣는 역방향 조회.
 *
 * ★ 절대 규칙 경계: 판정은 여기서도 {@link EligibilityEngine} 만 쓴다({@link
 * com.portfolio.chungyak.alert.NewAnnouncementAlertService} 와 같은 패턴). 이 서비스는 접수중·
 * 접수예정 공고 전체를 순회하며 판정 결과를 나열할 뿐 새로 만들어내지 않는다.
 *
 * 전체 공고(2,800여 건)가 아니라 {@code findOpenOrUpcoming} 로 이미 좁혀진 접수중·접수예정
 * 공고만 순회한다 — 마감된 공고는 신청할 수 없어 의미가 없고, 수십~수백 건 규모라 매 요청마다
 * 전건 판정해도 감당된다(D1 MyBatis 전환 전까지는 이 규모 전제가 유효).
 */
@Service
@RequiredArgsConstructor
public class AnnouncementMatchService {

    private final AnnouncementQueryService queryService;
    private final EligibilityEngine eligibilityEngine;

    public List<MatchRow> findMatching(ApplicantProfile profile) {
        LocalDate today = queryService.today();
        List<Announcement> announcements = queryService.findOpenOrUpcoming(null, null, null);

        List<MatchRow> rows = new ArrayList<>();
        for (Announcement announcement : announcements) {
            MatchResult result = eligibilityEngine.evaluate(profile, announcement);
            if (!result.hasAnyMatch()) {
                continue;
            }
            rows.add(MatchRow.of(result, queryService.statusOf(announcement), today));
        }

        // 마감 임박한 공고를 먼저 — 신청 가능한데 놓치면 안 되는 순서
        rows.sort(Comparator.comparing(MatchRow::receptEndDate,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return rows;
    }

    /**
     * "이것만 바뀌면 가능" — 지금 신청 가능한 유형이 하나도 없는 공고 중, 물량 있는 유형의 미충족이 전부
     * 기간·선택으로 메울 수 있는 것({@link com.portfolio.chungyak.rule.EligibilityDecision#isFixable()})을
     * 그 유형의 힌트 문장 묶음별로 모은다. 많이 열리는 보완부터.
     *
     * 유형마다 따로 묶는다 — 한 공고에서 "신혼부부는 통장 3개월, 생애최초는 거주 6개월"이면 둘 중 하나만
     * 채워도 되는 것이지 둘 다가 아니다. 힌트를 공고 단위로 합치면 필요한 보완을 부풀려 말하게 된다.
     */
    public List<FixableGroup> fixableGroups(ApplicantProfile profile) {
        Map<List<String>, List<FixableGroup.Item>> byHints = new LinkedHashMap<>();
        for (Announcement announcement : queryService.findOpenOrUpcoming(null, null, null)) {
            MatchResult result = eligibilityEngine.evaluate(profile, announcement);
            if (result.hasAnyMatch()) continue;   // 이미 신청 가능한 공고는 위 결과 목록에 있다
            var offered = java.util.Set.copyOf(announcement.visibleSupplyTypes());
            result.decisions().forEach((type, decision) -> {
                if (!offered.contains(type) || !decision.isFixable() || decision.getImprovementHints().isEmpty()) return;
                List<String> hints = decision.getImprovementHints().stream().distinct().sorted().toList();
                byHints.computeIfAbsent(hints, k -> new ArrayList<>())
                        .add(new FixableGroup.Item(announcement.getId(), announcement.getHouseName(), type.getLabel()));
            });
        }
        return byHints.entrySet().stream()
                .map(e -> new FixableGroup(e.getKey(), mergeTypesPerAnnouncement(e.getValue())))
                .sorted(Comparator.comparingInt(FixableGroup::count).reversed())
                .toList();
    }

    /** 같은 보완으로 한 공고의 여러 유형이 열리면 한 줄로 — "A 공고 · 신혼부부, 생애최초" */
    private static List<FixableGroup.Item> mergeTypesPerAnnouncement(List<FixableGroup.Item> items) {
        Map<Long, FixableGroup.Item> merged = new LinkedHashMap<>();
        for (FixableGroup.Item i : items) {
            merged.merge(i.id(), i, (a, b) -> new FixableGroup.Item(a.id(), a.houseName(), a.typeLabel() + ", " + b.typeLabel()));
        }
        return new ArrayList<>(merged.values());
    }

    /**
     * 공고 목록 카드의 "내 자격" 배지 — 접수중·접수예정 공고마다 엔진으로 판정해 결과를 배지로 옮긴다.
     * 목록에 무엇이 보이는지(필터)와 무관하게 전부 돌린다 — 화면이 자기 카드 id 로 골라 쓴다.
     */
    public Map<Long, ListBadge> badges(ApplicantProfile profile) {
        Map<Long, ListBadge> badges = new LinkedHashMap<>();
        for (Announcement announcement : queryService.findOpenOrUpcoming(null, null, null)) {
            badges.put(announcement.getId(), ListBadge.of(eligibilityEngine.evaluate(profile, announcement)));
        }
        return badges;
    }
}
