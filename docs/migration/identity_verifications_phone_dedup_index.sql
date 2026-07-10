-- identity_verifications: 중복가입 차단 기준을 DI → 휴대폰 번호로 변경.
-- 조회가 di → phone_number로 바뀌므로 di 인덱스를 제거하고 phone_number 인덱스를 추가한다. (di 컬럼은 감사용으로 유지)
ALTER TABLE identity_verifications
    DROP INDEX idx_iv_di,
    ADD KEY idx_iv_phone_number (phone_number);
