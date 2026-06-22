-- teams의 활동지역을 region_id(regions FK) 단일 컬럼으로 둔다.
-- (이전에 추가했던 region 문자열 / region_code(권역)는 제거 — 팀 매칭은 구성원 권역을 쓰므로 팀 단위 권역 불필요)
-- 초대 시 regionId로 받아 저장하고, 표시용 지역명은 응답 시 regions를 join해 내려준다.
ALTER TABLE teams ADD COLUMN region_id BIGINT NOT NULL DEFAULT 0 AFTER gender;
ALTER TABLE teams DROP COLUMN IF EXISTS region;
ALTER TABLE teams DROP COLUMN IF EXISTS region_code;
-- 기존 행은 활동지역 원본이 없어 region_id=0(placeholder). (regions 시드 후 재선택 대상)
