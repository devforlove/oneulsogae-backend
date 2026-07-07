-- 모임 일정을 gathering_schedules로 분리하면서 gatherings의 단일 일시 컬럼(gathering_at)을 제거한다.
-- 정렬 컬럼이 사라지므로 (status, type, gathering_at) 인덱스도 (status, type)로 축소한다.
ALTER TABLE gatherings DROP INDEX idx_status_type_gathering_at;
ALTER TABLE gatherings ADD INDEX idx_status_type (status, type);
ALTER TABLE gatherings DROP COLUMN gathering_at;
