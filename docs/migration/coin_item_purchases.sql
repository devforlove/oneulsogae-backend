-- 회원당 1회 구매 코인 패키지의 구매 가드 기록 테이블. once_per_user 상품을 실제 적립 성공한 시점에 저장한다.
-- (user_id, item_id) 유니크가 결제 경로(PG·IAP) 무관 이중구매를 원자적으로 막는다.
CREATE TABLE coin_item_purchases (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    item_id    BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY ux_coin_item_purchases_user_item (user_id, item_id)
);
