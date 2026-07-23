-- 라운지 셀소 대화 신청에 만료 시각(expired_at = 신청 시각 + 3일)을 추가한다.
-- 만료 판정을 created_at 계산이 아니라 저장된 expired_at으로 하고, 응답에도 그대로 내려준다.
-- 기존 행은 created_at + 3일로 채운 뒤 NOT NULL로 조인다. (새 행은 앱이 생성 시각 + 3일로 채운다)
ALTER TABLE lounge_chat_requests ADD COLUMN expired_at DATETIME(6) NULL;
UPDATE lounge_chat_requests SET expired_at = created_at + INTERVAL 3 DAY WHERE expired_at IS NULL;
ALTER TABLE lounge_chat_requests MODIFY COLUMN expired_at DATETIME(6) NOT NULL;
