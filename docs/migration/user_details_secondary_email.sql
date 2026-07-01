-- 마케팅·광고·매칭 알림 수신용 보조 이메일을 저장하기 위해 secondary_email 컬럼을 추가한다.
-- 선택 항목이므로 기본값은 NULL이며, 미설정 사용자는 NULL로 남는다.
ALTER TABLE user_details ADD COLUMN secondary_email VARCHAR(255) NULL;
