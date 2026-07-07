-- 승인 시 유저 프로필 회사명(user_details.company_name)이 덮어써지면 심사 상세의 '이전 회사명'이 새 값으로 보였다.
-- 제출 시점의 프로필 회사명을 스냅샷해 승인 후에도 이전 회사명을 안정적으로 보여주기 위해 previous_company_name 컬럼을 추가한다.
ALTER TABLE company_image_verifications ADD COLUMN previous_company_name VARCHAR(100) NULL AFTER company_name;
