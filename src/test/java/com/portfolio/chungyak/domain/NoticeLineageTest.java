package com.portfolio.chungyak.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 정정·취소공고 묶기 (#47). 제목·PAN_DT 는 2026-10-14 LH 라이브 목록에서 그대로 가져왔다.
 */
class NoticeLineageTest {

    private static String key(String panDt, String aisTpCd, String title) {
        return NoticeLineage.lineageKey("LH-", panDt, aisTpCd, title);
    }

    @Nested
    @DisplayName("kindOf — 제목 태그")
    class Kind {

        @Test
        void correctionTags() {
            assertThat(NoticeLineage.kindOf("[정정공고]대구연호 A-2BL, A-3BL 통합공공임대주택 입주자 모집"))
                    .isEqualTo(NoticeKind.CORRECTION);
            assertThat(NoticeLineage.kindOf("[정정공고](정정) 창녕영산 1BL 행복주택 예비입주자 모집 공고 ('26.10.07. 공고)"))
                    .isEqualTo(NoticeKind.CORRECTION);
            assertThat(NoticeLineage.kindOf("전주삼천6단지 50년공공임대주택 예비입주자 모집(정정공고)"))
                    .isEqualTo(NoticeKind.CORRECTION);
        }

        @Test
        void cancellation() {
            assertThat(NoticeLineage.kindOf("[취소공고]태백철암1 국민임대주택 예비입주자 모집 공고"))
                    .isEqualTo(NoticeKind.CANCELLATION);
        }

        @Test
        @DisplayName("지역·조건 태그와 본문 괄호는 정정·취소가 아니다")
        void otherTagsAreOriginal() {
            assertThat(NoticeLineage.kindOf("[경기남부] 신혼·신생아 매입임대주택Ⅰ 예비입주자 모집공고"))
                    .isEqualTo(NoticeKind.ORIGINAL);
            assertThat(NoticeLineage.kindOf("양산시 국민임대주택 예비입주자 완화 모집('26.09.15)"))
                    .isEqualTo(NoticeKind.ORIGINAL);
            // 긴 괄호는 본문 — "취소" 가 들어 있어도 태그로 보지 않는다
            assertThat(NoticeLineage.kindOf("○○ 행복주택 모집(당첨 취소 세대 포함, 소득·자산 요건 완화)"))
                    .isEqualTo(NoticeKind.ORIGINAL);
            assertThat(NoticeLineage.kindOf(null)).isEqualTo(NoticeKind.ORIGINAL);
        }
    }

    @Nested
    @DisplayName("lineageKey — 원래 공고와 정정·취소본이 같은 키")
    class Key {

        @Test
        void realPairsShareKey() {
            assertThat(key("20260930", "48", "[정정공고]대구연호 A-2BL, A-3BL 통합공공임대주택 입주자 모집"))
                    .isEqualTo(key("20260930", "48", "대구연호 A-2BL, A-3BL 통합공공임대주택 입주자 모집"));
            // 정정본이 태그를 더 달아도([조기접수마감]) 같은 계보
            assertThat(key("20260930", "07", "[정정공고][조기접수마감]양산 삼성파크빌 자격완화 입주자 선착순 모집(2026.09.30)"))
                    .isEqualTo(key("20260930", "07", "양산 삼성파크빌 자격완화 입주자 선착순 모집(2026.09.30)"));
            assertThat(key("20261007", "10", "[정정공고](정정) 창녕영산 1BL 행복주택 예비입주자 모집 공고 ('26.10.07. 공고)"))
                    .isEqualTo(key("20261007", "10", "창녕영산 1BL 행복주택 예비입주자 모집 공고 ('26.10.07. 공고)"));
            assertThat(key("20250831", "07", "[취소공고]태백철암1 국민임대주택 예비입주자 모집 공고"))
                    .isEqualTo(key("20250831", "07", "태백철암1 국민임대주택 예비입주자 모집 공고"));
        }

        @Test
        @DisplayName("최초 공고일·세부유형이 다르면 다른 계보 — 취소 뒤 다시 낸 공고는 살아 있다")
        void differentOriginalDateOrTypeIsDifferentLineage() {
            assertThat(key("20260914", "07", "태백철암1 국민임대주택 예비입주자 모집 공고"))
                    .isNotEqualTo(key("20260831", "07", "태백철암1 국민임대주택 예비입주자 모집 공고"));
            // 인천검단 AA19 영구임대(09)와 국민임대(07)는 같은 날 낸 다른 공고
            assertThat(key("20260820", "09", "인천검단 AA19블록 영구임대 최초 입주자 모집"))
                    .isNotEqualTo(key("20260820", "07", "인천검단 AA19블록 영구임대 최초 입주자 모집"));
        }

        @Test
        @DisplayName("최초 공고일이 없으면 묶지 않는다")
        void noOriginalDateNoKey() {
            assertThat(key("", "07", "태백철암1")).isNull();
            assertThat(key(null, "07", "태백철암1")).isNull();
            assertThat(key("20260831", "07", "태백철암1")).hasSize(64);
        }
    }

    @Nested
    @DisplayName("resolve — 계보 안 상태")
    class Resolve {

        private Announcement notice(String id, String title, LocalDate noticeDate, String providerStatus) {
            return Announcement.builder()
                    .externalId("LH-" + id).houseManageNo("02").pblancNo(id)
                    .houseName(title).houseType(HouseType.UNKNOWN).houseDetailType(HouseDetailType.PUBLIC)
                    .noticeDate(noticeDate)
                    .noticeKind(NoticeLineage.kindOf(title))
                    .providerStatus(providerStatus)
                    .build();
        }

        @Test
        @DisplayName("정정본이 최신, 원래 공고는 정정 전")
        void correctionSupersedesOriginal() {
            Announcement original = notice("2015122300020855", "대구연호 A-2BL, A-3BL 통합공공임대주택 입주자 모집",
                    LocalDate.of(2026, 9, 30), "정정공고중");
            Announcement corrected = notice("2015122300020878", "[정정공고]대구연호 A-2BL, A-3BL 통합공공임대주택 입주자 모집",
                    LocalDate.of(2026, 10, 6), "접수중");

            NoticeLineage.resolve(List.of(corrected, original));

            assertThat(original.getRevisionState()).isEqualTo(RevisionState.SUPERSEDED);
            assertThat(corrected.getRevisionState()).isEqualTo(RevisionState.CURRENT);
        }

        @Test
        @DisplayName("정정이 여러 번이면 가장 늦은 본만 최신")
        void chainOfCorrections() {
            Announcement first = notice("2015122300020760", "[정정공고]대구죽전 행복주택(산업단지형) 예비 입주자 모집",
                    LocalDate.of(2026, 9, 15), "접수마감");
            Announcement second = notice("2015122300020763", "[정정공고]대구죽전 행복주택(산업단지형) 예비 입주자 모집",
                    LocalDate.of(2026, 9, 15), "접수마감");
            Announcement third = notice("2015122300020813", "[정정공고]대구죽전 행복주택(산업단지형) 예비 입주자 모집",
                    LocalDate.of(2026, 9, 21), "접수마감");

            NoticeLineage.resolve(List.of(third, first, second));

            assertThat(List.of(first, second, third)).extracting(Announcement::getRevisionState)
                    .containsExactly(RevisionState.SUPERSEDED, RevisionState.SUPERSEDED, RevisionState.CURRENT);
        }

        @Test
        @DisplayName("취소공고는 자신과 원래 공고를 모두 내린다")
        void cancellationTakesDownOriginal() {
            Announcement original = notice("2015122300020638", "태백철암1 국민임대주택 예비입주자 모집 공고",
                    LocalDate.of(2026, 8, 31), "접수마감");
            Announcement cancel = notice("2015122300020645", "[취소공고]태백철암1 국민임대주택 예비입주자 모집 공고",
                    LocalDate.of(2026, 8, 31), "접수마감");

            NoticeLineage.resolve(List.of(original, cancel));

            assertThat(original.getRevisionState()).isEqualTo(RevisionState.CANCELLED);
            assertThat(cancel.getRevisionState()).isEqualTo(RevisionState.CANCELLED);
            assertThat(original.isCurrentRevision()).isFalse();
        }

        @Test
        @DisplayName("취소 뒤 같은 계보로 다시 낸 공고는 살린다")
        void reannouncementAfterCancelSurvives() {
            Announcement original = notice("A1", "태백철암1 국민임대", LocalDate.of(2026, 8, 31), null);
            Announcement cancel = notice("A2", "[취소공고]태백철암1 국민임대", LocalDate.of(2026, 9, 1), null);
            Announcement again = notice("A3", "태백철암1 국민임대", LocalDate.of(2026, 9, 11), null);

            NoticeLineage.resolve(List.of(original, cancel, again));

            assertThat(again.getRevisionState()).isEqualTo(RevisionState.CURRENT);
            assertThat(original.getRevisionState()).isEqualTo(RevisionState.CANCELLED);
        }

        @Test
        @DisplayName("혼자여도 원천이 '정정공고중'이면 정정 전 — 정정본 제목이 달라 안 묶인 경우")
        void providerSupersededAlone() {
            Announcement original = notice("B1", "창녕영산 1BL 행복주택 예비입주자 모집 공고",
                    LocalDate.of(2026, 10, 7), "정정공고중");
            Announcement plain = notice("B2", "춘천 행복주택", LocalDate.of(2026, 10, 7), "공고중");

            NoticeLineage.resolve(List.of(original));
            NoticeLineage.resolve(List.of(plain));

            assertThat(original.getRevisionState()).isEqualTo(RevisionState.SUPERSEDED);
            assertThat(plain.getRevisionState()).isEqualTo(RevisionState.CURRENT);
        }
    }

    @Test
    @DisplayName("화면 제목 — 정정·취소 태그만 떼고 다른 태그는 남긴다")
    void displayTitle() {
        assertThat(NoticeLineage.displayTitle("[정정공고][조기접수마감]양산 삼성파크빌"))
                .isEqualTo("[조기접수마감]양산 삼성파크빌");
        assertThat(NoticeLineage.displayTitle("대구연호 A-2BL 공공분양 (정정)")).isEqualTo("대구연호 A-2BL 공공분양");
        assertThat(NoticeLineage.displayTitle("단지 (소득, 자산, 무주택 요건 완화 정정)"))
                .isEqualTo("단지 (소득, 자산, 무주택 요건 완화 정정)");   // 긴 괄호는 본문
        assertThat(NoticeLineage.displayTitle("[정정공고]")).isEqualTo("[정정공고]");   // 다 떼면 빈 제목 — 원래대로
    }
}
