-- 인앱결제(App Store/Google Play) 코인 구매 결제 기록 테이블.
-- 스토어 거래 식별자(transaction_id)가 유니크라 같은 영수증 재검증을 멱등 처리한다.
-- (user_id) 인덱스로 사용자별 IAP 결제 내역 조회를 커버한다.
CREATE TABLE iap_payments (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    user_id        BIGINT       NOT NULL,
    item_id        BIGINT       NOT NULL,
    platform       VARCHAR(10)  NOT NULL,
    product_id     VARCHAR(255) NOT NULL,
    transaction_id VARCHAR(255) NOT NULL,
    coin_amount    INT          NOT NULL,
    status         VARCHAR(50)  NOT NULL,
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,
    deleted_at     DATETIME(6)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY ux_iap_payments_transaction_id (transaction_id),
    KEY idx_user_id (user_id)
);
