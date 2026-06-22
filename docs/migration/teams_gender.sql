-- teams 테이블에 팀 성별 컬럼 추가. 팀은 동성으로 구성되므로 팀 단위 단일 성별을 가진다.
-- 1) nullable로 컬럼 추가
ALTER TABLE teams ADD COLUMN gender VARCHAR(50) NULL AFTER name;

-- 2) 기존 팀은 구성원(team_members) 성별로 백필. (팀은 동성 구성이라 아무 구성원이나 동일)
UPDATE teams t
SET t.gender = (
    SELECT tm.gender
    FROM team_members tm
    WHERE tm.team_id = t.id
    ORDER BY tm.id ASC
    LIMIT 1
)
WHERE t.gender IS NULL;

-- 3) 백필 후 NOT NULL 제약 적용
ALTER TABLE teams MODIFY COLUMN gender VARCHAR(50) NOT NULL;
