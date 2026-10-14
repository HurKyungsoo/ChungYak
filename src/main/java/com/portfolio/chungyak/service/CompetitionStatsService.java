package com.portfolio.chungyak.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.domain.HouseDetailType;
import com.portfolio.chungyak.domain.UnitType;
import com.portfolio.chungyak.mapper.CompetitionMapper;
import com.portfolio.chungyak.mapper.CompetitionMapper.PastUnit;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "이 지역 최근 당첨 기록" — 공고 상세의 <b>참고 정보</b> (#34). 판정과 무관하다(규칙 엔진은 이 값을 보지 않는다).
 *
 * 비교 단위는 데이터 조사로 정했다(2026-10-14, CLAUDE.md): 같은 <b>시도</b> × 전용면적대(60㎡ 이하 / 60~85 / 85 초과) ×
 * 이 공고 전 <b>12개월</b>(표본이 3 공고 미만이면 24개월, 그래도 모자라면 그 면적대는 뺀다), 해당지역 기준.
 * 시군구는 표본이 너무 적었다(최근 공고 25건 중 2건만 3건 이상). 같은 묶음 안에서도 최저가점이 ±10점가량 흩어져
 * 숫자 하나가 아니라 범위·표본 수로만 보여 준다 — 분양가·규제·단지 규모에 따라 크게 달라 당첨 가능성을 뜻하지 않는다.
 *
 * 청약홈 민영 분양 공고만(국민주택은 가점제가 없어 가점이 0 으로 온다, LH 는 API 범위 밖, 무순위는 판정 대상 밖).
 */
@Service
public class CompetitionStatsService {

    static final int MIN_NOTICES = 3;
    static final int MONTHS = 12;
    static final int FALLBACK_MONTHS = 24;

    private final CompetitionMapper mapper;
    private final Cache<Long, Summary> cache = Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterWrite(Duration.ofHours(6))   // 하루 한 번 수집 — 몇 시간 늦게 반영돼도 된다
            .build();

    public CompetitionStatsService(CompetitionMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 면적대 하나의 지역 최근 기록.
     * @param months         실제로 본 기간(12 또는 24)
     * @param total          그 면적대에 가점·경쟁률 기록이 하나라도 있는 비교 공고 수
     * @param notices        그중 최저가점(0 아님)이 있는 공고 수 — 가점 범위의 표본. 미달 공고는 가점이 0 이라 빠진다
     * @param minLowest      공고별 최저가점(그 면적대 주택형 중 최솟값)의 최솟값 · 중앙값 · 최댓값
     * @param medianRate     1순위 해당지역 경쟁률(공고별 중앙값)의 중앙값 — 숫자 경쟁률이 하나도 없으면 null
     * @param undersubscribed 1순위 해당지역에서 미달("(△n)")이 난 공고 수
     */
    public record BandStat(String band, int months, int total, int notices, int minLowest, int medianLowest, int maxLowest,
                           Double medianRate, int undersubscribed) {}

    public record Summary(String region, List<BandStat> bands) {
        public static final Summary EMPTY = new Summary(null, List.of());

        public boolean isEmpty() {
            return bands.isEmpty();
        }
    }

    public Summary forAnnouncement(Announcement a) {
        if (!eligible(a)) return Summary.EMPTY;
        Summary hit = cache.getIfPresent(a.getId());
        if (hit != null) return hit;
        LocalDate to = a.getNoticeDate();
        List<PastUnit> past = mapper.pastUnits(a.getRegionName(), to.minusMonths(FALLBACK_MONTHS), to);
        Summary s = summarize(a.getRegionName(), bandsOf(a), past, to);
        // 빈 결과는 담아 두지 않는다 — 첫 수집 전에 연 공고가 몇 시간 동안 비어 보이지 않게
        if (!s.isEmpty()) cache.put(a.getId(), s);
        return s;
    }

    static boolean eligible(Announcement a) {
        return a != null && a.getId() != null && !a.isLh() && !a.isRemainder()
                && a.getHouseDetailType() == HouseDetailType.PRIVATE
                && a.getNoticeDate() != null && a.getRegionName() != null && !a.getRegionName().isBlank();
    }

    private static Set<String> bandsOf(Announcement a) {
        Set<String> bands = new LinkedHashSet<>();
        for (UnitType u : a.getUnitTypes()) {
            String b = band(u.getTypeName());
            if (b != null) bands.add(b);
        }
        return bands;
    }

    private static final Pattern AREA = Pattern.compile("^\\s*(\\d{1,3})\\.(\\d+)");
    private static final Pattern NUMERIC_RATE = Pattern.compile("^[\\d,]+(?:\\.\\d+)?$");

    /** 주택형 "084.9955A" → 전용 84.9955㎡ → 면적대 */
    static String band(String houseType) {
        if (houseType == null) return null;
        Matcher m = AREA.matcher(houseType);
        if (!m.find()) return null;
        double area = Double.parseDouble(m.group(1) + "." + m.group(2));
        return area <= 60 ? "60㎡ 이하" : area <= 85 ? "60~85㎡" : "85㎡ 초과";
    }

    /** 순수 함수 — 테스트가 직접 부른다 */
    static Summary summarize(String region, Set<String> bands, List<PastUnit> past, LocalDate noticeDate) {
        List<BandStat> out = new ArrayList<>();
        for (String band : List.of("60㎡ 이하", "60~85㎡", "85㎡ 초과")) {
            if (!bands.contains(band)) continue;
            BandStat stat = bandStat(band, past, noticeDate.minusMonths(MONTHS), MONTHS);
            if (stat == null) stat = bandStat(band, past, noticeDate.minusMonths(FALLBACK_MONTHS), FALLBACK_MONTHS);
            if (stat != null) out.add(stat);
        }
        return out.isEmpty() ? Summary.EMPTY : new Summary(region, List.copyOf(out));
    }

    private static BandStat bandStat(String band, List<PastUnit> past, LocalDate from, int months) {
        Map<String, List<PastUnit>> byNotice = new LinkedHashMap<>();
        for (PastUnit u : past) {
            if (u.noticeDate() == null || u.noticeDate().isBefore(from) || !band.equals(band(u.houseType()))) continue;
            byNotice.computeIfAbsent(u.houseManageNo(), k -> new ArrayList<>()).add(u);
        }
        List<Integer> lows = new ArrayList<>();
        List<Double> rates = new ArrayList<>();
        int under = 0;
        for (List<PastUnit> units : byNotice.values()) {
            units.stream().mapToInt(PastUnit::lowest).filter(v -> v > 0).min().ifPresent(lows::add);
            List<Double> noticeRates = new ArrayList<>();
            boolean anyUnder = false;
            for (PastUnit u : units) {
                String t = u.rateText() == null ? "" : u.rateText().strip();
                if (NUMERIC_RATE.matcher(t).matches()) noticeRates.add(Double.parseDouble(t.replace(",", "")));
                else if (t.startsWith("(")) anyUnder = true;   // "(△1)" 미달
            }
            if (!noticeRates.isEmpty()) rates.add(median(noticeRates));
            if (anyUnder) under++;
        }
        if (lows.size() < MIN_NOTICES) return null;
        lows.sort(Integer::compare);
        return new BandStat(band, months, byNotice.size(), lows.size(), lows.get(0), (int) Math.round(median(lows.stream().map(Integer::doubleValue).toList())),
                lows.get(lows.size() - 1), rates.isEmpty() ? null : median(rates), under);
    }

    private static double median(List<Double> values) {
        List<Double> v = new ArrayList<>(values);
        v.sort(Double::compare);
        int n = v.size();
        return n % 2 == 1 ? v.get(n / 2) : (v.get(n / 2 - 1) + v.get(n / 2)) / 2;
    }
}
