package com.portfolio.chungyak.service;

import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.domain.HouseDetailType;
import com.portfolio.chungyak.domain.HouseType;
import com.portfolio.chungyak.domain.NoticeFigures;
import com.portfolio.chungyak.domain.SupplyCategory;
import com.portfolio.chungyak.repository.AnnouncementDocumentRepository;
import com.portfolio.chungyak.rule.IncomeReference;
import com.portfolio.chungyak.rule.NewlywedHopeTownProperties;
import com.portfolio.chungyak.rule.SpecialSupplyRequirementProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 제도 금액 감시 — 열린 분양 공고의 공고문에 적힌 소득표·자산 기준을 설정값(application.yml)과 대조한다.
 *
 * 기준 금액은 사람이 매년 고친다(CLAUDE.md 절대 규칙 6). 새 해 값은 공고문에 먼저 나오므로, 공고문과 설정이 어긋나면
 * 설정을 갱신할 때라는 신호다. 비교 대상은 그 공고의 자격 판단 기준일에 맞는 설정 버전({@code YearlyFigure})이다.
 * 판정을 바꾸지 않는다 — 알리기만 한다(ERROR 로그 + {@code GET /api/admin/figures/check}). 서비스 상태(health)와는 무관하다.
 *
 * 소득표·공공 자산은 적힌 값을 직접 비교하고, 신혼희망타운 자산·민영 부동산 추첨 기준은 표기가 제각각이라 설정 금액이
 * 공고문에 적혀 있는지만 본다("확인 필요"). 임대 공고는 소득표·자산 기준이 분양과 달라 대조하지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FigureWatchService {

    private final AnnouncementQueryService queryService;
    private final AnnouncementDocumentRepository documentRepository;
    private final IncomeReference incomeReference;
    private final SpecialSupplyRequirementProperties requirements;
    private final NewlywedHopeTownProperties hopeTown;
    private final Clock clock;
    private final AtomicReference<Report> last = new AtomicReference<>();

    /**
     * @param figure   어떤 기준인지
     * @param certain  true: 적힌 값이 설정과 다름 / false: 설정 금액이 공고문에 안 보임(확인 필요)
     * @param expected 설정값(그 공고 기준일의 버전) · @param found 공고문에 적힌 값(못 읽었으면 빈 문자열)
     */
    public record Finding(Long announcementId, String houseName, LocalDate baseDate, String figure, boolean certain,
                          String expected, String found) {}

    public record Report(Instant checkedAt, int announcementsChecked, int withNotice, List<Finding> findings) {}

    @Scheduled(cron = "0 50 4 * * *", zone = "Asia/Seoul")   // 공고(04:00)·경쟁률(04:30) 수집 뒤
    public void checkDaily() {
        try {
            Report r = check();
            if (r.findings().isEmpty()) {
                log.info("제도 금액 감시 — 분양 공고 {}건(공고문 {}건) 설정과 일치", r.announcementsChecked(), r.withNotice());
            }
        } catch (RuntimeException e) {
            log.warn("제도 금액 감시 실패 — 다음 배치에서 다시: {}", e.toString());
        }
    }

    /** 지금 대조한다. 어긋난 건마다 ERROR 로그 — 로그 알림 규칙으로 잡을 수 있다(docs/DEPLOYMENT.md 모니터링) */
    @Transactional(readOnly = true)
    public Report check() {
        List<Finding> findings = new ArrayList<>();
        int checked = 0, withNotice = 0;
        for (Announcement a : queryService.findOpenOrUpcoming(null, null, null)) {
            if (!a.isEligibilityTarget() || a.getSupplyCategory() == SupplyCategory.RENTAL) continue;
            checked++;
            var doc = documentRepository.findByAnnouncementId(a.documentOwnerId()).orElse(null);
            if (doc == null) continue;
            withNotice++;
            String text = doc.getRawText();
            LocalDate base = a.eligibilityBaseDate();
            checkIncome(a, base, text, findings);
            boolean hope = a.getHouseType() == HouseType.NEWLYWED_HOPE_TOWN || a.getSupplyCategory() == SupplyCategory.HOPE_TOWN;
            if (hope) checkHopeTownAsset(a, base, text, findings);
            else if (a.usesTotalAssetLimit()) checkTotalAsset(a, base, text, findings);
            else if (a.getHouseDetailType() == HouseDetailType.PUBLIC) checkPublicAsset(a, base, text, findings);
            else if (a.getHouseDetailType() == HouseDetailType.PRIVATE) checkRealEstateTrack(a, base, text, findings);
        }
        findings.forEach(f -> log.error("제도 금액 감시 — 공고 #{} {} 의 {}: 설정 {} / 공고문 {} — {}",
                f.announcementId(), f.houseName(), f.figure(), f.expected(), f.found().isEmpty() ? "(설정 금액 없음)" : f.found(),
                f.certain() ? "설정 갱신 필요" : "확인 필요"));
        Report r = new Report(clock.instant(), checked, withNotice, List.copyOf(findings));
        last.set(r);
        return r;
    }

    /** 마지막 배치 결과(없으면 null) */
    public Report lastReport() {
        return last.get();
    }

    private void checkIncome(Announcement a, LocalDate base, String text, List<Finding> out) {
        List<Long> row = NoticeFigures.incomeRow100(text);
        if (row.isEmpty()) return;
        var table = incomeReference.tableFor(base);
        Map<Integer, Long> t = table.value();
        List<Long> expected = new ArrayList<>();
        for (int size = 3; size <= 8; size++) expected.add(t.get(size));
        if (!expected.equals(row)) {
            out.add(new Finding(a.getId(), a.getHouseName(), base, "도시근로자 소득표 100%(" + table.basis() + "년, 3인 이하·4~8인)",
                    true, won(expected), won(row)));
        }
    }

    private void checkPublicAsset(Announcement a, LocalDate base, String text, List<Finding> out) {
        var pick = requirements.assetLimitFor(base);
        if (pick == null) return;
        var limit = pick.value();
        Set<Long> realEstate = NoticeFigures.thousandWonAfter(text, "부동산");
        Set<Long> car = NoticeFigures.thousandWonAfter(text, "자동차");
        Set<Long> knownRe = new java.util.HashSet<>(), knownCar = new java.util.HashSet<>();
        for (int tier = 0; tier <= 2; tier++) {
            var amounts = limit.forBirthTier(tier);
            knownRe.add(amounts.realEstate());
            knownCar.add((long) amounts.carValue());
        }
        // 적힌 금액이 설정(출생 완화 금액 포함)과 하나도 안 맞을 때만 — 다른 표의 금액이 섞여도 거짓 경보를 줄인다
        if (!realEstate.isEmpty() && realEstate.stream().noneMatch(knownRe::contains)) {
            out.add(new Finding(a.getId(), a.getHouseName(), base, "공공 특공 자산 — 부동산(" + pick.basis() + ")",
                    true, won(List.copyOf(knownRe)), won(List.copyOf(realEstate))));
        }
        if (!car.isEmpty() && car.stream().noneMatch(knownCar::contains)) {
            out.add(new Finding(a.getId(), a.getHouseName(), base, "공공 특공 자산 — 자동차(" + pick.basis() + ")",
                    true, won(List.copyOf(knownCar)), won(List.copyOf(car))));
        }
    }

    /** 뉴홈 토지임대부·지분적립형 — 공고문에 "총자산 362백만원" 같은 기본 금액이 있는지(표기가 제각각이라 확인 필요로만) */
    private void checkTotalAsset(Announcement a, LocalDate base, String text, List<Finding> out) {
        var pick = requirements.assetLimitFor(base);
        if (pick == null || pick.value().totalAsset() == null || !text.contains("총자산")) return;
        long expected = pick.value().totalAsset().base();
        if (!NoticeFigures.mentionsAmount(text, expected)) {
            out.add(new Finding(a.getId(), a.getHouseName(), base, "토지임대부·지분적립형 총자산(" + pick.basis() + ")",
                    false, won(List.of(expected)), ""));
        }
    }

    private void checkHopeTownAsset(Announcement a, LocalDate base, String text, List<Finding> out) {
        var pick = hopeTown.assetLimitFor(base);
        if (pick == null || !text.contains("총자산")) return;
        if (!NoticeFigures.mentionsAmount(text, pick.value())) {
            out.add(new Finding(a.getId(), a.getHouseName(), base, "신혼희망타운 자산(" + pick.basis() + ")",
                    false, won(List.of(pick.value())), ""));
        }
    }

    private void checkRealEstateTrack(Announcement a, LocalDate base, String text, List<Finding> out) {
        if (requirements.realEstateLimit() == null) return;
        String flat = text.replaceAll("\\s+", "");
        if (!flat.contains("부동산가액") || !flat.contains("추첨")) return;
        var pick = requirements.realEstateLimitFor(base);
        if (!NoticeFigures.mentionsAmount(text, pick.value())) {
            out.add(new Finding(a.getId(), a.getHouseName(), base, "민영 부동산 추첨 기준(" + pick.basis() + ")",
                    false, won(List.of(pick.value())), ""));
        }
    }

    private static String won(List<Long> amounts) {
        return amounts.stream().sorted().map(v -> String.format(java.util.Locale.ROOT, "%,d", v)).toList().toString();
    }
}
