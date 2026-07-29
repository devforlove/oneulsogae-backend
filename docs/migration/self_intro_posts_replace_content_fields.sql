-- 셀소 본문 항목 개편: 결혼관·장거리·원하는 나이대·선호 상대·자유 한마디 제거, 관심사·이상형/연애관 추가.
-- ⚠️ 컬럼 DROP은 기존 글의 해당 본문을 복구 불가로 삭제한다. 보존이 필요하면 실행 전 백업할 것.
-- 새 컬럼은 구버전 글 표시를 위해 DEFAULT ''로 채운다. (애플리케이션은 신규 등록 시 항상 값을 넣는다)
ALTER TABLE self_intro_posts
    ADD COLUMN interests VARCHAR(500) NOT NULL DEFAULT '' AFTER mbti,
    ADD COLUMN ideal_type VARCHAR(500) NOT NULL DEFAULT '' AFTER interests,
    DROP COLUMN long_distance,
    DROP COLUMN desired_age,
    DROP COLUMN marriage_thought,
    DROP COLUMN preferred_partner,
    DROP COLUMN free_word;
