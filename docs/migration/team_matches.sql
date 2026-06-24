-- 2:2 팀 매칭 헤더 테이블. 결성된 두 팀을 소개로 묶으며, 팀 조합 정규화 키(member_key)에 유니크 제약을 걸어 중복 소개를 차단한다.
CREATE TABLE team_matches (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    member_key          VARCHAR(255) NOT NULL,
    introduced_date     DATE         NOT NULL,
    expires_at          DATETIME(6)  NOT NULL,
    status              VARCHAR(50)  NOT NULL,
    match_type          VARCHAR(50)  NOT NULL,
    date_init_amount    INT          NOT NULL,
    date_accept_amount  INT          NOT NULL,
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    deleted_at          DATETIME(6)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT ux_member_key UNIQUE (member_key)
);
