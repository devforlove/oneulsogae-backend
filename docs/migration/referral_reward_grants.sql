-- referral_reward_grants: 추천 보상을 본인확인 DI 기준 1인 1회로 제한하는 방어 이력.
-- 탈퇴 후 유예가 지나 파기되면 같은 사람이 새 계정으로 재가입할 수 있는데, DI는 동일하므로 이 이력으로 재수령을 막는다.
-- 원본 DI는 담지 않고 SHA-256(salt + di) 해시만 보관한다. (개인정보 아님 → 파기 대상 아님)
CREATE TABLE referral_reward_grants
(
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    referred_di_hash  VARCHAR(64)  NOT NULL COMMENT '피추천인 DI의 SHA-256 해시(hex)',
    referrer_user_id  BIGINT       NOT NULL,
    referred_user_id  BIGINT       NOT NULL,
    coin_amount       INT          NOT NULL,
    granted_at        DATETIME     NOT NULL,
    created_at        DATETIME     NOT NULL,
    updated_at        DATETIME     NOT NULL,
    deleted_at        DATETIME     NULL,
    PRIMARY KEY (id),
    UNIQUE KEY ux_referred_di_hash (referred_di_hash),
    -- 추천 실적 집계(추천 코드 화면의 친구 수·받은 코인)용.
    KEY idx_referrer_user_id (referrer_user_id)
);
