-- 사용자 신고 테이블. 신고자(from_user_id)가 대상(to_team_id 또는 to_user_id)을 사유(type)와 함께 신고한다.
-- 대상·채팅방(chat_room_id)·상세 설명(description)은 상황에 따라 없을 수 있어 nullable이다.
CREATE TABLE reports (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    type          VARCHAR(50)   NOT NULL,
    from_user_id  BIGINT        NOT NULL,
    chat_room_id  BIGINT        NULL,
    to_team_id    BIGINT        NULL,
    to_user_id    BIGINT        NULL,
    description   VARCHAR(1000) NULL,
    created_at    DATETIME(6)   NOT NULL,
    updated_at    DATETIME(6)   NOT NULL,
    deleted_at    DATETIME(6)   NULL,
    PRIMARY KEY (id)
);
