-- matched_teams: 팀이 신청(APPLY)할 때 코인을 지불한 구성원 userId를 기록한다.
--   미성사 만료 시 이 컬럼으로 환불 대상(지불자)을 식별한다. (기존 행은 NULL → 소급 환불 대상 아님)
ALTER TABLE matched_teams
    ADD COLUMN applicant_user_id BIGINT NULL;
