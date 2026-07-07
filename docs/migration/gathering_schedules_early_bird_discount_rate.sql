-- 얼리버드 참가비를 남/녀 금액(early_bird_male_fee·early_bird_female_fee)이 아니라
-- 정상가에 곱하는 할인율(%)로 저장하도록 바꾼다. 얼리버드 금액 = 정상가 × (100 - 할인율) / 100 (응답에서 계산).
-- early_bird_capacity·early_bird_remaining(적용 인원·남은 개수)은 그대로 둔다.
ALTER TABLE gathering_schedules ADD COLUMN early_bird_discount_rate INT NULL AFTER female_fee;
ALTER TABLE gathering_schedules DROP COLUMN early_bird_male_fee;
ALTER TABLE gathering_schedules DROP COLUMN early_bird_female_fee;
