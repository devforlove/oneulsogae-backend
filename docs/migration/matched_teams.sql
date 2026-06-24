-- 팀 매칭(team_matches) ↔ 팀(teams) 연결(join) 테이블. 한 매칭의 참가 팀(두 행)과 팀별 상태(WAITING/APPLY/ACTIVE/DEACTIVE)를 보관한다.
CREATE TABLE matched_teams (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    team_match_id   BIGINT       NOT NULL,
    team_id         BIGINT       NOT NULL,
    status          VARCHAR(50)  NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    deleted_at      DATETIME(6)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT ux_team_match_id_team_id UNIQUE (team_match_id, team_id),
    INDEX idx_team_id (team_id)
);
