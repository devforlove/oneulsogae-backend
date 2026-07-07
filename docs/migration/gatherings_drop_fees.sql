-- 참가비를 일정(gathering_schedules)으로 옮기면서 모임(gatherings)의 참가비 컬럼을 제거한다.
-- (선 실행: gathering_schedules_add_fees.sql 로 일정에 컬럼 추가 + 기존 데이터 이관 후 제거)
ALTER TABLE gatherings DROP COLUMN male_fee;
ALTER TABLE gatherings DROP COLUMN female_fee;
ALTER TABLE gatherings DROP COLUMN early_bird_male_fee;
ALTER TABLE gatherings DROP COLUMN early_bird_female_fee;
ALTER TABLE gatherings DROP COLUMN early_bird_capacity;
ALTER TABLE gatherings DROP COLUMN discount_male_fee;
ALTER TABLE gatherings DROP COLUMN discount_female_fee;
