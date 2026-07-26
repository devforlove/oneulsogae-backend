-- 미션 정의 테이블. 미션 종류(type)별 보상 코인·노출 정보를 관리한다.
-- type 유니크가 동일 미션 종류의 중복 정의를 막는다.
CREATE TABLE missions (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    type          VARCHAR(50)  NOT NULL,
    reward_coin   INT          NOT NULL,
    title         VARCHAR(100) NOT NULL,
    description   VARCHAR(255) NULL,
    active        TINYINT(1)   NOT NULL DEFAULT 1,
    display_order INT          NOT NULL DEFAULT 0,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    deleted_at    DATETIME(6)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY ux_missions_type (type)
);
