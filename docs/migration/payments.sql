-- 결제 기록 테이블. 접수 시점에 PENDING으로 저장하고 PG 최종 승인 결과로 APPROVED/FAILED로 전이한다.
-- status는 PG 청구 라이프사이클 원장이며, 참가 승인 원장(gathering_members.status)과는 다른 축이다.
CREATE TABLE payments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    gathering_id BIGINT NOT NULL,
    schedule_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    payment_key VARCHAR(255) NOT NULL,
    order_id VARCHAR(255) NOT NULL,
    gender VARCHAR(50) NOT NULL,
    amount INT NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) DEFAULT NULL,
    UNIQUE KEY uk_payment_key (payment_key),
    INDEX idx_schedule_id_user_id (schedule_id, user_id)
);
