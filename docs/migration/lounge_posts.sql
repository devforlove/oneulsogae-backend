-- 라운지 탭 글 저장 테이블 4종.
-- lounge_posts가 모든 글의 공통 골격이고, 타입별 본문(self_intro_posts)·사진·좋아요를 1:N/1:1로 붙인다.
CREATE TABLE lounge_posts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    type VARCHAR(50) NOT NULL,
    user_id BIGINT NOT NULL,
    like_count INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) DEFAULT NULL,
    INDEX idx_type_id (type, id),
    INDEX idx_user_id (user_id)
);

-- 셀프 소개팅(셀소) 본문. lounge_posts와 1:1.
-- 성별·나이·키·지역·직업은 프로필(user_details) 소유라 복사하지 않고 조회 시 조인한다. 본문 항목은 모두 필수다.
CREATE TABLE self_intro_posts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_id BIGINT NOT NULL,
    long_distance VARCHAR(40) NOT NULL,
    desired_age VARCHAR(40) NOT NULL,
    mbti VARCHAR(10) NOT NULL,
    marriage_thought VARCHAR(500) NOT NULL,
    preferred_partner VARCHAR(500) NOT NULL,
    charm_point VARCHAR(500) NOT NULL,
    free_word VARCHAR(500) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) DEFAULT NULL,
    UNIQUE KEY ux_post_id (post_id)
);

-- 글에 올린 사진. 파일은 S3에 두고 오브젝트 키만 보관하며 display_order 오름차순으로 노출한다.
CREATE TABLE lounge_post_images (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_id BIGINT NOT NULL,
    image_key VARCHAR(512) NOT NULL,
    display_order INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) DEFAULT NULL,
    INDEX idx_post_id_display_order (post_id, display_order)
);

-- 좋아요. 취소는 soft delete가 아니라 행 삭제다(삭제 행이 남으면 재좋아요가 유니크 제약에 걸린다).
CREATE TABLE lounge_post_likes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) DEFAULT NULL,
    UNIQUE KEY ux_post_id_user_id (post_id, user_id)
);
