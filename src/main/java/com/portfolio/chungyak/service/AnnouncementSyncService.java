package com.portfolio.chungyak.service;

import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.domain.UnitType;
import com.portfolio.chungyak.external.ExternalAnnouncement;
import com.portfolio.chungyak.external.ExternalUnitType;
import com.portfolio.chungyak.repository.AnnouncementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 수집 -> 정규화 -> upsert.
 *
 * 주택형은 공고당 API 를 한 번 더 호출해야 해서, 신규 공고에만 호출한다.
 * (KOPIS 상세 조회와 같은 패턴 — 트래픽 절약)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnnouncementSyncService {

    private final AnnouncementRepository announcementRepository;

    @Transactional
    public SyncStat sync(List<ExternalAnnouncement> externals) {
        int created = 0;
        int updated = 0;

        for (ExternalAnnouncement external : externals) {
            Announcement existing = announcementRepository
                    .findByExternalId(external.getExternalId())
                    .orElse(null);

            if (existing == null) {
                Announcement saved = announcementRepository.save(toEntity(external));
                attachUnitTypes(saved, external.getUnitTypes());
                created++;
            } else {
                existing.updateFromExternal(
                        external.getHouseName(), external.getRegionName(), external.getAddress(),
                        external.getTotalSupplyCount(), external.getReceptBeginDate(),
                        external.getReceptEndDate(), external.getWinnerAnnounceDate(),
                        external.getRegulationFlags());
                updated++;
            }
        }

        return new SyncStat(externals.size(), created, updated);
    }

    private Announcement toEntity(ExternalAnnouncement e) {
        return Announcement.builder()
                .externalId(e.getExternalId())
                .houseManageNo(e.getHouseManageNo())
                .pblancNo(e.getPblancNo())
                .houseName(e.getHouseName())
                .houseType(e.getHouseType())
                .houseDetailType(e.getHouseDetailType())
                .regionName(e.getRegionName())
                .regionCode(e.getRegionCode())
                .address(e.getAddress())
                .zipCode(e.getZipCode())
                .totalSupplyCount(e.getTotalSupplyCount())
                .noticeDate(e.getNoticeDate())
                .receptBeginDate(e.getReceptBeginDate())
                .receptEndDate(e.getReceptEndDate())
                .specialReceptBeginDate(e.getSpecialReceptBeginDate())
                .specialReceptEndDate(e.getSpecialReceptEndDate())
                .winnerAnnounceDate(e.getWinnerAnnounceDate())
                .regulationFlags(e.getRegulationFlags())
                .noticeUrl(e.getNoticeUrl())
                .homepageUrl(e.getHomepageUrl())
                .inquiryTel(e.getInquiryTel())
                .developerName(e.getDeveloperName())
                .constructorName(e.getConstructorName())
                .moveInYearMonth(e.getMoveInYearMonth())
                .build();
    }

    private void attachUnitTypes(Announcement announcement, List<ExternalUnitType> externals) {
        for (ExternalUnitType e : externals) {
            announcement.addUnitType(UnitType.builder()
                    .modelNo(e.getModelNo())
                    .typeName(e.getTypeName())
                    .supplyArea(e.getSupplyArea())
                    .generalSupplyCount(e.getGeneralSupplyCount())
                    .specialSupplyCount(e.getSpecialSupplyCount())
                    .supplyBreakdown(e.getSupplyBreakdown())
                    .topAmount(e.getTopAmount())
                    .build());
        }
    }

    public record SyncStat(int received, int created, int updated) {}
}
