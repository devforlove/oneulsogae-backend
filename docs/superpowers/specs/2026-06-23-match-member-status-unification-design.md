# MatchMember 상태 모델 통합 설계 (accepted 제거)

작성일: 2026-06-23
브랜치: `main` 기준(작업 브랜치 분기 예정)

## 1. 배경 / 목표

현재 솔로(1:1) 매칭의 참가자 `MatchMember`는 **두 축**을 따로 가진다.
- `accepted: Boolean?` — null(무응답) / true(관심 신청함)
- `status: MatchMemberStatus` — `ACTIVE`(정상 참가) / `DEACTIVE`(채팅 나감)

이 둘을 **하나의 참가자 상태 enum**으로 통합한다.

| 새 상태 | 의미 | 진입 시점 |
|---|---|---|
| `WAITING` | 소개 직후·관심 미신청 | 매치 생성(PROPOSED) |
| `APPLY` | 관심 신청함·매치 미성사 | 관심 신청(한쪽만) |
| `ACTIVE` | 매치 성사·활성 | 전원 신청 완료(MATCHED) |
| `DEACTIVE` | 채팅방 나감 | 채팅 나가기 |

**비목표(확정)**
- 매치 헤더 상태 `MatchStatus`(PROPOSED/PARTIALLY_ACCEPTED/MATCHED/CLOSED)는 **그대로 유지**한다. 멤버 status에서 파생하던 방식을 유지하고, 쿼리·읽기모델·API의 `status`/`hasUserInterest` 계약은 보존한다.
- 거절(decline) 상태는 만들지 않는다. 현재 `respond()`는 수락만 하고 무응답은 만료되므로 `WAITING`이 무응답을 커버한다.
- 팀(2:2) 매칭은 범위 밖.

## 2. enum: `MatchMemberStatus` (meeple-common)

```kotlin
enum class MatchMemberStatus(val description: String) {
	WAITING("대기"),    // 소개 직후, 관심 미신청
	APPLY("신청"),      // 관심 신청함, 매치 미성사
	ACTIVE("활성"),     // 매치 성사되어 활성
	DEACTIVE("비활성"), // 채팅방 나감
}
```

## 3. 도메인: `MatchMember` / `MatchMembers`

### 3.1 `MatchMember`
- **`accepted: Boolean?` 필드 제거.**
- `status: MatchMemberStatus = MatchMemberStatus.WAITING` (기본값 WAITING — 생성 시 대기 상태).
- 메서드:
  - `accept()` → **`apply(): MatchMember = copy(status = APPLY)`** (WAITING→APPLY).
  - 신규 **`activate(): MatchMember = copy(status = ACTIVE)`** (성사 시 승격).
  - `deactivate(): MatchMember = copy(status = DEACTIVE)` — 유지(어떤 상태에서든 DEACTIVE로).
  - `delete(now): MatchMember = copy(status = DEACTIVE, deletedAt = now)` — 유지.
  - `isAccepted` → **`hasApplied: Boolean get() = status == APPLY || status == ACTIVE`** ("관심 신청했는가").

### 3.2 `MatchMembers`
- `allAccepted()` → **`allApplied(): Boolean = values.isNotEmpty() && values.all { it.hasApplied }`**
- `anyAccepted()` → **`anyApplied(): Boolean = values.any { it.hasApplied }`**
- `accepted()` → **`applied(): List<MatchMember> = values.filter { it.hasApplied }`** (환불 대상).
- `accept(userId)` → **`apply(userId): MatchMembers`** (해당 멤버 WAITING→APPLY).
- 신규 **`activateAll(): MatchMembers = MatchMembers(values.map { it.activate() })`** (전원 ACTIVE 승격).
- `deactivate(userId)`, `delete(now)` — 유지.

## 4. 도메인: `Match` 전이

```kotlin
fun respond(userId: Long): Match {
	val applied: Match = copy(members = members.apply(userId))   // WAITING→APPLY
	val recomputed: Match = applied.withRecomputedStatus()
	return if (recomputed.status == MatchStatus.MATCHED) recomputed.extendExpirationForMatched() else recomputed
}

private fun withRecomputedStatus(): Match =
	when {
		members.allApplied() -> copy(status = MatchStatus.MATCHED, members = members.activateAll()) // 전원 신청 → 성사 + 전원 ACTIVE
		members.anyApplied() -> copy(status = MatchStatus.PARTIALLY_ACCEPTED)
		else -> copy(status = MatchStatus.PROPOSED)
	}
```

- 한 명 신청: 그 멤버 APPLY, 매치 PARTIALLY_ACCEPTED.
- 두 번째 신청: 전원 APPLY → 매치 MATCHED + **전원 ACTIVE 승격**(먼저 APPLY였던 멤버도 ACTIVE로).
- `hasUserInterest(userId) = members.find(userId)?.hasApplied == true`, `hasPartnerInterest(userId) = members.partnersOf(userId).any { it.hasApplied }`.
- `failureRefunds() = members.applied().map { ... }` (신청자=APPLY/ACTIVE 환불).
- `validateRespondable`의 "이미 응답함" 판정을 `isAccepted` → `hasApplied`로.
- `deactivateMember`, `delete`, `propose` — 유지.

## 5. 영속성: 엔티티 / 매퍼 / 마이그레이션

- `SoloMatchMemberEntity`: **`accepted` 컬럼 제거**, `status` 기본값 `MatchMemberStatus.WAITING`. `idx_user_id_status(user_id, status)` 유지. status는 `@Enumerated(EnumType.STRING)` + `varchar(50)`이라 **DB enum 변경 불필요**(새 값은 문자열).
- `MatchMemberMapper`: `toDomain`/`toEntity`에서 `accepted` 제거.
- 마이그레이션(스키마만, 데이터 유실 허용):
  ```sql
  -- solo_match_members: 참가자 상태를 단일 enum(WAITING/APPLY/ACTIVE/DEACTIVE)으로 통합하며 accepted 제거.
  ALTER TABLE solo_match_members DROP COLUMN accepted;
  ```

## 6. 조회 동작 보존 (중요)

`GetMatchWithPartnerDaoImpl`(매칭 목록/미팅탭):
- 필터 `mySoloMatchMember.status.eq(ACTIVE)` → **`mySoloMatchMember.status.ne(DEACTIVE)`**.
  - 원 의도는 "나간(DEACTIVE) 멤버 제외"였다. 구 모델은 미응답·신청도 모두 `ACTIVE`였으나, 새 모델은 WAITING/APPLY가 별도 값이라 `eq(ACTIVE)`는 그 매치들을 잘못 숨긴다. `ne(DEACTIVE)`로 원 의도(안 나간 내 매치 전부 노출)를 보존한다.
- 프로젝션 `accepted.coalesce(false)`(나/상대 hasInterest) → **`status.in(APPLY, ACTIVE)`** (BooleanExpression 투영).
- 읽기모델 `MatchWithPartner`·API `MatchResponse`의 `hasUserInterest`/`hasPartnerInterest`(boolean) 계약은 유지.

`GetMatchRecordDaoImpl.findMatchedUserIds`: `match.status=MATCHED AND member.status=ACTIVE` — **그대로 정확**(성사 매치의 안 나간 멤버는 ACTIVE). 변경 없음.

## 7. 서비스 (대부분 무변경)

- `SendInterestService`: `match.respond()` 결과의 `MatchStatus`(MATCHED/PARTIALLY_ACCEPTED)로 분기 — **코드 변경 없음**(respond 내부만 바뀜).
- `LeaveChatRoomService`/`DeactivateMatchMemberService`/`RemoveMatchService`: `deactivate()`/`delete()` 호출 — **무변경**.

## 8. 테스트 전략 (통합 실행 검증 포함)

- **도메인 유닛**: `MatchTest`(respond 전이: PARTIALLY→APPLY, MATCHED→전원 ACTIVE / hasUserInterest / delete→DEACTIVE), `MatchMembersTest`(apply/anyApplied/allApplied/activateAll).
- **E2E/통합 실행으로 검증**(요구사항):
  - `SendInterestE2ETest`: 1차 신청 → 신청자 **APPLY** + 매치 PARTIALLY + DATING_INIT; 2차 신청 → **전원 ACTIVE** + 매치 MATCHED + DATING_ACCEPT + 채팅방 생성. 멤버 status를 DB로 단언.
  - `LeaveChatRoomE2ETest`: 한 명 나감 → 그 멤버 **DEACTIVE**, 매치 유지; 마지막 나감 → 매치 CLOSED·전원 soft delete.
  - 매칭 목록/미팅탭 E2E(`GetMeetingTabE2ETest`/매칭 조회): WAITING/APPLY 상태의 내 매치가 **노출**되는지(=ne(DEACTIVE) 필터) 확인.
  - `RunSoloMatchBatchIntegrationTest`: 성사(ACTIVE) 유저 제외 / 이탈(DEACTIVE) 유저 재소개 — 픽스처 status 갱신 후 GREEN 유지.
- **픽스처** `SoloMatchMemberEntityFixture`: `accepted` 파라미터 제거, `status` 기본값 WAITING. MATCHED 매치를 만드는 테스트 셋업은 `status = ACTIVE`를 명시.

## 9. 영향 파일 (참고, 계획 단계 확정)

- common: `MatchMemberStatus`(값 추가).
- core domain: `MatchMember`, `MatchMembers`, `Match`.
- infra: `SoloMatchMemberEntity`, `MatchMemberMapper`, `GetMatchWithPartnerDaoImpl`(필터·프로젝션), 마이그레이션 SQL.
- 검증 대상(무변경 기대): `SendInterestService`, `LeaveChatRoomService`, `DeactivateMatchMemberService`, `GetMatchRecordDaoImpl`, `MatchResponse`/`MatchWithPartner`.
- 테스트/픽스처: 위 §8.
