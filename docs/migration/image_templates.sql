-- image_templates: 배포 없이 교체 가능한 이미지(URL·치수) 참조 테이블. code로 조회한다.
-- 팝업 등 여러 도메인이 code로 참조해 조회 시점에 현재 이미지를 해석한다. (앱은 읽기만, 행은 DB에서 관리)
CREATE TABLE image_templates (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    code         VARCHAR(100) NOT NULL,
    image_url    VARCHAR(500) NOT NULL,
    image_width  INT          NOT NULL,
    image_height INT          NOT NULL,
    created_at   DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    deleted_at   DATETIME(6)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT ux_image_template_code UNIQUE (code)
);

-- 환불 팝업 이미지 (placeholder — 운영에서 image_url/치수를 실제 값으로 교체한다)
INSERT INTO image_templates (code, image_url, image_width, image_height, created_at, updated_at)
VALUES
    ('POPUP_MATCH_FAILED_REFUND',   'https://placehold.co/320x400?text=match-failed-refund',   320, 400, NOW(6), NOW(6)),
    ('POPUP_MEETING_FAILED_REFUND', 'https://placehold.co/320x400?text=meeting-failed-refund', 320, 400, NOW(6), NOW(6));
