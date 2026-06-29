-- 만료 정리 배치의 조회(status IN (...) AND expires_at < now)를 인덱스 seek로 받친다.
--   동등 조건(status) → 범위 조건(expires_at) 순서의 복합 인덱스.
ALTER TABLE solo_matches
    ADD INDEX idx_status_expires_at (status, expires_at);

ALTER TABLE team_matches
    ADD INDEX idx_status_expires_at (status, expires_at);
