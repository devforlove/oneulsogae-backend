-- popups 이미지 저장을 inline 컬럼 → image_templates 코드 참조로 이전한다.
-- 선행: image_templates.sql (테이블 생성 + 환불 템플릿 seed)

-- 1) image_code 컬럼 추가
ALTER TABLE popups ADD COLUMN image_code VARCHAR(100) NULL AFTER description;

-- 2) 기존 이미지가 있는 popup을 image_templates로 백필하고 image_code로 연결 (기존 이미지 보존)
INSERT INTO image_templates (code, image_url, image_width, image_height, created_at, updated_at)
SELECT CONCAT('POPUP_LEGACY_', p.id), p.image_url, COALESCE(p.image_width, 0), COALESCE(p.image_height, 0), NOW(6), NOW(6)
FROM popups p
WHERE p.image_url IS NOT NULL;

UPDATE popups p
SET p.image_code = CONCAT('POPUP_LEGACY_', p.id)
WHERE p.image_url IS NOT NULL;

-- 3) 옛 이미지 컬럼 제거
ALTER TABLE popups
    DROP COLUMN image_url,
    DROP COLUMN image_width,
    DROP COLUMN image_height;
