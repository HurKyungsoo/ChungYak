-- LH 공고는 오픈 API 가 특별공급 유형별 세대수를 주지 않는다.
-- countsKnown=false 면 SupplyBreakdown 의 각 필드는 세대수가 아니라 "유형 존재"(1/0)만 뜻한다.
-- 기존 행(청약홈)은 실제 세대수이므로 TRUE.
ALTER TABLE unit_type ADD COLUMN counts_known BOOLEAN NOT NULL DEFAULT TRUE;
