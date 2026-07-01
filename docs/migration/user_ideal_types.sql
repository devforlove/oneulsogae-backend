-- 이상형(매칭 선호) 저장용 테이블. 사용자(users)와 1:1이며, user_id에 유니크 인덱스를 둔다.
-- 나이/키는 숫자 경계, 나머지는 enum 문자열로 저장한다(모두 NULL 허용, NULL = "상관없음").
CREATE TABLE user_ideal_types (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    user_id         BIGINT      NOT NULL,
    age_min         INT         NULL,
    age_max         INT         NULL,
    height_min      INT         NULL,
    height_max      INT         NULL,
    marital_status  VARCHAR(50) NULL,
    smoking_status  VARCHAR(50) NULL,
    drinking_status VARCHAR(50) NULL,
    religion        VARCHAR(50) NULL,
    distance        VARCHAR(50) NULL,
    created_at      DATETIME(6) NOT NULL,
    updated_at      DATETIME(6) NOT NULL,
    deleted_at      DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY ux_user_ideal_type_user_id (user_id)
);
