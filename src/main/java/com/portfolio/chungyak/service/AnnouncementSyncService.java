package com.portfolio.chungyak.service;

import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.domain.AnnouncementDocument;
import com.portfolio.chungyak.domain.RegulationFlags;
import com.portfolio.chungyak.domain.UnitType;
import com.portfolio.chungyak.external.ExternalAnnouncement;
import com.portfolio.chungyak.external.ExternalUnitType;
import com.portfolio.chungyak.repository.AnnouncementDocumentRepository;
import com.portfolio.chungyak.repository.AnnouncementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
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
    private final AnnouncementDocumentRepository documentRepository;
    private final Clock clock;

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
                saveNoticeContent(saved, external);
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
                // 임베디드 값이 null 이면 저장 시 NOT NULL 위반 — 소스가 안 주면 전부 false(비규제)로
                .regulationFlags(e.getRegulationFlags() != null
                        ? e.getRegulationFlags() : RegulationFlags.builder().build())
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

    /**
     * 공고문 원문을 별도 테이블에 저장 — 벡터 검색(RAG) 소스.
     * 원문이 없으면(대부분의 청약홈 공고) 아무것도 안 한다. 인덱싱은 별도 배치가 한다.
     */
    private void saveNoticeContent(Announcement saved, ExternalAnnouncement external) {
        String content = external.getNoticeContent();
        if (content == null || content.isBlank()) return;

        String source = external.getExternalId() != null && external.getExternalId().startsWith("LH")
                ? "LH" : "APPLYHOME";
        documentRepository.findByAnnouncementId(saved.getId())
                .ifPresentOrElse(
                        doc -> doc.replaceText(content, clock.instant()),
                        () -> documentRepository.save(new AnnouncementDocument(
                                saved.getId(), source, content, clock.instant())));
    }

    public record SyncStat(int received, int created, int updated) {}
}
