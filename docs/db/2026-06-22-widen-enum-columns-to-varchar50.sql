-- 모든 enum 컬럼을 varchar(50)로 넓힌다.
--
-- 배경: 스키마는 Hibernate ddl-auto(update/create-drop)로 관리되며, enum은 @Enumerated(STRING) → VARCHAR로 저장된다.
-- ddl-auto:update 는 "새 컬럼/테이블 추가"만 하고 기존 컬럼 타입을 넓혀주지 않으므로,
-- 과거에 좁게(varchar(10/20/30) 또는 현재 enum 최댓값 길이) 생성된 컬럼은 새 enum 값이 추가되면
-- "Data truncated for column '…'" 오류가 난다. 엔티티 매핑은 varchar(50)로 통일했고,
-- 이미 실행 중인 DB에는 이 스크립트로 기존 컬럼을 한 번 넓혀준다. (이후 enum 값 추가는 50자 이내면 DDL 불필요)
--
-- 주의: MODIFY COLUMN 은 NOT NULL/DEFAULT 를 보존하지 않으므로 NOT NULL 컬럼은 아래처럼 명시한다.
--       (이 컬럼들은 DB DEFAULT 없이 애플리케이션에서 값을 채우므로 DEFAULT 는 두지 않는다)
-- 실행 전 대상 스키마(USE …)를 확인할 것.

-- ── NOT NULL enum 컬럼 ──────────────────────────────────────────────
ALTER TABLE match_user         MODIFY COLUMN gender          VARCHAR(50) NOT NULL;
ALTER TABLE match_user         MODIFY COLUMN marital_status  VARCHAR(50) NOT NULL;
ALTER TABLE users              MODIFY COLUMN role            VARCHAR(50) NOT NULL;
ALTER TABLE users              MODIFY COLUMN status          VARCHAR(50) NOT NULL;
ALTER TABLE solo_matches       MODIFY COLUMN status          VARCHAR(50) NOT NULL;
ALTER TABLE solo_matches       MODIFY COLUMN match_type      VARCHAR(50) NOT NULL;
ALTER TABLE solo_match_members MODIFY COLUMN gender          VARCHAR(50) NOT NULL;
ALTER TABLE solo_match_members MODIFY COLUMN status          VARCHAR(50) NOT NULL;
ALTER TABLE teams              MODIFY COLUMN status          VARCHAR(50) NOT NULL;
ALTER TABLE team_members       MODIFY COLUMN gender          VARCHAR(50) NOT NULL;
ALTER TABLE team_members       MODIFY COLUMN status          VARCHAR(50) NOT NULL;
ALTER TABLE team_matches       MODIFY COLUMN status          VARCHAR(50) NOT NULL;
ALTER TABLE team_matches       MODIFY COLUMN match_type      VARCHAR(50) NOT NULL;
ALTER TABLE chat_messages      MODIFY COLUMN type            VARCHAR(50) NOT NULL;
ALTER TABLE chat_rooms         MODIFY COLUMN status          VARCHAR(50) NOT NULL;
ALTER TABLE chat_room_members  MODIFY COLUMN status          VARCHAR(50) NOT NULL;
ALTER TABLE popups             MODIFY COLUMN popup_type      VARCHAR(50) NOT NULL;
ALTER TABLE alarms             MODIFY COLUMN type            VARCHAR(50) NOT NULL;

-- ── NULLABLE enum 컬럼 ──────────────────────────────────────────────
ALTER TABLE user_details       MODIFY COLUMN gender          VARCHAR(50) NULL;
ALTER TABLE user_details       MODIFY COLUMN marital_status  VARCHAR(50) NULL;
ALTER TABLE user_details       MODIFY COLUMN smoking_status  VARCHAR(50) NULL;
ALTER TABLE user_details       MODIFY COLUMN religion        VARCHAR(50) NULL;
ALTER TABLE user_details       MODIFY COLUMN drinking_status VARCHAR(50) NULL;
ALTER TABLE user_details       MODIFY COLUMN body_type       VARCHAR(50) NULL;
ALTER TABLE coin_histories     MODIFY COLUMN coin_get_type   VARCHAR(50) NULL;
ALTER TABLE coin_histories     MODIFY COLUMN coin_usage_type VARCHAR(50) NULL;
