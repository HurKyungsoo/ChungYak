-- 신혼희망타운(HOUSE_SECD=10) 특별공급은 표준 신혼부부 특공과 자격기준(소득·자산·통장 요건,
-- 혼인기간 예외)이 달라 SpecialSupplyType.NEWLYWED 와 분리해서 센다.
-- 기존 행은 전부 0 (아직 반영 전 데이터이므로 재-sync 로 채워짐).
ALTER TABLE unit_type ADD COLUMN newlywed_hope_town INT NOT NULL DEFAULT 0;
