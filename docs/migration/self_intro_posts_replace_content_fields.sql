-- 셀소 본문 항목 개편: 결혼관·장거리·원하는 나이대·선호 상대 제거, 관심사·성격·이상형/연애관 추가.
-- free_word(한마디)는 새 체계에서도 마지막 입력란으로 유지한다 — DROP하지 않아 기존 데이터가 보존된다.
-- ⚠️ 컬럼 DROP은 기존 글의 해당 본문을 복구 불가로 삭제한다. 보존이 필요하면 실행 전 백업할 것.
-- 새 컬럼은 구버전 글 표시를 위해 DEFAULT ''로 채운다. (애플리케이션은 신규 등록 시 항상 값을 넣는다)
ALTER TABLE self_intro_posts
    ADD COLUMN interests VARCHAR(500) NOT NULL DEFAULT '' AFTER mbti,
    ADD COLUMN personality VARCHAR(500) NOT NULL DEFAULT '' AFTER interests,
    ADD COLUMN ideal_type VARCHAR(500) NOT NULL DEFAULT '' AFTER personality,
    DROP COLUMN long_distance,
    DROP COLUMN desired_age,
    DROP COLUMN marriage_thought,
    DROP COLUMN preferred_partner;

-- 이전 버전의 이 마이그레이션(DROP COLUMN free_word 포함)을 이미 적용한 DB만 아래를 추가 실행한다.
-- ALTER TABLE self_intro_posts ADD COLUMN personality VARCHAR(500) NOT NULL DEFAULT '' AFTER interests;
-- ALTER TABLE self_intro_posts ADD COLUMN free_word VARCHAR(500) NOT NULL DEFAULT '' AFTER charm_point;
