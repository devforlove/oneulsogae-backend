-- 참가자가 매칭(소개)을 언제 확인했는지 기록하기 위해 checked_at 컬럼을 추가한다.
-- 목록 조회로 처음 확인될 때 채워지며, 미확인 상태는 NULL로 남는다.
ALTER TABLE solo_match_members ADD COLUMN checked_at DATETIME(6) NULL;
