-- user_details: 온보딩·프로필 수정에서 받는 MBTI 4자(대문자).
-- 기존 가입자는 값이 없어 NULL 허용으로 추가한다. (요청 경계에서만 필수)
ALTER TABLE user_details
    ADD COLUMN mbti VARCHAR(4) NULL;
