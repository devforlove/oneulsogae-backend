-- 코인 구매 결제 기록 테이블. 접수 시점에 PENDING으로 저장하고 PG 최종 승인 결과로 APPROVED/FAILED로 전이한다.
-- status는 PG 청구 라이프사이클 원장이며, 실제 코인 지급 원장(coin_histories)과는 다른 축이다.
CREATE TABLE coin_payments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    item_id BIGINT NOT NULL,
    coin_amount INT NOT NULL,
    payment_key VARCHAR(255) NOT NULL,
    order_id VARCHAR(255) NOT NULL,
    amount INT NOT NULL,
    status VARCHAR(50) NOT NULL,
    fail_reason VARCHAR(1000) DEFAULT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) DEFAULT NULL,
    UNIQUE KEY uk_payment_key (payment_key),
    INDEX idx_user_id (user_id)
);
