-- solo_matches: 일일 배치의 제외 조회를 인덱스 seek로 받친다.
--   - introduced_date: "오늘 매칭된 유저" 제외
--   - status: "성사(MATCHED) 유저" 제외
ALTER TABLE solo_matches
    ADD INDEX idx_introduced_date (introduced_date),
    ADD INDEX idx_status (status);
