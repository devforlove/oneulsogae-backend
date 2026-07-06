-- 참가비를 모임(gatherings)에서 일정(gathering_schedules)으로 옮기면서 일정에 성별·티어별 참가비 컬럼을 추가한다.
-- 정상가(남/녀)는 필수(NOT NULL), 얼리버드 특가(남/녀+인원)·할인가(남/녀)는 선택(NULL).
ALTER TABLE gathering_schedules ADD COLUMN male_fee INT NOT NULL DEFAULT 0 AFTER end_at;
ALTER TABLE gathering_schedules ADD COLUMN female_fee INT NOT NULL DEFAULT 0 AFTER male_fee;
ALTER TABLE gathering_schedules ADD COLUMN early_bird_male_fee INT NULL AFTER female_fee;
ALTER TABLE gathering_schedules ADD COLUMN early_bird_female_fee INT NULL AFTER early_bird_male_fee;
ALTER TABLE gathering_schedules ADD COLUMN early_bird_capacity INT NULL AFTER early_bird_female_fee;
ALTER TABLE gathering_schedules ADD COLUMN discount_male_fee INT NULL AFTER early_bird_capacity;
ALTER TABLE gathering_schedules ADD COLUMN discount_female_fee INT NULL AFTER discount_male_fee;
