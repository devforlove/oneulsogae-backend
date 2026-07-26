-- 신규 가입자에게 가입 후 7일간 1회 한정 2배(200코인) PG 상품. 만료는 유저별 가입시각 + 7일로 자동 계산된다.
INSERT INTO coin_items (coin_amount, price, sale_price, once_per_user, sale_channel,
                        store_product_id, valid_days, created_at, updated_at)
VALUES (200, 10000, 4900, 1, 'PG', NULL, 7, NOW(6), NOW(6));
