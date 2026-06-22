-- 활동지역을 문자열(activity_area)에서 regions FK(region_id)로 전환한다. (regionCode(1~5)는 매칭 키로 유지)
-- 표시용 지역명은 응답 시 regions를 join해 내려준다.

-- 1) user_details: region_id(nullable) 추가 후 activity_area 제거.
--    기존 행은 활동지역 원본 문자열을 regions에 매핑할 수 없어 region_id=null로 둔다. (regions 시드 후 사용자 재선택 필요)
ALTER TABLE user_details ADD COLUMN region_id BIGINT NULL AFTER job;
ALTER TABLE user_details DROP COLUMN activity_area;

-- 2) match_user: region_id 추가. match_user는 user 이벤트로 재적재되는 읽기 모델이다.
--    NOT NULL 제약을 위해, 기존 행은 user_details.region_id로 백필하고(대부분 legacy라 null),
--    채울 수 없는 행은 삭제한다. (다음 로그인/프로필 변경 시 재동기화된다)
ALTER TABLE match_user ADD COLUMN region_id BIGINT NULL AFTER region_code;
UPDATE match_user m
JOIN user_details d ON d.user_id = m.user_id
SET m.region_id = d.region_id
WHERE m.region_id IS NULL AND d.region_id IS NOT NULL;
DELETE FROM match_user WHERE region_id IS NULL;
ALTER TABLE match_user MODIFY COLUMN region_id BIGINT NOT NULL;
