-- 정정·취소공고 묶기 (#47). LH 는 정정·취소를 새 PAN_ID + 제목 태그로 다시 올려 원래 공고와 나란히 보였다.
-- MariaDB / H2 MODE=MySQL 공용. 추가분 VARCHAR 124자(utf8mb4 ≈ 496B) — 행 상한 65,535B 에 한참 못 미친다.
-- lineage_key 는 SHA-256 hex 64자라 인덱스 길이(3,072B)도 안전하다.
ALTER TABLE announcement ADD COLUMN notice_kind VARCHAR(20) NOT NULL DEFAULT 'ORIGINAL';
ALTER TABLE announcement ADD COLUMN lineage_key VARCHAR(64);
ALTER TABLE announcement ADD COLUMN provider_status VARCHAR(20);
ALTER TABLE announcement ADD COLUMN revision_state VARCHAR(20) NOT NULL DEFAULT 'CURRENT';
CREATE INDEX idx_announcement_lineage ON announcement (lineage_key);

-- 취소공고 자신은 지금 바로 내린다. 계보 키·원래 공고의 상태는 다음 수집에서 목록 응답으로 채워진다.
UPDATE announcement SET notice_kind = 'CANCELLATION', revision_state = 'CANCELLED'
 WHERE external_id LIKE 'LH-%' AND (house_name LIKE '%[취소공고]%' OR house_name LIKE '%[공고취소]%');
