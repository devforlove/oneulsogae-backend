-- 모임(gatherings)의 일정(세션) 테이블. 한 모임이 여러 일정을 가질 수 있어 gatherings : gathering_schedules = 1 : N이다.
-- 시간 범위는 start_at(필수)·end_at(선택, 미정 가능)으로, 진행 상태는 status(SCHEDULED/ONGOING/COMPLETED/CANCELED)로 보관한다.
CREATE TABLE gathering_schedules (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    gathering_id  BIGINT       NOT NULL,
    start_at      DATETIME(6)  NOT NULL,
    end_at        DATETIME(6)  NULL,
    status        VARCHAR(50)  NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    deleted_at    DATETIME(6)  NULL,
    PRIMARY KEY (id),
    -- 모임별 일정을 시작 시각순으로 조회하기 위한 인덱스. (gathering_id 동등 조건 + start_at 정렬)
    INDEX idx_gathering_id_start_at (gathering_id, start_at)
);
