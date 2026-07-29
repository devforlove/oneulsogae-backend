-- 셀소 MBTI를 입력 대신 등록 시점 프로필(user_details.mbti) 스냅샷으로 채우도록 변경.
-- 프로필에 MBTI가 없는 사용자도 등록할 수 있어야 하므로 컬럼을 NULL 허용으로 완화한다.
ALTER TABLE self_intro_posts MODIFY mbti VARCHAR(10) NULL;
