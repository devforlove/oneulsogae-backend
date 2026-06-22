-- 솔로 유저 대상 팀 추천(RecommendedTeam) 테이블. 유저당 1행(user_id 유니크)으로 일일 배치가 교체(upsert)한다.
CREATE TABLE recommended_teams (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    user_id          BIGINT       NOT NULL,
    team_id          BIGINT       NOT NULL,
    recommended_date DATE         NOT NULL,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    deleted_at       DATETIME(6)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT ux_user_id UNIQUE (user_id)
);
