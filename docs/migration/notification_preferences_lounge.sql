-- 셀소(라운지) 알림을 소개팅(1:1) 알림에서 분리 — lounge 컬럼 추가.
-- 기존 사용자는 지금까지 ONE_TO_ONE 토글이 라운지 알림을 관장했으므로,
-- 동작이 바뀌지 않도록 기존 one_to_one 값을 그대로 이어받는다.
ALTER TABLE notification_preferences
    ADD COLUMN lounge BOOLEAN NOT NULL DEFAULT TRUE AFTER one_to_one;

UPDATE notification_preferences
SET lounge = one_to_one;
