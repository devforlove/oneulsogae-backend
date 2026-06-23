-- solo_match_members: 참가자 상태를 단일 status enum(WAITING/APPLY/ACTIVE/DEACTIVE)으로 통합하며 accepted 컬럼을 제거한다.
-- (개발 단계로 데이터 보존 환산 없이 컬럼 DROP만. status는 varchar라 새 값 추가에 DDL 불필요)
ALTER TABLE solo_match_members DROP COLUMN accepted;
