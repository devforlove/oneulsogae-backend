-- 비로그인(익명) 고객센터 문의를 허용하기 위해 user_id를 nullable로 변경한다.
-- 로그인 사용자는 회원 ID, 비로그인 사용자는 NULL로 저장된다.
ALTER TABLE inquiries MODIFY user_id BIGINT NULL;
