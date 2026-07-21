-- 라운지 셀소 대화 신청. 신청자가 코인을 내고 글 작성자에게 대화를 신청한 한 건을 보관한다.
-- 생성된 채팅방은 컬럼으로 두지 않는다 — chat_rooms(match_type='LOUNGE', match_id=이 행의 id)로 역참조한다.
-- receiver_user_id는 lounge_posts.user_id를 비정규화한 값이다. 받은/보낸 신청 목록이 모두 글과 무관하게
-- "한 사용자의 신청을 최신순으로" 훑는 조회라, 조인 없이 (사용자, id desc)를 인덱스로 seek+정렬하려면 필요하다.
-- (글 작성자는 바뀌지 않으므로 복사 저장해도 원본과 어긋나지 않는다)
CREATE TABLE lounge_chat_requests
(
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    post_id           BIGINT      NOT NULL COMMENT '대상 셀소 글(lounge_posts.id)',
    requester_user_id BIGINT      NOT NULL COMMENT '대화를 신청한 사용자',
    receiver_user_id  BIGINT      NOT NULL COMMENT '신청을 받은 사용자(글 작성자, lounge_posts.user_id 비정규화)',
    status            VARCHAR(20) NOT NULL COMMENT 'PENDING / ACCEPTED',
    created_at        DATETIME(6) NOT NULL,
    updated_at        DATETIME(6) NOT NULL,
    deleted_at        DATETIME(6) NULL,
    PRIMARY KEY (id),
    -- 같은 글에 같은 사용자가 두 번 신청하지 못하게 막는 최종 방어선(분산 락을 뚫고 들어온 동시 요청 대비).
    CONSTRAINT ux_post_requester UNIQUE (post_id, requester_user_id),
    -- 내가 받은 신청 목록(최신순). 동등 조건 → 정렬 컬럼 순서.
    INDEX idx_receiver_user_id_id (receiver_user_id, id),
    -- 내가 보낸 신청 목록(최신순). ux_post_requester는 선두가 post_id라 requester_user_id로 seek할 수 없어 따로 둔다.
    INDEX idx_requester_user_id_id (requester_user_id, id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
