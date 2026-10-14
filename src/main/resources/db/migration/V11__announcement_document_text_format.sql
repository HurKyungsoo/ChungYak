-- 공고문 원문 추출 형식 (#42). 1 = 위치순 추출(표 칸이 섞인다), 2 = 표를 행 단위 문장으로 편 추출.
-- 기존 행은 모두 1 — 접수 전·중 공고는 다음 수집에서 PDF 를 다시 받아 2 로 바뀐다(AnnouncementSyncScheduler).
-- MariaDB / H2 MODE=MySQL 공용. INT 4B 추가라 행 상한과 무관하다.
ALTER TABLE announcement_document ADD COLUMN text_format INT NOT NULL DEFAULT 1;
