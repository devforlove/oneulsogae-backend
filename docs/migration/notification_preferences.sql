-- notification_preferences: 사용자별 알림 설정. 유저당 1행(user_id 유니크)으로 설정 저장 API가 교체(upsert)한다.
-- push 마스터 + 카테고리 5종(one_to_one/meeting/team/message/marketing). 알림톡 전송 게이트가 이 값을 읽어 발송 여부를 정한다.
-- 행이 없는 유저는 앱에서 기본값으로 간주한다(별도 시드 없음). 소프트 삭제는 쓰지 않으나 BaseEntity 컬럼은 그대로 둔다.
CREATE TABLE notification_preferences (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    push       BIT(1)      NOT NULL,
    one_to_one BIT(1)      NOT NULL,
    meeting    BIT(1)      NOT NULL,
    team       BIT(1)      NOT NULL,
    message    BIT(1)      NOT NULL,
    marketing  BIT(1)      NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT ux_user_id UNIQUE (user_id)
);
