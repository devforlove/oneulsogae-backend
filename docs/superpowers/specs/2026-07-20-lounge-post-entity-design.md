# 라운지 포스트 엔티티 설계

프론트(`meeple-frontend`) 라운지 탭은 현재 로컬 목데이터(`LoungeFeedData.ts` / `LoungeFeedStore.ts`)로 동작한다.
이 스펙은 그 피드를 서버에 저장하기 위한 **영속성 엔티티(JPA)만** 정의한다.

## 범위

- 포함: `oneulsogae-common` 포스트 타입 enum, `oneulsogae-infra`의 엔티티 4개, 배포 전 DDL.
- 제외: 도메인 모델·포트·서비스·Repository·Adapter·API·E2E. 댓글·신고·조회수.
  (실제 작성/조회 유스케이스가 확정될 때 별도 세션에서 추가한다)

## 데이터 모델

### `LoungePostType` (oneulsogae-common `common/lounge`)

`SELF_INTRO`("셀프 소개팅") 하나로 시작. 라운지에 다른 글 타입이 생기면 여기에 추가한다.

### `lounge_posts` — 모든 라운지 글의 공통 골격

| 컬럼 | 타입 | 비고 |
| --- | --- | --- |
| `type` | varchar(50) NOT NULL | `LoungePostType` |
| `user_id` | bigint NOT NULL | 작성자 |
| `like_count` | int NOT NULL default 0 | 그리드 목록 표시용 비정규화 카운트 |

- `BaseEntity` 상속(id·created_at·updated_at·deleted_at) + `@SQLRestriction("deleted_at is null")`.
- 인덱스 `idx_type_id (type, id)`: 타입 동등 필터 + 최신순(id desc) 정렬을 인덱스 seek로 받친다.
- 인덱스 `idx_user_id (user_id)`: 내가 쓴 글 조회.

### `self_intro_posts` — 셀소 전용 본문 (lounge_posts와 1:1)

`post_id` bigint NOT NULL **unique**, `long_distance` varchar(40), `desired_age` varchar(40),
`mbti` varchar(10), `marriage_thought`·`preferred_partner`·`charm_point`·`free_word` varchar(500).

- 본문 컬럼은 전부 nullable. 프론트 작성 화면(`SelfIntroScreen.tsx`)이 빈 값을 허용하므로 DB에서 강제하지 않고,
  필수 여부는 이후 도메인/API 레이어에서 결정한다.
- 길이는 프론트 `maxLength`(40 / 4 / 200) 대비 여유를 둔 값이다.
- **작성 시점 프로필 값(성별·나이·키·지역·직업)은 복사 저장하지 않는다.** 표시용 프로필은 `user` 도메인 소유이므로
  조회 어댑터가 `user_details`를 조인해 투영한다.
- 연관은 `@ManyToOne`이 아니라 `post_id` 원시 FK 컬럼으로 둔다(레포 기존 엔티티 관례).

### `lounge_post_images` — 셀소 사진(여러 장)

`post_id` bigint NOT NULL, `image_key` varchar(512) NOT NULL(S3 오브젝트 키), `display_order` int NOT NULL.

- 인덱스 `idx_post_id_display_order (post_id, display_order)`: 글 상세에서 사진을 순서대로 읽는 경로를 커버.
- 열람용 URL은 조회 시 presigned로 발급한다(`GatheringEntity.imageKey`와 동일 관례).

### `lounge_post_likes` — 좋아요

`post_id` bigint NOT NULL, `user_id` bigint NOT NULL.

- 유니크 `ux_post_id_user_id (post_id, user_id)`: 중복 좋아요 방지 + "내가 눌렀는지" 조회를 함께 커버.
- **좋아요 취소는 하드 삭제**한다. soft delete로 두면 삭제 행이 남아 재좋아요 시 유니크 제약과 충돌한다.
  따라서 이 엔티티에는 `@SQLRestriction`을 붙이지 않는다(`deleted_at` 컬럼은 `BaseEntity` 상속으로 존재하되 쓰지 않음).
- `lounge_posts.like_count` 증감은 좋아요 유스케이스(명령 서비스)를 만들 때 함께 처리한다. 이번 범위 밖.

## 배포 전 DDL

`docs/migration/lounge_posts.sql` 한 파일에 4개 테이블 CREATE를 담는다. 실 DB 반영은 배포 전 수동 실행.

## 프론트 영향

없음. 이번 변경은 API를 노출하지 않으므로 `meeple-frontend`는 계속 로컬 목데이터로 동작한다.
