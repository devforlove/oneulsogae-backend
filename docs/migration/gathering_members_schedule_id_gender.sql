-- 참가를 일정(회차) 단위로 전환한다: schedule_id·gender·early_bird_applied 컬럼 추가, 유니크 제약을 (schedule_id, user_id)로 변경.
-- gathering_members는 아직 어떤 기능도 쓰지 않는 빈 테이블이므로 NOT NULL 추가가 안전하다.
ALTER TABLE gathering_members ADD COLUMN schedule_id BIGINT NOT NULL AFTER gathering_id;
ALTER TABLE gathering_members ADD COLUMN gender VARCHAR(50) NOT NULL AFTER user_id;
ALTER TABLE gathering_members ADD COLUMN early_bird_applied BOOLEAN NOT NULL DEFAULT FALSE AFTER gender;
ALTER TABLE gathering_members DROP INDEX ux_gathering_id_user_id;
ALTER TABLE gathering_members ADD UNIQUE INDEX ux_schedule_id_user_id (schedule_id, user_id);
