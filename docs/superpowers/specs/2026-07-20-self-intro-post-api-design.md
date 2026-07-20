# 셀프 소개팅(셀소) 등록 API 설계

프론트 `/lounge/self-intro` 작성 화면(`SelfIntroScreen.tsx`)의 입력을 서버에 저장하는 명령 경로다.
엔티티는 [라운지 포스트 엔티티 설계](2026-07-20-lounge-post-entity-design.md)에서 이미 정의했다.

## 엔드포인트

`POST /lounge/v1/self-intro-posts` — multipart/form-data, 인증 필요.

| 파라미터 | 타입 | 제약 |
| --- | --- | --- |
| `photos` | 파일(여러 개) | 1~5장, JPEG·PNG, 장당 10MB. 보낸 순서가 노출 순서 |
| `longDistance` · `desiredAge` | 문자열 | 필수, 각 40자 |
| `mbti` | 문자열 | 필수, 10자 |
| `marriageThought` · `preferredPartner` · `charmPoint` · `freeWord` | 문자열 | 필수, 각 500자 |

응답: `{ "success": true, "data": { "postId": 1 } }`

## 처리 흐름 (`RegisterSelfIntroPostService`, `@Transactional`)

1. `GetUserByIdUseCase`로 작성자 존재 확인 (user 도메인 in-port에 위임)
2. **업로드 전에 전부 검증** — 본문 필수·길이 → 사진 장수 → 파일별 형식·크기 → 등록 빈도.
   롤백돼도 S3에 고아 객체가 남지 않게 하려는 순서다.
3. 등록 빈도: `lounge_posts`에서 `user_id` + `type=SELF_INTRO` + `created_at > now-24h` 카운트가
   1건 이상이면 `LOUNGE-007`(429). 기준 시각은 `TimeGenerator`로 얻어 도메인에 넘긴다.
4. 사진을 `lounge-posts/{userId}/{uuid}.{ext}` 키로 S3에 비공개 업로드
5. `lounge_posts` → `self_intro_posts` → `lounge_post_images` 순서로 저장하고 `postId` 반환

## 구성 요소

- **meeple-core `lounge`**
  - `LoungeErrorCode` — LOUNGE-001~007
  - `command/domain`: `LoungePost`(공통 골격), `SelfIntroPost`(본문 + 검증 규칙 전부), `LoungePostImages`(사진 일급 컬렉션, 순서 부여)
  - in-port `RegisterSelfIntroPostUseCase` + `RegisterSelfIntroPostCommand`(웹 타입 없는 `FilePart`) + `RegisterSelfIntroPostResult`
  - out-port `SaveLoungePostPort` · `CountRecentSelfIntroPostPort` · `SaveSelfIntroPostPort` · `SaveLoungePostImagePort` · `FileStoragePort`(lounge 전용)
- **meeple-infra `lounge/command`**: 엔티티당 어댑터 1개(`LoungePostAdapter`가 저장 + 카운트), 리포지토리·매퍼 각 3개, `S3LoungeImageStorageAdapter`
- **meeple-api**: `SelfIntroPostController`, `SelfIntroPostResponse`

## 에러 코드

| 코드 | 상태 | 상황 |
| --- | --- | --- |
| LOUNGE-001 | 400 | 사진 0장 |
| LOUNGE-002 | 400 | 사진 5장 초과 |
| LOUNGE-003 | 400 | 빈 파일 |
| LOUNGE-004 | 400 | JPEG·PNG가 아닌 사진 |
| LOUNGE-005 | 400 | 10MB 초과 |
| LOUNGE-006 | 400 | 본문 누락·길이 초과 |
| LOUNGE-007 | 429 | 최근 24시간 내 이미 등록 |

## 스키마 변경

본문이 모두 필수가 되었으므로 `self_intro_posts` 본문 7개 컬럼을 NOT NULL로 바꿨다.
`docs/migration/lounge_posts.sql`은 아직 실 DB에 반영되지 않아 파일 수정으로 끝난다. **배포 전 실행 필요.**

## 검증

- 도메인 유닛: `SelfIntroPostTest`(본문·사진·빈도 제한·확장자), `LoungePostImagesTest`(순서 부여)
- E2E: `SelfIntroPostE2ETest` — 정상 등록(3개 테이블 저장 확인)·사진 0장 400·gif 400·본문 누락 400·24시간 재등록 429

## 프론트 필요 변경 (백엔드에서 손대지 않음)

`meeple-frontend/src/domains/lounge`:
- `SelfIntroScreen.tsx`의 `submit()`을 로컬 스토어(`useAddLoungePost`) 대신 위 엔드포인트로 multipart POST
- 현재 `FileReader`로 data URL만 남기므로 원본 `File` 객체를 별도 상태로 보관해 전송해야 한다
- 사진 1장 이상 + 본문 7개 항목이 모두 채워지기 전에는 등록 버튼 비활성화 (서버가 400으로 막음)
- 429(LOUNGE-007) 응답에 "셀소는 하루에 한 번만 등록할 수 있습니다" 토스트 처리
