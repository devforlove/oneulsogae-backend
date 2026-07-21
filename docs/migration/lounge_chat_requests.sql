-- 라운지 셀소 대화 신청. 신청자가 코인을 내고 글 작성자에게 대화를 신청한 한 건을 보관한다.
-- 글 작성자(수신자)는 lounge_posts.user_id가 단일 진실원천이라 여기에 복사하지 않는다.
-- 생성된 채팅방도 컬럼으로 두지 않는다 — chat_rooms(match_type='LOUNGE', match_id=이 행의 id)로 역참조한다.
CREATE TABLE lounge_chat_requests
(
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    post_id           BIGINT      NOT NULL COMMENT '대상 셀소 글(lounge_posts.id)',
    requester_user_id BIGINT      NOT NULL COMMENT '대화를 신청한 사용자',
    status            VARCHAR(20) NOT NULL COMMENT 'PENDING / ACCEPTED',
    created_at        DATETIME(6) NOT NULL,
    updated_at        DATETIME(6) NOT NULL,
    deleted_at        DATETIME(6) NULL,
    PRIMARY KEY (id),
    -- 같은 글에 같은 사용자가 두 번 신청하지 못하게 막는 최종 방어선(분산 락을 뚫고 들어온 동시 요청 대비).
    CONSTRAINT ux_post_requester UNIQUE (post_id, requester_user_id),
    -- 글별 신청 목록을 최신순(id desc)으로 seek. 동등 조건(post_id) → 정렬 컬럼(id) 순서.
    INDEX idx_post_id_id (post_id, id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
