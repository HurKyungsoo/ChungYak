package com.portfolio.chungyak.service;

import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.domain.HouseDetailType;
import com.portfolio.chungyak.mapper.CompetitionMapper;
import com.portfolio.chungyak.mapper.CompetitionMapper.PastSpecialSupply;
import com.portfolio.chungyak.web.view.EligibilityResultView;
import com.portfolio.chungyak.web.view.EligibilityResultView.TypeDecision;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * "충족한 특공 유형이 여럿일 때 어느 쪽으로 넣을까" 참고 표.
 *
 * 특별공급은 한 공고에 한 유형으로만 신청한다. 판정이 끝난 뒤, 충족한 유형마다 이 공고의 배정 세대와
 * 같은 시도·같은 공고 구분(민영/국민)의 최근 12개월 공고에서 "신청 ÷ 배정"이 어땠는지를 나란히 놓는다.
 * 판정을 다시 하지 않고(충족 여부는 화면 모델 그대로) 추천하지도 않는다 — 과거 기록일 뿐이고,
 * 유형마다 당첨자 선정 방식(가점·추첨·우선공급)이 달라 같은 배수라도 당첨 가능성이 같지 않다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpecialSupplyCompareService {

    /** 비교 기간 — 당첨가점 기록(#34)과 같은 12개월 */
    static final int MONTHS = 12;
    /**
     * 12개월 표본이 모자란 행만 넓혀 다시 세는 기간. 운영 확인(2026-11-11, 열린 공고 16건·42행)에서 표본 부족은
     * 지방 민영 신생아 3행뿐이었다 — 전체 기간을 늘리면 표본 많은 지역에 오래된 시장 상황이 섞이므로 그 행만 넓힌다.
     */
    static final int WIDE_MONTHS = 24;
    /** 이보다 적은 공고로는 중앙값을 내지 않는다 — 공고 한두 개는 그 단지 사정이다 */
    static final int MIN_SAMPLES = 3;

    private final CompetitionMapper mapper;
    private final Clock clock;

    /**
     * @param allocated   이 공고의 이 유형 배정 세대
     * @param median      과거 공고별 "신청 ÷ 배정" 중앙값 — 표본이 MIN_SAMPLES 미만이면 null
     * @param samples     과거 공고 수(이 유형 배정이 있던 공고)
     * @param lowest      이 표에서 중앙값이 가장 낮은 유형인지(같은 기간으로 센 유형이 둘 이상일 때만)
     * @param months      이 행을 센 기간 — 기본 {@link #MONTHS}, 표본이 모자라 넓혔으면 {@link #WIDE_MONTHS}
     */
    public record Row(String typeName, String typeLabel, int allocated, Double median, Double min, Double max,
                      int samples, boolean lowest, int months) {

        public boolean widened() {
            return months != MONTHS;
        }

        Row markLowest() {
            return new Row(typeName, typeLabel, allocated, median, min, max, samples, true, months);
        }
    }

    public record Comparison(String region, String scope, int months, List<Row> rows) {}

    public Optional<Comparison> compare(Announcement a, EligibilityResultView view) {
        if (a == null || view == null || a.getExternalId() == null || a.getExternalId().startsWith("LH-")) {
            return Optional.empty();   // LH 는 이 API 범위 밖이고 유형별 배정 세대도 없다
        }
        List<TypeDecision> eligible = view.allDecisions().stream()
                .filter(TypeDecision::eligible)
                .filter(d -> d.allocatedInAnnouncement() != null && d.allocatedInAnnouncement() > 0)
                .toList();
        if (eligible.size() < 2 || a.getRegionName() == null || a.getHouseDetailType() == null
                || a.getHouseDetailType() == HouseDetailType.UNKNOWN) {
            return Optional.empty();
        }

        LocalDate to = a.getNoticeDate() != null ? a.getNoticeDate() : LocalDate.now(clock);
        Map<String, List<Double>> ratios;
        Map<String, List<Double>> wideRatios = null;
        try {
            Map<String, List<Double>> recent = ratios(a, to.minusMonths(MONTHS), to);
            ratios = recent;
            boolean anyShort = eligible.stream().anyMatch(d -> recent.getOrDefault(d.typeName(), List.of()).size() < MIN_SAMPLES);
            if (anyShort) wideRatios = ratios(a, to.minusMonths(WIDE_MONTHS), to);
        } catch (RuntimeException e) {
            log.warn("특공 유형 비교 기록 조회 실패 — 표를 빼고 결과를 보여 준다: {}", e.toString());
            return Optional.empty();
        }

        List<Row> rows = new ArrayList<>();
        for (TypeDecision d : eligible) {
            List<Double> r = ratios.getOrDefault(d.typeName(), List.of());
            int months = MONTHS;
            if (r.size() < MIN_SAMPLES && wideRatios != null) {
                List<Double> wide = wideRatios.getOrDefault(d.typeName(), List.of());
                if (wide.size() > r.size()) {   // 넓혀도 그대로면 12개월 그대로 둔다
                    r = wide;
                    months = WIDE_MONTHS;
                }
            }
            boolean enough = r.size() >= MIN_SAMPLES;
            rows.add(new Row(d.typeName(), d.typeLabel(), d.allocatedInAnnouncement(),
                    enough ? median(r) : null, enough ? r.get(0) : null, enough ? r.get(r.size() - 1) : null,
                    r.size(), false, months));
        }
        // 기록이 하나도 없으면(수집 전·기록 없는 지역) 표가 "기록 없음"만 늘어놓는다 — 빼고 결과만 보여 준다
        if (rows.stream().allMatch(x -> x.samples() == 0)) return Optional.empty();

        // "가장 낮음"은 같은 기간으로 센 행끼리만 — 기간이 다르면 시장 상황이 달라 나란히 견줄 수 없다
        List<Row> withMedian = rows.stream().filter(x -> x.median() != null && !x.widened()).toList();
        if (withMedian.size() < 2) withMedian = rows.stream().filter(x -> x.median() != null && x.widened()).toList();
        if (withMedian.size() >= 2) {
            Row low = withMedian.stream().min(Comparator.comparingDouble(Row::median)).orElseThrow();
            rows.replaceAll(x -> x == low ? x.markLowest() : x);
        }
        return Optional.of(new Comparison(a.getRegionName(), a.getHouseDetailType().getLabel(), MONTHS, rows));
    }

    /** 유형별 과거 공고 "신청 ÷ 배정" — 오름차순 */
    private Map<String, List<Double>> ratios(Announcement a, LocalDate from, LocalDate to) {
        List<PastSpecialSupply> past = mapper.pastSpecialSupplies(a.getRegionName(), a.getHouseDetailType().name(), from, to);
        Map<String, List<Double>> ratios = past.stream()
                .filter(p -> p.households() > 0)
                .collect(Collectors.groupingBy(PastSpecialSupply::supplyType,
                        Collectors.mapping(p -> (double) p.applications() / p.households(), Collectors.toList())));
        ratios.values().forEach(l -> l.sort(Comparator.naturalOrder()));
        return ratios;
    }

    static double median(List<Double> sorted) {
        int n = sorted.size();
        return n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2;
    }
}
