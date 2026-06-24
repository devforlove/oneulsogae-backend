-- chat_rooms.match_id는 solo_matches.id와 team_matches.id를 함께 가리키는 다형성 참조다.
-- 두 테이블이 독립 AUTO_INCREMENT라 같은 id를 내면 ux_match_id(단일 컬럼 유니크)가 충돌해 채팅방 생성 시 duplicate key 에러가 났다.
-- match_type 판별 컬럼을 추가하고 유니크를 (match_type, match_id) 복합으로 바꿔, 타입별로 독립적으로 "매칭당 1방"을 보장한다.
-- 기존 채팅방은 모두 1:1(solo) 매칭에서 생성됐으므로 DEFAULT 'SOLO'로 백필된다.
ALTER TABLE chat_rooms
    ADD COLUMN match_type VARCHAR(20) NOT NULL DEFAULT 'SOLO' AFTER match_id;

ALTER TABLE chat_rooms
    DROP INDEX ux_match_id,
    ADD CONSTRAINT ux_match_type_match_id UNIQUE (match_type, match_id);
