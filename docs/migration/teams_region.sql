-- teams 테이블에 활동지역(region) + 권역 코드(region_code) 컬럼 추가 (NOT NULL).
-- 초대 시 요청으로 region을 받아 Region.resolveAreaCode로 region_code(1~5)를 산출해 저장한다.
-- 1) nullable로 추가
ALTER TABLE teams ADD COLUMN region VARCHAR(100) NULL AFTER gender;
ALTER TABLE teams ADD COLUMN region_code INT NULL AFTER region;

-- 2) 기존 팀 백필: region_code는 구성원(match_user) 권역으로, region 문자열은 원본이 없어 placeholder('미입력')로 둔다.
UPDATE teams t
SET t.region_code = COALESCE(
        (SELECT mu.region_code FROM team_members tm JOIN match_user mu ON mu.user_id = tm.user_id WHERE tm.team_id = t.id LIMIT 1),
        1
    ),
    t.region = '미입력'
WHERE t.region IS NULL OR t.region_code IS NULL;

-- 3) NOT NULL 적용
ALTER TABLE teams MODIFY COLUMN region VARCHAR(100) NOT NULL;
ALTER TABLE teams MODIFY COLUMN region_code INT NOT NULL;
