# 채팅 말풍선별 "안 읽은 사람 수" (카톡식) 설계

- 작성일: 2026-06-20
- 대상 모듈: `meeple-chatting`, `meeple-infra`, `meeple-core`, `meeple-api`

## 목표

카카오톡처럼 채팅방의 각 메시지(말풍선) 옆에 **그 메시지를 아직 읽지 않은 참가자 수**를 표시한다.
내가 방에 머무는 동안 상대가 메시지를 읽으면 내 화면의 숫자가 **실시간으로 감소**해야 한다.

현재 시스템에는 멤버별 **누적** 미확인 수(`chat_room_members.unread_count`, 방 목록 뱃지용)만 있고,
메시지별 안 읽은 사람 수 개념은 없다. 이 둘은 별개이며 본 작업은 후자를 추가한다.

## 결정 사항 (브레인스토밍 합의)

1. **실시간 감소 필요** — 완전 카톡 UX. 읽음 이벤트를 다른 참가자에게 즉시 브로드캐스트한다.
2. **클라이언트 계산** — 서버는 멤버별 "읽음 포인터"만 제공하고, 말풍선별 숫자는 클라이언트가 산출한다.
   (서버 측 메시지×멤버 집계 쿼리 불필요, 확장성 우수, 미팅 그룹은 소수 인원)
3. **STOMP 실시간 전용 읽음 보고** — 발송과 동일하게 STOMP로 읽음을 보고하고 즉시 브로드캐스트한다.

## 핵심 모델: 읽음 포인터(read pointer)

멤버마다 "어디까지 읽었는가"를 메시지 id 하나(`lastReadMessageId`)로 표현한다.

말풍선 `M`의 안 읽은 사람 수(클라이언트 계산):

```
count(M) = | { P ∈ 활성 참가자 :  P.userId != M.senderId  AND  P.lastReadMessageId < M.id } |
```

- 발신자는 자기 메시지를 읽은 것으로 보아 자동 제외(카톡과 동일).
- 나간(DEACTIVE) 참가자는 세지 않는다 → 클라이언트가 구분할 수 있게 참가자 `active` 여부를 함께 내려준다.
- `lastReadMessageId`가 `null`(한 번도 안 읽음)이면 모든 메시지를 안 읽은 것으로 본다.

### 스키마 변경

`chat_room_members`에 컬럼 추가:

```
last_read_message_id BIGINT NULL  -- 이 참가자가 마지막으로 읽은 메시지 id (없으면 null)
```

기존 `unread_count`(방 목록 뱃지), `last_read_at`은 용도가 다르므로 그대로 둔다.

## 데이터 흐름 (3개 경로)

### (a) 방 입장 / 히스토리 조회 — REST `GET /chat/v1/rooms/{id}` (기존 확장)

- 첫 페이지에서 이미 참가자 목록을 내려준다. 여기에 참가자별 `lastReadMessageId` + `active`를 추가한다.
- 클라이언트는 이 포인터들로 화면의 모든 말풍선 숫자를 한 번에 계산한다.
- 커서(이후) 페이지는 기존처럼 참가자/방 정보를 다시 싣지 않는다(클라이언트가 보유).

### (b) 새 메시지 — STOMP `/app/{roomId}` (기존 경로, 거의 변경 없음)

- 기존 흐름 유지: 발신자 검증 → `updateLastMessageIfActive` → 메시지 저장 → `increaseForOthers`(뱃지 +1) → `/topic/{roomId}` 브로드캐스트.
- 새 말풍선의 초기 숫자는 클라이언트가 보유한 포인터로 계산한다(발신자 제외 전원이 미확인).
- 발신자의 `lastReadMessageId`를 발송 트랜잭션에서 새 메시지 id로 전진시켜 일관성을 유지한다.
  (발송 = 그 이전 메시지를 모두 읽은 것으로 본다 — 카톡과 동일)

### (c) 읽음 보고 — STOMP `/app/{roomId}/read` (신규) ← 실시간 감소의 핵심

- 페이로드: `{ lastReadMessageId: Long }`
- 서버 처리:
  1. 참가자 검증 — 기존 `existsByChatRoomIdAndUserId` 재사용 (비참가자 거부)
  2. 포인터 **전진(forward-only)** + 뱃지 리셋(`unread_count=0`, `last_read_at=now`)
  3. 영향 행이 1 이상이면 `/topic/{roomId}`로 읽음 이벤트 브로드캐스트:
     `{ chatRoomId, readerId, lastReadMessageId }`
- 다른 클라이언트는 `readerId`의 포인터를 갱신하고 해당 범위 말풍선 숫자를 재계산 → 숫자 즉시 감소.

## 아키텍처 매핑 (헥사고날 + 모듈 경계)

### `meeple-chatting` (실시간 읽음 = command, 발송 경로와 대칭)

- in-port `MarkMessagesAsReadUseCase` + 구현 `MarkMessagesAsReadService` (`@Service @Transactional`)
- out-port `AdvanceReadPointerPort` — forward-only 포인터 전진 + 뱃지 리셋, 영향 행 수 반환
- in-port `GetChatRoomMemberPort.existsByChatRoomIdAndUserId` 재사용 (참가자 검증)
- `ChatMessageController`에 `@MessageMapping("/{roomId}/read")` 추가 →
  서비스가 "갱신됨" 여부를 반환하면 그때만 읽음 이벤트 DTO `MessageReadDto`를 `/topic/{roomId}`로 브로드캐스트
- 발송 서비스처럼 **멤버 도메인 로드 없이 타깃 쿼리**로 처리
  (기존 `updateLastMessageIfActive` 조건부 UPDATE 패턴과 동일한 스타일)
- 신규 요청 DTO `ChatReadReportRequest { lastReadMessageId }`, 응답 DTO `MessageReadDto { chatRoomId, readerId, lastReadMessageId }`

### `meeple-infra` `ChatRoomMemberAdapter` (같은 엔티티 → 한 어댑터에서 함께 구현)

- `AdvanceReadPointerPort` 구현 = `@Modifying` 조건부 벌크 UPDATE (반환: 영향 행 수)

  ```
  update ChatRoomMemberEntity m
  set m.lastReadMessageId = :messageId,
      m.unreadCount = 0,
      m.lastReadAt = :now
  where m.chatRoomId = :chatRoomId
    and m.userId = :userId
    and m.status = ACTIVE
    and m.deletedAt is null
    and (m.lastReadMessageId is null or m.lastReadMessageId < :messageId)
  ```

- `GetChatParticipantDaoImpl` 프로젝션에 `member.lastReadMessageId`, status(active 판정) 추가
- `ChatRoomMemberEntity`에 `last_read_message_id` 컬럼 매핑 추가

### `meeple-core` (query 노출)

- `ChatParticipant` read model에 `lastReadMessageId: Long?`, `active: Boolean` 추가
- REST `MarkChatRoomAsReadService`(뱃지 리셋)는 변경하지 않는다. 권위 있는 포인터/실시간 전파는 STOMP 읽음 경로가 담당.

### `meeple-api`

- `ChatParticipantResponse`에 `lastReadMessageId`, `active` 추가

## 동시성 / 정합성

- **forward-only**: 순서가 뒤바뀐 읽음 프레임이 포인터를 뒤로 못 돌리도록 WHERE에 `< :messageId` 조건.
  포인터가 이미 같거나 크면 영향 행 0 → 브로드캐스트 생략(멱등).
- **데드락**: 읽음 경로는 `chat_room_members` 단일 행만 UPDATE하고 `chat_rooms` 락을 잡지 않는다.
  기존 발송/나가기(방 락 선점) 락 순서와 충돌하지 않는다.
- **권한**: 읽음 프레임도 발송과 동일하게 활성 참가자만 처리(비참가자 위조 차단). 토픽 구독 자체는 기존 SUBSCRIBE 인가가 막는다.

## 테스트 전략 (CLAUDE.md)

- **E2E (meeple-api, AbstractIntegrationSupport + Testcontainers)**:
  - 메시지 발송 → STOMP 읽음 프레임 전송 → `/topic/{roomId}`로 읽음 이벤트 수신 확인
  - `GET /rooms/{id}` 상세에서 참가자별 `lastReadMessageId`/`active` 노출 확인
  - forward-only(역행 무시), 비참가자 읽음 보고 거부 케이스
- 기존 발송 E2E에 발신자 포인터 전진 단언 추가
- 포인터 계산은 SQL WHERE 조건이므로 도메인 유닛 테스트는 최소(별도 도메인 로직 없음)

## 범위 밖 (YAGNI)

- "누가 읽었는지" 이름/목록 표시 (숫자만)
- 다중 인스턴스 브로커 환경에서의 읽음 이벤트 전파 (현재 단일 인스턴스 simple broker 전제, 발송 브로드캐스트와 동일 제약)
- 클라이언트 측 말풍선 렌더링/상태관리 구현 (백엔드 계약만 정의)
