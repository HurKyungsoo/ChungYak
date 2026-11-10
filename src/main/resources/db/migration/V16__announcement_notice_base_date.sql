-- 공고문에 적힌 "입주자모집공고일" — 자격 판단 기준일. LH 정정·추가 공고는 목록의 공고일(PAN_DT)이 이번 공고를 올린 날이고
-- 공고문의 기준일은 원래 공고일인 경우가 있다(익산평화 추가 입주자모집: 목록 2026-08-12, 공고문 2025.03.05).
-- 매년 바뀌는 기준 금액(rule/YearlyFigure)과 판정 폼의 기간 계산이 이 날짜를 먼저 쓴다. NULL = 공고문에서 못 읽음 → 공고일. H2 / MariaDB 공용.
ALTER TABLE announcement ADD COLUMN notice_base_date DATE;
