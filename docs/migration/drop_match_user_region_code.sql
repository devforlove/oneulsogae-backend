-- match_user.region_code 제거. 팀 추천 배치가 근접(region_id) 방식으로 전환되면서
-- 더 이상 권역코드를 쓰는 매칭 경로가 없어 컬럼·인덱스를 모두 제거한다.
-- (이전 match_user_region_id_index.sql 주석의 "2:2 매칭이 계속 사용" 전제는 폐기됨)
ALTER TABLE match_user DROP INDEX idx_gender_region_code_last_login_at;
ALTER TABLE match_user DROP COLUMN region_code;
