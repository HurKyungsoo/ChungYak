-- LH 주택형명(HTY_NM)이 50자를 넘는 공고가 있어 MariaDB 가 "Data too long" 으로 수집 전체를 중단시켰다.
-- MariaDB / H2 MODE=MySQL 공용.
ALTER TABLE unit_type MODIFY COLUMN type_name VARCHAR(200) NOT NULL;
