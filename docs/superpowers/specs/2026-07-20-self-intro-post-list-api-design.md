# 셀소 목록 조회 API 설계

프론트 라운지 탭(`LoungeTab.tsx`)의 4열 그리드를 채우는 조회 경로다.
등록 경로는 [셀소 등록 API 설계](2026-07-20-self-intro-post-api-design.md) 참고.

## 엔드포인트

`GET /lounge/v1/self-intro-posts?cursor={postId}` — 인증 필요.

- 페이지 크기 24 고정(그리드 6줄 × 4열). 클라이언트가 크기를 정하지 않는다.
- 응답
  ```json
  { "success": true, "data": {
      "items": [{ "postId": 26, "authorNickname": "라운지주민", "likeCount": 12, "imageUrl": "https://..." }],
      "hasNext": true, "nextCursor": 3 } }
  ```
- 다음 페이지는 `nextCursor`를 `cursor`로 그대로 넘긴다. `hasNext=false`면 마지막 페이지이고 `nextCursor`는 null이다.

## 조회 규칙

- `lounge_posts`에서 `type=SELF_INTRO`, `id` 내림차순(최신순). 커서는 `id < :cursor` keyset —
  복합 인덱스 `idx_type_id (type, id)`가 동등 조건 + 정렬을 받쳐 뒤 페이지도 seek로 끝난다(offset 스캔 없음).
- 페이지 크기 + 1건을 읽어 COUNT 없이 다음 페이지 존재를 판정한다. (`SelfIntroPostPage.of`)
- 작성자 닉네임은 `user_details`, 대표 사진은 `lounge_post_images`의 `display_order = 0` 행을 **left join**으로 붙인다.
  프로필이나 사진이 없어도 글은 목록에서 빠지지 않는다(각각 null).
- 좋아요 수는 `lounge_posts.like_count`(비정규화 카운트)를 그대로 읽는다. 집계 조인 없음.
- 사진은 비공개 저장이라 dao는 `imageKey`까지만 담고, 서비스가 presigned GET URL로 변환한다.

## 구성 요소 (CQRS query 패키지)

- **meeple-core `lounge/query`**
  - `dto/SelfIntroPostView`(read model), `dto/SelfIntroPostPage`(커서 페이지 일급 컬렉션 — `of`·`nextCursor`·`withImageUrls`)
  - `dao/GetSelfIntroPostDao`
  - `service/GetSelfIntroPostsService`(`@Transactional(readOnly = true)`, `PAGE_SIZE = 24`) + `service/port/in/GetSelfIntroPostsUseCase`
  - `service/port/out/LoungeImageUrlPort`(presign)
- **meeple-infra `lounge/query`**: `GetSelfIntroPostDaoImpl`(QueryDSL), `S3LoungeImageUrlAdapter`
- **meeple-api**: `SelfIntroPostController`에 GET 추가, `SelfIntroPostPageResponse`·`SelfIntroPostItemResponse`

query는 자기 dao에만 의존하며 command 도메인·포트를 참조하지 않는다.

## 검증

- 유닛: `SelfIntroPostPageTest` — 한 건 더 읽기 판정·커서 산출·URL 채우기
- E2E: `GetSelfIntroPostsE2ETest` — 26건 중 첫 페이지 24건 + 커서로 나머지 2건, 사진 없는 글도 노출(imageUrl null)

## 제외한 것

상세 조회(`/lounge/{postId}`), 좋아요 등록/취소, `likedByMe` 플래그. 별도 작업이다.

## 프론트 필요 변경 (백엔드에서 손대지 않음)

`LoungeTab.tsx`의 `mockLoungeFeed()` + `useLoungePosts()` 조합을 이 엔드포인트 호출로 교체:
`item.seed`/`loungeImageUri` 폴백 대신 `imageUrl`을 쓰고(없으면 플레이스홀더), `author` → `authorNickname`,
`id` → `postId`. 무한 스크롤은 `hasNext`/`nextCursor`로 잇는다. 상세 화면은 조회 API가 아직 없어 그대로 둔다.
