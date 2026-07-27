-- users: 추천 실적 조회(내 추천 코드로 가입한 친구 수) 집계용 인덱스.
-- GET /users/v1/me/referral-code 가 referred_by_user_id = :myUserId 로 count 하므로 동등 조건을 seek로 받는다.
ALTER TABLE users
    ADD INDEX idx_referred_by_user_id (referred_by_user_id);
