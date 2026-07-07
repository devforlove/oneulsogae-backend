-- company_image_verifications: 제출 희망 회사명·반려 사유 컬럼 추가.
-- company_name: 유저가 제출 시 기입한 희망 회사명(어드민 심사 근거). 기존 행은 NULL.
-- rejection_reason: 어드민 반려 사유. 반려 시에만 채워진다.
ALTER TABLE company_image_verifications ADD COLUMN company_name VARCHAR(100) NULL;
ALTER TABLE company_image_verifications ADD COLUMN rejection_reason VARCHAR(500) NULL;
