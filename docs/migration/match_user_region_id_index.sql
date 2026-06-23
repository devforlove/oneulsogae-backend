-- match_user: 거리 기반 1:1 매칭 온보딩 후보 조회(gender=, region_id IN(...), last_login_at>=) seek용 인덱스.
-- 기존 idx_gender_region_code_last_login_at 은 팀(2:2) 매칭이 계속 사용하므로 유지한다.
ALTER TABLE match_user
    ADD INDEX idx_gender_region_id_last_login_at (gender, region_id, last_login_at);
