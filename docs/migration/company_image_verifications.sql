-- company_image_verifications: 직장 서류 이미지 인증 제출 기록.
-- 업로드한 서류의 S3 오브젝트 키(image_key)와 심사 상태(status)를 보관한다. (파일 자체는 S3에 비공개 저장)
-- 서류는 자동 검증이 불가능해 제출 시 PENDING으로 시작하고, 어드민 심사로 APPROVED/REJECTED가 된다.
CREATE TABLE company_image_verifications (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    image_key  VARCHAR(512) NOT NULL,
    status     VARCHAR(20)  NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    deleted_at DATETIME(6)  NULL,
    PRIMARY KEY (id),
    -- user_id로 필터 후 최신 제출(PK 내림차순)을 찾는다. (심사 목록/최근 제출 조회 대비)
    KEY idx_user_id (user_id)
);
