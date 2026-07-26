-- 학교 인증 미션: 학교(대학) 인증 완료 → 10코인
INSERT INTO missions (type, reward_coin, title, description, active, display_order, created_at, updated_at)
VALUES ('VERIFY_UNIVERSITY', 10, '학교 인증', '학교 인증을 완료하고 10코인을 받으세요', 1, 1, NOW(6), NOW(6));
