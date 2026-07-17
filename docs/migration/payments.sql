-- 결제 기록 테이블. 무검증 접수 단계: 결제수단·PG 정보 없이 접수 내용(누가·어느 일정·성별·확정가)만 보관한다.
-- 참가 상태의 원장은 gathering_members.status이며 payments에는 상태 컬럼을 두지 않는다.
CREATE TABLE payments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    gathering_id BIGINT NOT NULL,
    schedule_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    payment_key VARCHAR(255) NOT NULL,
    gender VARCHAR(50) NOT NULL,
    amount INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) DEFAULT NULL,
    INDEX idx_schedule_id_user_id (schedule_id, user_id)
);
