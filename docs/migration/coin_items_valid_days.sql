-- 코인 상품에 유저 가입시각 기준 유효일수(가입 후 N일) 컬럼 추가. NULL이면 상시 판매(기존 상품 하위호환).
ALTER TABLE coin_items ADD COLUMN valid_days INT NULL;
