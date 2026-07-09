-- identity_verifications: KCP 본인확인 거래 기록. 거래등록(REQUESTED) 후 결과확정 시 VERIFIED로 전이하며 검증값을 담는다.
-- di는 중복가입 차단 조회용 평문(인덱스), ci_encrypted는 앱단 AES-GCM 암호문. CI/DI는 응답·로그에 노출하지 않는다.
CREATE TABLE identity_verifications (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    user_id       BIGINT       NOT NULL,
    ordr_idxx     VARCHAR(50)  NOT NULL,
    reg_cert_key  VARCHAR(100) NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    real_name     VARCHAR(255) NULL,
    birthday      DATE         NULL,
    gender        VARCHAR(20)  NULL,
    phone_number  VARCHAR(20)  NULL,
    di            VARCHAR(100) NULL,
    ci_encrypted  VARCHAR(512) NULL,
    foreigner     BIT(1)       NULL,
    telecom       VARCHAR(20)  NULL,
    verified_at   DATETIME(6)  NULL,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    deleted_at    DATETIME(6)  NULL,
    PRIMARY KEY (id),
    -- user_id로 최신 거래(PK 내림차순) 조회
    KEY idx_iv_user_id (user_id),
    -- di로 중복가입(VERIFIED) 존재 여부 조회
    KEY idx_iv_di (di)
);
