-- 같은 회사 구성원 소개 차단 기능을 위해 match_user에 두 컬럼을 추가한다.
-- refuse_same_company_intro: 같은 회사 소개 거부 플래그. 기본값 거부(1).
-- company_name: 회사명(같은 회사 판정 기준). user_details.company_name을 스냅샷 동기화하며, 기존 행은 백필한다.
ALTER TABLE match_user ADD COLUMN refuse_same_company_intro TINYINT(1) NOT NULL DEFAULT 1;
ALTER TABLE match_user ADD COLUMN company_name VARCHAR(100) NULL;

UPDATE match_user mu
JOIN user_details ud ON ud.user_id = mu.user_id
SET mu.company_name = ud.company_name
WHERE ud.company_name IS NOT NULL;
