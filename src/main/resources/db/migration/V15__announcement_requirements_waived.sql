-- 공고문이 "소득·자산(·청약저축·당첨 이력) 불문"이라고 밝힌 공고 — LH 잔여세대·추가 입주자모집 등.
-- 특별공급 소득·자산 규칙으로 판정하면 틀린 결론("자산 초과로 불가")이 나오므로 판정 대상에서 뺀다(Announcement.isEligibilityTarget).
-- NULL = 아직 확인 안 함(공고문 없음 포함). 공고문 저장 때와 기동 때(기존 원문 백필) 채운다. H2 / MariaDB 공용.
ALTER TABLE announcement ADD COLUMN requirements_waived BOOLEAN;
