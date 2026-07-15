-- payment_methods: 체크아웃 화면에 노출할 결제수단 참조 테이블. code로 식별한다.
-- 활성 여부(active)·노출 순서(display_order)를 배포 없이 DB에서 조정한다. (앱은 읽기만, 행은 DB에서 관리)
CREATE TABLE payment_methods (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    code          VARCHAR(50)  NOT NULL,
    name          VARCHAR(100) NOT NULL,
    display_order INT          NOT NULL,
    active        BOOLEAN      NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    deleted_at    DATETIME(6)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT ux_payment_method_code UNIQUE (code)
);

-- 초기 결제수단. PG 연동 전이라 무통장입금만 활성, 카드·카카오페이는 비활성으로 시작한다. (활성 여부는 운영 판단으로 조정)
INSERT INTO payment_methods (code, name, display_order, active, created_at, updated_at)
VALUES
    ('BANK_TRANSFER', '무통장입금', 1, TRUE,  NOW(6), NOW(6)),
    ('CARD',          '카드',       2, FALSE, NOW(6), NOW(6)),
    ('KAKAO_PAY',     '카카오페이', 3, FALSE, NOW(6), NOW(6));
