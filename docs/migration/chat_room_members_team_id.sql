-- chat_room_members: TEAM 채팅방에서 내 팀/상대 팀 구분을 위해 생성 시점의 team_id를 스냅샷으로 보관한다.
-- SOLO 방은 NULL. 목록 조회는 같은 방의 내 참가자 행과 team_id를 비교해(self-join, ux_chat_room_id_user_id로 seek) 상대 팀만 노출한다.
-- team_id는 seek 키가 아니라 비교 컬럼이라 별도 인덱스를 두지 않는다. (운영에 기존 TEAM 채팅방이 없어 백필 불필요)
ALTER TABLE chat_room_members
    ADD COLUMN team_id BIGINT NULL;
