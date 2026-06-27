-- 팀 매칭 헤더에 낙관적 락 버전 컬럼 추가. 헤더+참가 팀(matched_teams)으로 이뤄진 팀 매칭 애그리거트에 대한
-- 동시 변경(예: 팀 탈퇴 ↔ 관심/수락)을 헤더 한 행의 낙관적 락으로 직렬화하기 위함. 기존 행은 0으로 채운다.
ALTER TABLE team_matches
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
