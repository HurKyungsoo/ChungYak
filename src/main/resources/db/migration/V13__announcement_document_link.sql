-- 청약홈에 올라온 LH 공급 공고(신혼희망타운·공공분양)의 공고문을 LH 쪽 같은 공고에서 빌려 쓴다.
-- 청약홈 공고 페이지에는 PDF 가 없고 LH 청약플러스로 링크만 걸려 있는데, 같은 공고가 LH 수집에도 있고 거기엔 공고문이 있다.
-- 원문·청크를 복사하지 않고 공고문 주인 공고 id 만 가리킨다(임베딩·메모리 두 벌 방지). null = 자기 공고문(또는 없음).
-- MariaDB / H2 MODE=MySQL 공용. BIGINT 8B 추가라 행 상한과 무관하다.
ALTER TABLE announcement ADD COLUMN document_announcement_id BIGINT;
