-- 팀 헤더에 낙관적 락 버전 컬럼 추가. 같은 팀에 대한 동시 변경(예: 초대받은 사람의 수락 ↔ 초대자의 철회)을
-- teams 한 행의 낙관적 락으로 직렬화하기 위함. 수락은 userId 락, 철회·해체·수정은 teamId 락이라 락 키가 달라
-- 서로 배제되지 않으므로, 같은 팀 행에 동시에 쓰는 경합은 이 버전으로 막는다. 기존 행은 0으로 채운다.
ALTER TABLE teams
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
