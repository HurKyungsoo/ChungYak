package com.portfolio.chungyak.service;

import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.rule.ApplicantProfile;
import com.portfolio.chungyak.rule.EligibilityEngine;
import com.portfolio.chungyak.rule.EligibilityEngine.MatchResult;
import com.portfolio.chungyak.web.view.MatchRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
}
