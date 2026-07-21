# 라운지 셀소 대화 신청 설계

## 배경

라운지 셀소(셀프 소개팅) 상세 화면에서 글을 본 사용자가 작성자에게 **대화를 신청**할 수 있어야 한다.
신청에는 코인이 든다. 신청을 받은 작성자는 여러 신청자 중 **원하는 상대를 골라 수락**하고, 수락하면 두 사람의 채팅방이 열린다.

기존 1:1 소개팅(solomatch)은 배치가 만든 매칭 위에서 양쪽이 관심을 보내 성사되는 구조라, "글을 보고 아무나 신청 → 작성자가 골라 수락"하는 라운지 흐름과 상태 모델이 다르다.
따라서 solomatch를 재사용하지 않고 **lounge 도메인에 별도 신청 애그리거트를 둔다.**

## 범위

포함:
- 대화 신청 API (코인 차감)
- 내 셀소에 온 신청 목록 조회 API
- 신청 수락 API (코인 차감 + 채팅방 생성)
- 신청 받음 / 수락됨 알람

제외(이번 범위 밖):
- 거절 API, 신청 만료, 코인 환불
- 신청 취소
- 프론트엔드 변경 (변경 필요 지점만 안내)

## 흐름

```
[신청자 B]  셀소 상세 → 대화 신청
            → 코인 32 차감(LOUNGE_CHAT_INIT)
            → lounge_chat_requests(PENDING) 생성
            → 작성자 A에게 "대화 신청 받음" 알람 (커밋 후)

[작성자 A]  내 셀소에 온 신청 목록 조회 (신청자 프로필 포함)
            → 원하는 상대 선택 → 수락
            → 코인 32 차감(LOUNGE_CHAT_ACCEPT)
            → chat_rooms(match_type=LOUNGE, match_id=requestId) 생성 + 참가자 2명
            → 신청 상태 ACCEPTED
            → 신청자 B에게 "대화 신청 수락됨" 알람 (커밋 후)
```

작성자는 **여러 신청을 각각 수락할 수 있다.** 수락할 때마다 코인을 내고 채팅방이 하나씩 생긴다.
수락되지 않은 신청은 PENDING으로 남는다. (거절·만료·환불 없음)

## 데이터 모델

### 신규 테이블 `lounge_chat_requests`

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `id` | bigint PK AUTO_INCREMENT | |
| `post_id` | bigint NOT NULL | 대상 셀소 글 (`lounge_posts.id`) |
| `requester_user_id` | bigint NOT NULL | 신청자 |
| `receiver_user_id` | bigint NOT NULL | 신청을 받은 사용자(글 작성자). `lounge_posts.user_id` 비정규화 |
| `status` | varchar(20) NOT NULL | `PENDING` / `ACCEPTED` |
| `created_at` | datetime NOT NULL | BaseEntity |
| `updated_at` | datetime NOT NULL | BaseEntity |
| `deleted_at` | datetime NULL | BaseEntity (soft delete) |

인덱스:
- `UNIQUE ux_post_requester (post_id, requester_user_id)` — 같은 글에 중복 신청 차단 (DB 레벨 최종 방어선)
- `INDEX idx_receiver_user_id_id (receiver_user_id, id)` — 내가 받은 신청 목록을 최신순(id desc)으로 seek
- `INDEX idx_requester_user_id_id (requester_user_id, id)` — 내가 보낸 신청 목록을 최신순으로 seek. `ux_post_requester`는 선두가 `post_id`라 `requester_user_id`로 seek할 수 없어 따로 둔다

**수신자를 비정규화하는 이유**: 받은/보낸 목록이 모두 글과 무관하게 "한 사용자의 신청을 최신순으로" 훑는 조회다.
수신자 컬럼이 없으면 `lounge_posts`를 조인해 내 글을 모은 뒤 `id desc`로 정렬해야 해서 filesort가 된다.
글 작성자는 생성 후 바뀌지 않으므로 복사 저장해도 원본과 어긋나지 않는다.

두지 않는 컬럼과 이유:
- **chat_room_id**: `chat_rooms(match_type='LOUNGE', match_id=request_id)` 복합 유니크로 역참조할 수 있다. 신청 목록 조회는 방 id를 내려주지 않으므로 조인조차 하지 않는다.

### 마이그레이션

`docs/migration/lounge_chat_requests.sql`에 `CREATE TABLE` DDL을 추가한다. 기존 테이블 변경은 없다.

## 공통 enum 변경

### `CoinUsageType` (common/coin)

```kotlin
LOUNGE_CHAT_INIT("라운지 대화 신청", 32),
LOUNGE_CHAT_ACCEPT("라운지 대화 수락", 32),
```

금액은 기존 소개팅(`DATING_INIT`/`DATING_ACCEPT`)과 동일한 32로 맞춘다.
차감액은 서버가 `CoinUsageType.coinAmount`로 산출한다. (클라이언트가 금액을 정하지 않음)

### `ChatRoomMatchType` (common/chat)

```kotlin
LOUNGE,  // 라운지 셀소 대화 신청 수락으로 생성된 채팅방 (lounge_chat_requests.id)
```

`chat_rooms`는 이미 `(match_type, match_id)` 다형성 참조 + 복합 유니크라 **스키마 변경 없이** 붙는다.
이 enum에 `when`이 걸린 두 곳에 분기를 추가한다 (둘 다 1:1 사용자 방이므로 SOLO와 동일 취급):

- `DeactivateChatRoomMemberService` → `SOLO_LEFT_MESSAGE`와 같은 나감 안내 문구
- `CreateReportService` → `ReportTargetType.USER`

### `AlarmType` (common/alarm)

```kotlin
LOUNGE_CHAT_REQUEST_RECEIVED("대화 신청 받음"),
LOUNGE_CHAT_ACCEPTED("대화 신청 수락됨"),
```

`category()`는 **기존 `NotificationCategory.ONE_TO_ONE`에 묶는다.**
`NotificationCategory.LOUNGE`를 신설하면 `NotificationPreference`에 컬럼 추가 + 마이탭 토글 + 프론트 대응까지 번져 범위가 커진다. 라운지 대화도 1:1 소개 성격이므로 기존 카테고리로 충분하다.

## API

### 1. 대화 신청

```
POST /lounge/v1/self-intro-posts/{postId}/chat-requests
```

요청 본문 없음 (경로의 `postId`와 인증 사용자로 충분).

응답:
```json
{ "data": { "requestId": 1 } }
```

에러:
| 상황 | 코드 |
|---|---|
| 글이 없거나 삭제됨 | 404 `LOUNGE-008` (SELF_INTRO_POST_NOT_FOUND) |
| 본인 글에 신청 | 400 `LOUNGE-009` (LOUNGE_CHAT_REQUEST_SELF) |
| 동성이거나 성별 확인 불가 | 400 `LOUNGE-014` (LOUNGE_CHAT_REQUEST_SAME_GENDER) |
| 이미 신청한 글 | 409 `LOUNGE-010` (LOUNGE_CHAT_REQUEST_DUPLICATED) |
| 코인 부족 | 400 (기존 `COIN-*` INSUFFICIENT_COIN_BALANCE) |
| 동시 요청 겹침 | 409 (기존 락 에러) |

### 2. 대화 신청 목록 (받은 / 보낸)

받은 신청과 보낸 신청은 성격이 다른 별개 목록이라 **엔드포인트를 나누고 각자 커서를 갖는다.**
(한 응답에 두 리스트를 담으면 `cursor`/`hasNext`/`nextCursor` 하나가 두 리스트를 동시에 가리킬 수 없다)

```
GET /lounge/v1/chat-requests/received?cursor={requestId}   # 내가 쓴 모든 셀소에 온 신청
GET /lounge/v1/chat-requests/sent?cursor={requestId}       # 내가 남의 셀소에 보낸 신청
```

둘 다 요청한 사용자 본인의 신청만 돌려주므로 별도 소유권 검증이 없다(기준 컬럼이 곧 본인).
최신순(requestId 내림차순) 커서 페이징, 페이지 크기 20.

항목의 `partner*`는 **이 신청에서 나의 상대방**이다. 받은 목록에서는 신청자, 보낸 목록에서는 글 작성자다.

```json
{
  "data": {
    "items": [
      {
        "requestId": 12,
        "postId": 5,
        "partnerUserId": 7,
        "partnerNickname": "홍길동",
        "partnerGender": "MALE",
        "partnerAge": 29,
        "partnerProfileImageCode": "PROFILE_07",
        "partnerActivityArea": "서울특별시 마포구",
        "partnerJob": "개발자",
        "partnerCompanyName": "오늘소개",
        "status": "ACCEPTED",
        "requestedAt": "2026-07-21T10:00:00"
      }
    ],
    "acceptCoinAmount": 32,
    "hasNext": false,
    "nextCursor": null
  }
}
```

수락으로 생긴 채팅방 id는 **싣지 않는다.** 신청 자체는 채팅방을 만들지 않고, 수락 직후에는 수락 API 응답의 `chatRoomId`로 이동하며, 그 뒤에는 채팅방 목록에서 확인한다.
`partnerProfileImageCode`·`partnerActivityArea`·`partnerJob`·`partnerCompanyName`은 상대 프로필에서 온 표시용 값이며, 미설정이면 null이다. (신청 자체는 목록에서 빠지지 않는다)
`acceptCoinAmount`(수락 시 드는 코인)는 **받은 목록에만** 싣는다. 보낸 신청은 내가 수락하는 것이 아니다.
신청마다 다르지 않은 전역 정책값이라 항목이 아니라 응답 루트에 한 번만 담는다.

### 3. 신청 수락

```
POST /lounge/v1/chat-requests/{requestId}/accept
```

경로가 `self-intro-posts` 아래가 아닌 이유: 신청은 `requestId`만으로 식별되고, 대상 글은 신청 행이 이미 알고 있다. 중복 식별자를 경로에 넣지 않는다.

응답:
```json
{ "data": { "chatRoomId": 3 } }
```

에러:
| 상황 | 코드 |
|---|---|
| 신청이 없음 | 404 `LOUNGE-012` (LOUNGE_CHAT_REQUEST_NOT_FOUND) |
| 내 글에 온 신청이 아님 | 403 `LOUNGE-011` |
| 이미 수락됨 | 409 `LOUNGE-013` (LOUNGE_CHAT_REQUEST_ALREADY_ACCEPTED) |
| 코인 부족 | 400 (기존 `COIN-*`) |
| 동시 요청 겹침 | 409 (기존 락 에러) |

## 도메인 모델

`LoungeChatRequest` (core/lounge/command/domain)

```kotlin
data class LoungeChatRequest(
    val id: Long = 0,
    val postId: Long,
    val requesterUserId: Long,
    val receiverUserId: Long,
    val status: LoungeChatRequestStatus = LoungeChatRequestStatus.PENDING,
    val createdAt: LocalDateTime? = null,
)
```

`LoungeChatRequestStatus` enum(`PENDING`, `ACCEPTED`)은 common/lounge에 둔다. (infra 엔티티와 core가 함께 쓴다)

도메인이 캡슐화하는 규칙 (서비스에 `if…throw` 나열 금지):
- `create(postId, requesterUserId, postAuthorUserId, requesterGender, postAuthorGender)` — 본인 글 신청을 막고(`LOUNGE_CHAT_REQUEST_SELF`) 이성인지 검증한 뒤(`validateOppositeGender`) 작성자를 `receiverUserId`로 확정한 PENDING 신청을 만든다. **본인 글 검사를 성별 검사보다 먼저** 한다(본인은 성별도 같아 순서가 바뀌면 엉뚱한 사유가 나간다)
- `validateOppositeGender(requesterGender, postAuthorGender)` — 성별이 같으면 `LOUNGE_CHAT_REQUEST_SAME_GENDER`. **둘 중 하나라도 null이면 이성임을 보장할 수 없으므로 함께 막는다**(프로필이 없거나 온보딩 미완료인 비정상 상태 — 통과시키면 동성 대화가 열린다)
- `acceptBy(actorUserId: Long): LoungeChatRequest` — 수락자가 `receiverUserId`가 아니면 `LOUNGE_POST_NOT_OWNED`, 이미 ACCEPTED면 `LOUNGE_CHAT_REQUEST_ALREADY_ACCEPTED`, 통과하면 상태를 ACCEPTED로 전이한 새 모델을 반환한다

신청 행이 수신자를 알고 있으므로 수락 경로는 글을 다시 읽지 않는다. (`GetLoungePostPort`는 신청 경로에서만 쓴다)

## 레이어 배치

기존 lounge 도메인의 command/query 분리 규약을 그대로 따른다.

### core (`oneulsogae-core`)

```
lounge/
  LoungeErrorCode.kt                                   (에러 코드 6개 추가)
  command/
    domain/LoungeChatRequest.kt
    domain/event/LoungeChatRequested.kt
    domain/event/LoungeChatRequestAccepted.kt
    application/RequestLoungeChatService.kt             (신청)
    application/AcceptLoungeChatService.kt              (수락)
    application/LoungeEventHandler.kt                   (알람 — AFTER_COMMIT, REQUIRES_NEW)
    application/port/in/RequestLoungeChatUseCase.kt
    application/port/in/AcceptLoungeChatUseCase.kt
    application/port/in/result/RequestLoungeChatResult.kt
    application/port/in/result/AcceptLoungeChatResult.kt
    application/port/out/GetLoungePostPort.kt           (글 작성자 확인 — 잠금 없는 단건 조회)
    application/port/out/GetLoungeChatRequestPort.kt
    application/port/out/SaveLoungeChatRequestPort.kt
  query/
    dao/GetLoungeChatRequestDao.kt
    dto/LoungeChatRequestView.kt
    dto/LoungeChatRequestPage.kt
    service/GetLoungeChatRequestsService.kt
    service/port/in/GetLoungeChatRequestsUseCase.kt
```

query 패키지는 자기 dao에만 의존하고 command 도메인·포트를 참조하지 않는다.
작성자 본인 검증에 필요한 `lounge_posts.user_id`도 query dao가 자체 조회한다. (command의 `GetLoungePostPort`를 공유하지 않음)

### 다른 도메인 참조 (모두 in-port)

- `SpendCoinUseCase` (coin) — 코인 차감
- `GetUserDetailUseCase` (user) — 이성 여부 판정에 쓸 신청자·작성자 성별 조회 (신청 경로)
- `SaveChatRoomUseCase` (chat) — 채팅방 생성
- `GetChatRoomMatchUseCase`는 쓰지 않는다. 목록 조회의 `chatRoomId`는 query dao가 `chat_rooms`를 직접 조인한다 (읽기 모델 투영)
- `SaveAlarmUseCase`, `GetUserDetailUseCase` (alarm/user) — 이벤트 핸들러에서만

### infra (`oneulsogae-infra`)

```
lounge/command/
  entity/LoungeChatRequestEntity.kt
  mapper/LoungeChatRequestMapper.kt
  repository/LoungeChatRequestJpaRepository.kt
  adapter/LoungeChatRequestAdapter.kt      (Get/Save 포트 구현, Spring Data 파생 쿼리)
  adapter/LoungePostAdapter.kt             (기존 — GetLoungePostPort 구현 추가)
lounge/query/
  GetLoungeChatRequestDaoImpl.kt           (QueryDSL — user_details·chat_rooms 조인)
```

목록 조회 QueryDSL:
- `lounge_chat_requests` ⋈ `user_details`(신청자 프로필: 닉네임·성별·생년월일)
- `left join chat_rooms on match_type = 'LOUNGE' and match_id = request.id` — `(match_type, match_id)` 유니크 인덱스로 seek
- `where post_id = :postId (and id < :cursor)` `order by id desc` `limit :size + 1` — `idx_post_id_id`로 seek + 정렬
- 만 나이는 기존 `SelfIntroPostDetailView`와 같이 dao가 `birthday`를 실어 오고 서비스가 `TimeGenerator.today()`로 계산한다

### api (`oneulsogae-api`)

```
lounge/LoungeChatRequestController.kt
lounge/response/LoungeChatRequestResponse.kt
lounge/response/LoungeChatRequestPageResponse.kt
lounge/response/AcceptLoungeChatResponse.kt
```

## 트랜잭션·동시성

### 신청 (`RequestLoungeChatService`)

```
@DistributedLock(prefix = LOUNGE_CHAT_REQUEST, keys = ["#postId", "#userId"], waitTime = 0)
@Transactional
```

- 락 키를 `postId + userId`로 잡는 이유: 경합 대상이 "이 사용자가 이 글에 신청했는가"라는 유니크 조건이다. 글 단위로 잠그면 서로 다른 신청자끼리 불필요하게 직렬화된다.
- `waitTime = 0` — 더블클릭으로 겹친 요청은 즉시 409. (멱등성 가드가 없으므로 이중 과금 fail-fast)
- 락을 뚫고 들어온 경우에도 `ux_post_requester` 유니크가 최종 방어선이다.
- 한 트랜잭션: 중복 신청 확인 → 신청 저장 → 코인 차감. 하나라도 실패하면 함께 롤백.

### 수락 (`AcceptLoungeChatService`)

```
@DistributedLock(prefix = LOUNGE_CHAT_ACCEPT, keys = ["#requestId"], waitTime = 0)
@Transactional
```

- 락 키를 `requestId`로 잡는 이유: 경합 대상이 신청 한 건의 상태 전이다.
- 한 트랜잭션: 신청 조회 → 소유권·상태 검증 → 상태 ACCEPTED 저장 → 코인 차감 → 채팅방 생성. 하나라도 실패하면 함께 롤백.
- `SaveChatRoomService`는 `(matchType, matchId)`로 이미 멱등 생성이므로, 중복 호출이 들어와도 방이 두 개 생기지 않는다.

`LockKeyConstraints`에 `LOUNGE_CHAT_REQUEST`, `LOUNGE_CHAT_ACCEPT` 상수를 추가한다.

### 알람

`LoungeEventHandler`가 `@TransactionalEventListener(AFTER_COMMIT)` + `@Transactional(REQUIRES_NEW)`로 best-effort 저장한다.
알람 저장이 실패해도 신청·과금·채팅방은 롤백되지 않는다. (`MatchEventHandler`와 동일한 패턴)

- `LoungeChatRequested` → 작성자에게 `LOUNGE_CHAT_REQUEST_RECEIVED`, `fromUserId` = 신청자
- `LoungeChatRequestAccepted` → 신청자에게 `LOUNGE_CHAT_ACCEPTED`, `fromUserId` = 작성자

문구는 `GetUserDetailUseCase`로 닉네임을 조회해 채우고, 닉네임이 없으면 기존 핸들러처럼 "상대방"으로 대체한다.

## 테스트

### 도메인 유닛 (Kotest, `oneulsogae-api/src/test/kotlin/.../domain/lounge`)

`LoungeChatRequestTest`
- 본인 글에 신청하면 `LOUNGE_CHAT_REQUEST_SELF`
- 정상 생성 시 상태가 PENDING
- 작성자가 아닌 사람이 수락하면 `LOUNGE_POST_NOT_OWNED`
- 이미 ACCEPTED인 신청을 수락하면 `LOUNGE_CHAT_REQUEST_ALREADY_ACCEPTED`
- 정상 수락 시 상태가 ACCEPTED로 전이

### E2E (`AbstractIntegrationSupport` + Testcontainers)

`RequestLoungeChatE2ETest`
- 신청 성공 → 신청 행 생성, 코인 32 차감, 코인 원장에 음수 기록
- 본인 글 신청 → 400
- 중복 신청 → 409, 코인은 한 번만 차감
- 코인 부족 → 400, 신청 행 없음(롤백)
- 없는 글 → 404

`GetLoungeChatRequestsE2ETest`
- 작성자가 조회 → 신청자 프로필과 상태가 최신순으로 내려옴
- 수락된 신청은 `chatRoomId`가 채워지고, PENDING은 null
- 남의 글 조회 → 403
- 커서 페이징 (`nextCursor`/`hasNext`)

`AcceptLoungeChatE2ETest`
- 수락 성공 → `chat_rooms`(match_type=LOUNGE) 1건 + 참가자 2명, 코인 32 차감, 상태 ACCEPTED
- 같은 글의 다른 신청도 별도로 수락 가능 → 채팅방 2개
- 이미 수락한 신청 재수락 → 409, 코인 추가 차감 없음
- 작성자가 아닌 사람이 수락 → 403
- 코인 부족 → 400, 채팅방·상태 변경 없음(롤백)

## 프론트엔드 대응 안내 (백엔드에서 수정하지 않음)

이 백엔드 변경에 맞춰 `meeple-frontend`에서 다음이 필요하다:

1. **셀소 상세 화면**: "대화 신청" 버튼 → `POST /lounge/v1/self-intro-posts/{postId}/chat-requests`. 응답 `requestId`. 잔액 부족·중복 신청(409) 처리.
   비용은 **상세 조회 응답의 `data.chatRequestCoinAmount`를 그대로 표시**한다(하드코딩 금지 — 정책이 바뀌면 서버 값만 바뀐다). 글마다 다르지 않은 전역 정책값이라 목록 응답에는 싣지 않는다.
   버튼 상태는 **상세 조회 응답의 `data.chatRequestedByMe`로 정한다.** true면 "신청함"으로 바꾸고 다시 누를 수 없게 한다(누르면 409 LOUNGE-010). 상태(PENDING/ACCEPTED)는 구분하지 않는다 — 어느 쪽이든 재신청이 불가능하다.
2. **라운지 목록 화면**: `GET /lounge/v1/self-intro-posts` 응답 루트의 배지 두 개를 표시한다. 0이면 숨긴다.
   - `data.receivedPendingChatRequestCount` — "받은 신청". 내가 쓴 **모든** 셀소에 온 신청 중 **아직 수락하지 않은(PENDING)** 건수. 내가 수락하면 줄어든다.
   - `data.sentPendingChatRequestCount` — "보낸 신청". 내가 남의 셀소에 보낸 신청 중 **아직 수락되지 않은(PENDING)** 건수(= 상대의 응답을 기다리는 수). 상대가 수락하면 줄어든다.
3. **대화 신청 화면(탭 2개)**: 받은 신청 `GET /lounge/v1/chat-requests/received`, 보낸 신청 `GET /lounge/v1/chat-requests/sent`. 탭마다 **자기 커서**로 페이징한다(`nextCursor`/`hasNext`). 카드에는 `partnerNickname`·`partnerGender`·`partnerAge`·`partnerProfileImageCode`·`partnerActivityArea`·`partnerJob`·`partnerCompanyName`을 쓰고, `postId`로 글 상세에 이동한다. 받은 탭은 상태별 액션(PENDING → "수락"), 보낸 탭은 상태 표시만(PENDING → "대기 중")이면 된다. **목록 응답에 채팅방 id가 없으므로** ACCEPTED 항목에서 바로 채팅방으로 보낼 수 없다 — 수락 직후에는 수락 API 응답의 `chatRoomId`로 이동하고, 그 뒤에는 채팅방 탭에서 이어간다.
4. **수락 액션**: `POST /lounge/v1/chat-requests/{requestId}/accept` → 응답 `chatRoomId`로 채팅방 이동.
   수락 비용은 **받은 목록 응답 루트의 `data.acceptCoinAmount`를 그대로 표시**한다(하드코딩 금지). 신청마다 다르지 않아 항목이 아니라 루트에 한 번만 오며, 보낸 목록에는 없다.
5. **알람 목록**: `AlarmType`에 `LOUNGE_CHAT_REQUEST_RECEIVED`, `LOUNGE_CHAT_ACCEPTED` 문구/아이콘 추가. 알림 설정 토글은 기존 "1:1 소개" 항목이 그대로 관장하므로 마이탭 변경은 없다.
6. **채팅방 목록/상세**: 채팅방 `type`에 `LOUNGE`가 추가된다. `SOLO`와 동일하게 1:1 사용자 방으로 렌더링하면 된다.
7. **코인 사용 내역 화면**: 코인 사용 내역 조회 응답이 `coinUsageType` enum 원문을 그대로 내려주므로, `LOUNGE_CHAT_INIT`(라운지 대화 신청) / `LOUNGE_CHAT_ACCEPT`(라운지 대화 수락) 라벨 매핑을 추가해야 한다.
