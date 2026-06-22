-- 활동지역(시/도 + 시/군/구)별 위치 참조 테이블. 좌표로 지역 간 거리를 계산해 가까운 사용자·팀을 소개하는 데 쓴다.
-- (sido, sigungu) 조합이 지역 조회 키이며 유니크하다. (행 적재(시드)는 별도로 채워야 기능이 동작한다)
CREATE TABLE regions (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    sido       VARCHAR(50) NOT NULL,
    sigungu    VARCHAR(50) NOT NULL,
    longitude  DOUBLE      NOT NULL,
    latitude   DOUBLE      NOT NULL,
    -- 노출 정렬 순서. 목록 조회는 이 값 오름차순으로 정렬한다. (order는 SQL 예약어라 컬럼명 display_order)
    display_order INT       NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT ux_sido_sigungu UNIQUE (sido, sigungu)
);
