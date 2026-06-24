-- match_user: 온보딩 후보 조회(gender=, region_id=, last_login_at>=)의 ORDER BY last_login_at DESC LIMIT 1을
-- backward index scan 없이 forward scan으로 처리하도록 last_login_at을 내림차순으로 둔다.
-- (이 인덱스의 유일한 소비자가 findFreshCandidateInRegion이고 항상 최근 로그인순 1명을 뽑는다)
ALTER TABLE match_user
    DROP INDEX idx_gender_region_id_last_login_at,
    ADD INDEX idx_gender_region_id_last_login_at (gender, region_id, last_login_at DESC);
