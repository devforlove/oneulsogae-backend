-- 일정(gathering_schedules)에 얼리버드 특가 남은 개수 컬럼을 추가한다.
-- 저장 시 early_bird_capacity로 초기화하고, 얼리버드 참가가 발생하면 차감한다. 특가가 없는 일정은 null.
ALTER TABLE gathering_schedules ADD COLUMN early_bird_remaining INT NULL AFTER early_bird_capacity;
-- 기존 행은 정원 값으로 남은 개수를 초기화한다.
UPDATE gathering_schedules SET early_bird_remaining = early_bird_capacity WHERE early_bird_capacity IS NOT NULL;
