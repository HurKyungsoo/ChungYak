package com.portfolio.chungyak.service;

import com.portfolio.chungyak.external.ApplyhomeCompetitionClient;
import com.portfolio.chungyak.external.ApplyhomeCompetitionClient.Page;
import com.portfolio.chungyak.external.CompetitionRecords.Rate;
import com.portfolio.chungyak.external.CompetitionRecords.Score;
import com.portfolio.chungyak.external.PublicDataProperties;
import com.portfolio.chungyak.mapper.CompetitionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * 당첨가점·경쟁률 기록 수집 (#34) — 매일 04:30(공고 수집 04:00 뒤). 표가 비어 있으면 전체(≈86회 호출), 아니면 최신 몇 페이지만.
 *
 * API 는 최신 공고 순이다. 받은 공고는 그 공고의 행을 통째로 바꾼다(발표 뒤 값이 채워지거나 고쳐진다). 마지막 페이지 끝에
 * 걸친 공고는 행이 잘렸을 수 있어 이번엔 건드리지 않는다 — 다음 배치나 전체 수집이 채운다.
 * 판정과 무관한 참고 정보다(규칙 엔진은 이 표를 보지 않는다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompetitionSyncService {

    private static final int INSERT_CHUNK = 500;

    private final ApplyhomeCompetitionClient client;
    private final CompetitionMapper mapper;
    private final PublicDataProperties properties;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public record Report(int scoreRows, int rateRows, int houses, boolean full) {}

    @Scheduled(cron = "0 30 4 * * *", zone = "Asia/Seoul")
    public void syncDaily() {
        try {
            Report r = sync();
            log.info("당첨가점·경쟁률 수집 완료 — {}", r);
        } catch (RuntimeException e) {
            log.warn("당첨가점·경쟁률 수집 실패 — 다음 배치에서 다시: {}", e.toString());
        }
    }

    @Transactional
    public Report sync() {
        if (!client.isEnabled() || !running.compareAndSet(false, true)) return new Report(0, 0, 0, false);
        try {
            boolean full = mapper.countScores() == 0;
            var a = properties.getApplyhome();
            int maxPages = full ? Integer.MAX_VALUE : Math.max(1, a.getCompetitionSyncPages());
            int perPage = Math.max(100, a.getCompetitionPerPage());

            Map<String, List<Score>> scores = collect(client::fetchScores, Score::houseManageNo, maxPages, perPage);
            Map<String, List<Rate>> rates = collect(client::fetchRates, Rate::houseManageNo, maxPages, perPage);

            replace(scores, mapper::deleteScores, mapper::insertScores);
            replace(rates, mapper::deleteRates, mapper::insertRates);
            int scoreRows = scores.values().stream().mapToInt(List::size).sum();
            int rateRows = rates.values().stream().mapToInt(List::size).sum();
            return new Report(scoreRows, rateRows, scores.size(), full);
        } finally {
            running.set(false);
        }
    }

    /** 공고별로 묶는다(순서 유지). 다 받지 못했으면 마지막 공고는 잘렸을 수 있어 뺀다. */
    static <T> Map<String, List<T>> collect(BiFunction<Integer, Integer, Page<T>> fetch, Function<T, String> house,
                                            int maxPages, int perPage) {
        Map<String, List<T>> byHouse = new LinkedHashMap<>();
        int fetched = 0;
        int total = 0;
        String last = null;
        for (int page = 1; page <= maxPages; page++) {
            Page<T> p = fetch.apply(page, perPage);
            if (p.rows().isEmpty()) break;
            total = p.totalCount();
            for (T row : p.rows()) {
                last = house.apply(row);
                byHouse.computeIfAbsent(last, k -> new ArrayList<>()).add(row);
            }
            fetched += p.rows().size();
            if (fetched >= total) break;
        }
        if (last != null && fetched < total) byHouse.remove(last);
        return byHouse;
    }

    private static <T> void replace(Map<String, List<T>> byHouse,
                                    Function<List<String>, Integer> delete, Function<List<T>, Integer> insert) {
        List<String> houses = new ArrayList<>(byHouse.keySet());
        for (int i = 0; i < houses.size(); i += INSERT_CHUNK) {
            delete.apply(houses.subList(i, Math.min(i + INSERT_CHUNK, houses.size())));
        }
        List<T> all = byHouse.values().stream().flatMap(List::stream).toList();
        for (int i = 0; i < all.size(); i += INSERT_CHUNK) {
            insert.apply(all.subList(i, Math.min(i + INSERT_CHUNK, all.size())));
        }
    }
}
