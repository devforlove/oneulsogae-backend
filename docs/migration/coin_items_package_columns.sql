-- coin_items: 회원당 1회 구매 패키지·판매 채널·스토어 SKU 컬럼 추가.
-- once_per_user는 패키지가 회원당 1회만 구매 가능한지, sale_channel은 판매 채널(PG·IAP·BOTH)을 나타낸다.
-- store_product_id는 IAP 검증이 스토어 SKU를 coin_items로 해석하는 데 쓰는 값이라 유니크다. (PG 전용 상품은 NULL)
ALTER TABLE coin_items
    ADD COLUMN once_per_user    TINYINT(1)   NOT NULL DEFAULT 0,
    ADD COLUMN sale_channel     VARCHAR(10)  NOT NULL DEFAULT 'PG',
    ADD COLUMN store_product_id VARCHAR(255) NULL,
    ADD UNIQUE KEY ux_coin_items_store_product_id (store_product_id);
