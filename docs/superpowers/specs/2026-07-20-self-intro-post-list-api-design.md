# 셀소 목록 조회 API 설계

프론트 라운지 탭(`LoungeTab.tsx`)의 4열 그리드를 채우는 조회 경로다.
등록 경로는 [셀소 등록 API 설계](2026-07-20-self-intro-post-api-design.md) 참고.

## 엔드포인트

`GET /lounge/v1/self-intro-posts?cursor={postId}` — 인증 필요.

- 페이지 크기 24 고정(그리드 6줄 × 4열). 클라이언트가 크기를 정하지 않는다.
- 응답
  ```json
  { "success": true, "data": {
      "items": [{ "postId": 26, "authorNickname": "라운지주민", "likeCount": 12, "imageUrl": "https://...",
                  "authorGender": "FEMALE", "authorAge": 30, "authorProfileImageCode": "PROFILE_03",
                  "authorJob": "기획자", "authorCompanyName": "오늘소개",
                  "authorActivityArea": "인천광역시 연수구" }],
      "receivedPendingChatRequestCount": 0, "sentPendingChatRequestCount": 0,
      "hasNext": true, "nextCursor": 3 } }
  ```
- 다음 페이지는 `nextCursor`를 `cursor`로 그대로 넘긴다. `hasNext=false`면 마지막 페이지이고 `nextCursor`는 null이다.

## 조회 규칙

- `lounge_posts`에서 `type=SELF_INTRO`, `id` 내림차순(최신순). 커서는 `id < :cursor` keyset —
  복합 인덱스 `idx_type_id (type, id)`가 동등 조건 + 정렬을 받쳐 뒤 페이지도 seek로 끝난다(offset 스캔 없음).
- 페이지 크기 + 1건을 읽어 COUNT 없이 다음 페이지 존재를 판정한다. (`SelfIntroPostPage.of`)
- 작성자 프로필(닉네임·성별·생년월일·프로필 이미지 코드·직업·회사명)은 `user_details`, 표시용 활동지역은 거기서 `regions`까지,
  대표 사진은 `lounge_post_images`의 `display_order = 0` 행을 **left join**으로 붙인다.
  프로필·지역이나 사진이 없어도 글은 목록에서 빠지지 않는다(각각 null).
  만 나이는 dao가 `birthday`까지만 담고 서비스가 `TimeGenerator`의 오늘 날짜로 계산한다(`SelfIntroPostPage.withAuthorAges`).
- 응답 루트의 `receivedPendingChatRequestCount`·`sentPendingChatRequestCount`는 요청한 사용자의 미수락 대화 신청 건수다.
  ([대화 신청 설계](2026-07-21-lounge-chat-request-design.md) 참고)
- 좋아요 수는 `lounge_posts.like_count`(비정규화 카운트)를 그대로 읽는다. 집계 조인 없음.
- 사진은 비공개 저장이라 dao는 `imageKey`까지만 담고, 서비스가 presigned GET URL로 변환한다.

## 구성 요소 (CQRS query 패키지)

- **oneulsogae-core `lounge/query`**
  - `dto/SelfIntroPostView`(read model), `dto/SelfIntroPostPage`(커서 페이지 일급 컬렉션 — `of`·`nextCursor`·`withImageUrls`·`withAuthorAges`·`withPendingChatRequestCounts`)
  - `dao/GetSelfIntroPostDao`
  - `service/GetSelfIntroPostsService`(`@Transactional(readOnly = true)`, `PAGE_SIZE = 24`) + `service/port/in/GetSelfIntroPostsUseCase`
  - `service/port/out/LoungeImageUrlPort`(presign)
- **oneulsogae-infra `lounge/query`**: `GetSelfIntroPostDaoImpl`(QueryDSL), `S3LoungeImageUrlAdapter`
- **oneulsogae-api**: `SelfIntroPostController`에 GET 추가, `SelfIntroPostPageResponse`·`SelfIntroPostItemResponse`

query는 자기 dao에만 의존하며 command 도메인·포트를 참조하지 않는다.

## 검증

- 유닛: `SelfIntroPostPageTest` — 한 건 더 읽기 판정·커서 산출·URL 채우기
- E2E: `GetSelfIntroPostsE2ETest` — 26건 중 첫 페이지 24건 + 커서로 나머지 2건, 사진 없는 글도 노출(imageUrl null)

## 상세 조회

`GET /lounge/v1/self-intro-posts/{postId}` — 인증 필요. 글이 없거나 셀소 본문이 없으면 404(LOUNGE-008).

- 응답: `postId`·`authorNickname`·`likeCount` + 프로필(`gender`·`age`·`height`·`activityArea`·`job`) +
  본문 7개 항목 + `imageUrls`(노출 순서대로 presigned URL 전체)
- 프로필은 `user_details`를 left join하고 활동지역은 `regions`를 조인해 "시/도 시/군/구"로 만든다.
  **생년월일은 응답에 노출하지 않고**, 서버가 `TimeGenerator.today()` 기준 만 나이만 계산해 내려준다.
- 본문(`self_intro_posts`)은 inner join — 본문 없는 글은 셀소 상세로 조회되지 않는다.
- 사진은 상세 쿼리와 분리해 `display_order` 오름차순으로 따로 읽는다.
  한 글에 여러 장이라 조인하면 상세 행이 사진 수만큼 곱해지기 때문이다.
- 검증: `GetSelfIntroPostDetailE2ETest` — 사진 2장 상세(프로필·본문·순서)·없는 글 404

## 제외한 것

좋아요 등록/취소, `likedByMe` 플래그, 글 수정/삭제. 별도 작업이다.

## 프론트 필요 변경 (백엔드에서 손대지 않음)

`LoungeTab.tsx`의 `mockLoungeFeed()` + `useLoungePosts()` 조합을 이 엔드포인트 호출로 교체:
`item.seed`/`loungeImageUri` 폴백 대신 `imageUrl`을 쓰고(없으면 플레이스홀더), `author` → `authorNickname`,
`id` → `postId`. 무한 스크롤은 `hasNext`/`nextCursor`로 잇는다.

`SelfIntroDetail.tsx`: `mockSelfIntro()` 폴백 대신 상세 API 응답을 쓴다. `photos` → `imageUrls`,
`gender`는 `MALE`/`FEMALE` enum이라 한국어 라벨 변환이 필요하고, `area` → `activityArea`,
`age`/`height`는 서버 값을 그대로 쓴다. 좋아요·대화 신청 버튼은 API가 없어 계속 mock으로 둔다.
