-- 공고 구분(분양/임대/신혼희망타운) + LH 세부 유형명 + 공고문 PDF 링크. (#18)
-- MariaDB / H2 MODE=MySQL 공용. announcement 행은 VARCHAR 합계 약 2,430자(utf8mb4 ≈ 9.7KB)라
-- 이 세 컬럼(+550자)을 더해도 행 상한 65,535B 에 한참 못 미친다.
ALTER TABLE announcement ADD COLUMN supply_category VARCHAR(20);
ALTER TABLE announcement ADD COLUMN supply_type_name VARCHAR(30);
ALTER TABLE announcement ADD COLUMN notice_pdf_url VARCHAR(500);

-- 이미 아는 것만 채운다. 나머지 LH 공고(분양/임대)는 다음 수집에서 목록 응답 코드로 채워진다.
UPDATE announcement SET supply_category = 'HOPE_TOWN' WHERE house_type = 'NEWLYWED_HOPE_TOWN';
-- 청약홈은 APT 분양정보 API 만 수집한다 — 전부 분양.
UPDATE announcement SET supply_category = 'SALE'
 WHERE supply_category IS NULL AND external_id NOT LIKE 'LH-%';
