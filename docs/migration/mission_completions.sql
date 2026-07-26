-- 미션 완료 기록 테이블. 회원이 특정 미션을 완료하고 코인을 받은 이력을 저장한다.
-- (user_id, mission_id) 유니크가 회원당 동일 미션 1회 수령을 원자적으로 막는다(이중수령 가드).
CREATE TABLE mission_completions (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    user_id       BIGINT      NOT NULL,
    mission_id    BIGINT      NOT NULL,
    rewarded_coin INT         NOT NULL,
    created_at    DATETIME(6) NOT NULL,
    updated_at    DATETIME(6) NOT NULL,
    deleted_at    DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY ux_mission_completions_user_mission (user_id, mission_id)
);
