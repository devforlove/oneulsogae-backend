-- match_user 백필 스크립트 (MySQL)
--
-- 배경: match 도메인이 user_details 결합을 줄이기 위해, 매칭 기준 필드만 담은 읽기 모델
--       match_user를 도입했다. 이후 user 프로필/가입/로그인 변경은 도메인 이벤트로 동기화되지만,
--       이벤트는 "변경 시점부터만" 동작하므로 배포 시점에 이미 정식 가입(ACTIVE)된 사용자는 누락된다.
--       이 스크립트로 기존 ACTIVE + 프로필 완성 사용자를 1회 적재한다.
--
-- 전제: match_user 테이블은 Hibernate ddl-auto(또는 별도 DDL)로 이미 생성돼 있어야 한다.
-- 멱등: user_id가 PK이므로 ON DUPLICATE KEY UPDATE로 재실행해도 안전하다.
-- 조건: 행의 존재 = "매칭 가능"이므로, 매칭 필수 필드(성별·권역·결혼여부·나이·닉네임·최근 로그인)가
--       모두 채워진 ACTIVE 사용자만 적재한다.

INSERT INTO match_user (user_id, gender, region_code, marital_status, age, nickname, last_login_at, created_at, updated_at)
SELECT
    u.id,
    d.gender,
    d.region_code,
    d.marital_status,
    d.age,
    d.nickname,
    u.last_login_at,
    NOW(),
    NOW()
FROM users u
JOIN user_details d ON d.user_id = u.id
WHERE u.status = 'ACTIVE'
  AND u.deleted_at IS NULL
  AND d.deleted_at IS NULL
  AND d.gender IS NOT NULL
  AND d.region_code IS NOT NULL
  AND d.marital_status IS NOT NULL
  AND d.age IS NOT NULL
  AND d.nickname IS NOT NULL
  AND u.last_login_at IS NOT NULL
ON DUPLICATE KEY UPDATE
    gender = VALUES(gender),
    region_code = VALUES(region_code),
    marital_status = VALUES(marital_status),
    age = VALUES(age),
    nickname = VALUES(nickname),
    last_login_at = VALUES(last_login_at),
    updated_at = NOW();
