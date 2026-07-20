# TEAM 채팅방에서 상대 팀만 participants로 노출 — 설계

## 배경 / 문제

채팅방 목록 조회(`GetChatRoomDaoImpl.findActiveByUserId`)는 각 방의 참가자를 "조회자(나)만 제외한 나머지"로 내려준다. SOLO(1:1) 방에서는 상대 1명이라 문제가 없지만, TEAM(2:2) 방은 4명(내 팀 2 + 상대 팀 2)이 한 방에 들어가므로 **내 팀원까지 participants에 포함**된다.

요구사항: **TEAM 매칭 채팅방은 상대 팀 구성원만 participants로 반환**한다(내 팀원 제외). SOLO는 기존 동작 유지.

핵심 난점: `chat_room_members`에는 `user_id`만 있고 팀 구분 정보가 없다. 팀 구분은 `team_matches → matched_teams → team_members` 쪽에만 존재한다.

## 결정 사항

- **팀 구분을 `chat_room_members.team_id`로 비정규화**한다(채팅방 생성 시점 스냅샷). 조회는 내 행과의 비교로 해결하며, 채팅 조회를 팀 도메인 구조에 결합시키지 않는다.
- 운영에 TEAM 채팅방이 아직 없으므로 **기존 행 백필은 하지 않는다**.
- 스키마 변경은 이 프로젝트 관례(Flyway 미사용)를 따른다: 엔티티 `@Column` 추가 + `docs/migration/*.sql` 문서화 + 로컬 `ddl-auto: update`.
- `SaveChatRoomCommand`의 참가자 입력을 **구조체 리스트**(`SaveChatRoomParticipant(userId, teamId?)`)로 교체한다(가벼운 `Map` 추가 대신, 일급 컬렉션·명시적 타입 컨벤션에 부합).

## 변경 범위

### 1. 스키마 / 엔티티

- `ChatRoomMemberEntity`에 컬럼 추가: `@Column(name = "team_id") val teamId: Long? = null` (nullable — SOLO는 null).
- `docs/migration/chat_room_members_team_id.sql`:
  ```sql
  -- chat_room_members: TEAM 채팅방에서 내 팀/상대 팀 구분을 위해 생성 시점의 team_id를 스냅샷으로 보관.
  -- SOLO 방은 NULL. 조회는 같은 방의 내 행과 team_id를 비교해 상대 팀만 노출한다(self-join, ux_chat_room_id_user_id로 seek).
  ALTER TABLE chat_room_members
      ADD COLUMN team_id BIGINT NULL;
  ```
- **인덱스 추가 없음**: 조회 필터는 기존 `ux_chat_room_id_user_id`(self-join)로 받쳐지고, `team_id`는 seek 키가 아니라 비교 컬럼이므로 별도 인덱스가 필요 없다.

### 2. 도메인 (`ChatRoomMember`)

- 필드 추가: `val teamId: Long? = null`.
- 팩토리 변경: `join(chatRoomId, userId, now)` → `join(chatRoomId, userId, teamId, now)`로 `teamId`를 받아 보관.

### 3. 커맨드 (`SaveChatRoomCommand`)

- 새 구조체: `data class SaveChatRoomParticipant(val userId: Long, val teamId: Long?)`.
- `participantUserIds: List<Long>` → `participants: List<SaveChatRoomParticipant>`로 교체.
- `SaveChatRoomService.save()`: 각 participant의 `teamId`를 `ChatRoomMember.join(..., teamId = it.teamId, ...)`로 전달.

### 4. 호출 측

- `SendTeamInterestService.completeMatch()` (TEAM): `teams: List<Team>`를 순회해 `team.activeMemberIds()`마다 `SaveChatRoomParticipant(userId, team.id)`를 만든다.
  ```kotlin
  val participants: List<SaveChatRoomParticipant> = teams.flatMap { team: Team ->
      team.activeMemberIds().map { memberId: Long -> SaveChatRoomParticipant(userId = memberId, teamId = team.id) }
  }
  ```
  - 알림용 `allMemberIds`(행위자 제외 수신자 산출)는 `participants.map { it.userId }` 또는 기존 방식 유지로 보존한다.
- `SendInterestService.completeMatch()` (SOLO): `match.participantUserIds()`를 `SaveChatRoomParticipant(userId, teamId = null)`로 매핑.

### 5. 매퍼 (`ChatRoomMemberMapper`)

- `toEntity()`/`toDomain()`에 `teamId` 왕복 추가.

### 6. 조회 (`GetChatRoomDaoImpl.findPartnerParticipants`) — 핵심

내 참가자 행을 self-join해 한 쿼리로 상대만 남긴다:

```
from partner(chatRoomMember)
join me(chatRoomMember) on me.chatRoomId == partner.chatRoomId and me.userId == :userId
join userDetail on userDetail.userId == partner.userId
where partner.chatRoomId in :roomIds
  and partner.userId != :userId
  and (me.teamId is null or partner.teamId != me.teamId)
order by partner.chatRoomId asc, partner.userId asc
```

- **SOLO**: `me.teamId is null` → 조건 참 → 전원 유지(나만 제외, 기존 동작 그대로).
- **TEAM**: `me.teamId = T` → `partner.teamId != T` → 내 팀원 제외, 상대 팀 2명만.
- 내 행은 `(chat_room_id, user_id)` 유니크이며 roomIds 자체가 내 ACTIVE 행에서 나왔으므로 항상 존재한다. self-join은 `ux_chat_room_id_user_id`로 seek.
- `ChatRoomSummary`, 응답 DTO, `findActiveRooms`는 **변경 없음**(team_id를 응답에 노출하지 않는다). 나간(DEACTIVE) 참가자 포함 등 기존 규칙 유지.

### 7. 테스트

- **도메인 유닛(Kotest)**: `ChatRoomMember.join`이 `teamId`를 그대로 담는지(및 SOLO에서 null) 검증.
- **E2E(`oneulsogae-api`, Testcontainers)**:
  - TEAM 방(4인: 팀A 2 + 팀B 2)을 생성하고 팀A 구성원으로 목록 조회 → participants가 팀B 2명만인지(내 팀원 미포함) 검증.
  - SOLO 방은 기존대로 상대 1명이 나오는지(회귀) 검증.
  - infra `testFixtures`로 `chat_room_members.team_id`를 세팅(픽스처에 teamId 파라미터 추가 필요 시 포함).

## 트레이드오프 / 주의

- **커맨드 시그니처 변경**: `participantUserIds`를 구조체 리스트로 바꾸면 호출부 2곳(`SendTeamInterestService`, `SendInterestService`)·`SaveChatRoomService`·관련 테스트가 함께 바뀐다. 의도된 범위.
- **스냅샷 의미**: 성사 이후 팀원이 팀을 바꾸거나 팀이 해체돼도 채팅 participants 구분은 성사 시점 구성 그대로 유지된다(라이브 조인이 아니므로). 의도된 동작.
- **TEAM 참가자 수 가정**: 현재 2:2라 상대 2명. 팀 인원이 늘어도 `team_id != 내 team_id`로 일반화되어 동작한다.

## 영향받는 파일 (예상)

- `oneulsogae-infra/.../chat/command/entity/ChatRoomMemberEntity.kt` (컬럼)
- `oneulsogae-infra/.../chat/command/mapper/ChatRoomMemberMapper.kt` (왕복)
- `oneulsogae-infra/.../chat/query/GetChatRoomDaoImpl.kt` (self-join 필터)
- `oneulsogae-core/.../chat/command/domain/ChatRoomMember.kt` (필드·팩토리)
- `oneulsogae-core/.../chat/command/application/SaveChatRoomService.kt` (teamId 전달)
- `oneulsogae-core/.../chat/command/application/port/in/command/SaveChatRoomCommand.kt` (구조체 리스트 + `SaveChatRoomParticipant`)
- `oneulsogae-core/.../match/command/application/SendTeamInterestService.kt` (TEAM participants 구성)
- `oneulsogae-core/.../match/command/application/SendInterestService.kt` (SOLO participants 구성)
- `docs/migration/chat_room_members_team_id.sql` (신규)
- 도메인 유닛 테스트 + `oneulsogae-api` E2E 테스트 + 필요 시 infra `testFixtures`
```
