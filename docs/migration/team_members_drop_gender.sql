-- 팀 성별을 teams.gender(팀 단위)로 일원화하면서 구성원별 team_members.gender 컬럼을 제거한다.
-- (ddl-auto는 컬럼을 자동으로 DROP하지 않으므로, 남겨 두면 NOT NULL 컬럼에 값을 안 넣어 INSERT가 실패한다)
ALTER TABLE team_members DROP COLUMN gender;
