-- 회사 이메일 도메인 1:N 허용 (그룹 계열사처럼 한 도메인을 여러 회사가 공유)
-- user_companies: 도메인 단독 유니크를 (도메인, 회사명) 조합 유니크로 교체.
--   조합 키의 선두 컬럼(email_domain)이 도메인 후보 조회 seek를 겸하므로 단독 인덱스는 두지 않는다.
ALTER TABLE user_companies
    ADD UNIQUE KEY ux_email_domain_company_name (email_domain, company_name),
    DROP INDEX ux_email_domain;

-- company_email_verifications: 요청 시점에 확정한 회사 매핑(user_companies) id 보관.
--   인증 확정 시 도메인을 재조회하지 않고 이 id로 회사명을 확정한다. (컬럼 도입 전 구버전 행은 NULL)
ALTER TABLE company_email_verifications
    ADD COLUMN user_company_id BIGINT NULL AFTER company_email;
