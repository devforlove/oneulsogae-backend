-- 첫 미션: 자기소개 100자 이상 작성 → 50코인
INSERT INTO missions (type, reward_coin, title, description, active, display_order, created_at, updated_at)
VALUES ('WRITE_INTRODUCTION', 50, '자기소개 작성', '자기소개를 100자 이상 작성하고 50코인을 받으세요', 1, 0, NOW(6), NOW(6));
