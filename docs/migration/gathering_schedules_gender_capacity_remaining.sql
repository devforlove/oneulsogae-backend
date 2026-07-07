-- 일정(gathering_schedules)에 남/녀 정원과 여분(남은 자리) 컬럼을 추가한다.
-- 정원(male_capacity·female_capacity)은 필수, 여분(male_remaining·female_remaining)은 저장 시 각 성별 정원으로 초기화하고
-- 남/녀가 참여하면 해당 여분을 차감한다.
ALTER TABLE gathering_schedules ADD COLUMN male_capacity INT NOT NULL DEFAULT 0 AFTER female_fee;
ALTER TABLE gathering_schedules ADD COLUMN female_capacity INT NOT NULL DEFAULT 0 AFTER male_capacity;
ALTER TABLE gathering_schedules ADD COLUMN male_remaining INT NOT NULL DEFAULT 0 AFTER female_capacity;
ALTER TABLE gathering_schedules ADD COLUMN female_remaining INT NOT NULL DEFAULT 0 AFTER male_remaining;
-- 기존 행은 여분을 각 성별 정원 값으로 초기화한다.
UPDATE gathering_schedules SET male_remaining = male_capacity, female_remaining = female_capacity;
