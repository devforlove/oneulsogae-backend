-- teams 테이블에 활동지역(region) + 권역 코드(region_code) 컬럼 추가.
-- 초대 시 요청으로 region을 받아 Region.resolveAreaCode로 region_code(1~5)를 산출해 저장한다.
-- 기존 행은 활동지역 정보가 없어 NULL로 둔다. (nullable — UserDetail의 activity_area/region_code와 동일 패턴)
ALTER TABLE teams ADD COLUMN region VARCHAR(100) NULL AFTER gender;
ALTER TABLE teams ADD COLUMN region_code INT NULL AFTER region;
