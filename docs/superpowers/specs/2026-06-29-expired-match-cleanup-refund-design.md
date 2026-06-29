# 만료 매칭 soft-delete + 코인 환불 배치 설계

## 배경

매칭(소개/미팅)은 생성 시각으로부터 1일(`Match.EXPIRATION`/`TeamMatch.EXPIRATION`)이 지나면
만료된 소개로 본다. 성사(`MATCHED`)되지 못하고 만료된 매칭 데이터(`solo_matches`,
`solo_match_members`, `team_matches`, `matched_teams`)는 현재 누구도 정리하지 않아 활성 테이블에
계속 쌓인다. 이를 주기적으로 soft-delete 하는 배치가 필요하다.

또한, 만료로 제거되는 매칭 중 **한쪽만 신청(APPLY)** 한 경우 — 즉 상대에게 코인을 써서 관심을
표했지만(`...InitAmount` 지불) 상대가 응답하지 않아 성사되지 못한 경우 — 신청자에게 신청 비용의
**절반을 환불**하고, 환불을 알리는 **팝업**을 띄워준다.

### 현재 코드 상태 (재사용 가능한 기존 부품)

soft-delete·솔로 환불 인프라는 이미 존재하나 **아무 곳에서도 호출되지 않는다**(이 배치를 위해
선반영된 것으로 보인다). 그대로 재사용한다.

- soft-delete: `BaseEntity.deletedAt` + 엔티티의 `@SQLRestriction("deleted_at is null")`,
  도메인 `Match.delete(now)` / `TeamMatch.delete(now)`(헤더 CLOSED + deletedAt, 멤버 DEACTIVE + deletedAt).
- 솔로 환불 산정: `Match.failureRefunds()` — `members.applied()`(신청자)에게 `datingInitAmount / 2`
  환불 목록(`List<MatchRefund>`)을 돌려준다. 0코인은 제외.
- 코인 적립: `AcquireCoinUseCase.acquire(userId, AcquireCoinCommand(amount, CoinGetType.REFUND))`.
  `CoinGetType.REFUND("환불")`는 이미 존재.
- 솔로 환불 팝업: `CreateRefundPopupUseCase.createMatchFailedRefund(userId, refundAmount)` +
  `Popup.matchFailedRefund(...)` + `PopupType.MATCH_FAILED_REFUND("매칭 실패 환불")`(7일 노출).

### 팀 쪽 공백 (신규로 채운다)

- `TeamMatch`에는 `failureRefunds()`가 없다.
- `matched_teams`는 `team_id` + `status`만 기록하고, **APPLY로 코인을 지불한 그 유저가 어디에도
  남지 않는다.** `coin_histories`도 `team_match_id`와 연결되지 않아 사후 식별이 불가능하다.
  → 지불자를 정확히 환불하려면 신청 시점에 신청자 userId를 기록해야 한다.

## 목표

1. 만료된(미성사) 솔로·팀 매칭 데이터를 주기적으로 soft-delete 한다. (매시간)
2. 제거되는 매칭 중 한쪽만 신청(APPLY)한 경우, 신청자에게 신청 비용의 절반을 환불한다.
   - 솔로 신청 16코인(`DATING_INIT`=32의 절반), 팀 신청 20코인(`MEETING_INIT`=40의 절반).
3. 환불 발생 시 환불 안내 팝업을 생성한다(솔로/팀 구분).
4. 매치 1건의 soft-delete + 환불 + 팝업을 **한 트랜잭션**으로 처리해 중복 환불을 방지한다.

## 비목표

- 성사(`MATCHED`) 매칭 정리: 만료 시각이 +100년이라 본 배치 조회에서 자연 제외된다(특별 처리 없음).
- 이미 종료(`CLOSED`)·soft-delete된 매칭: 이미 제거됨 / `@SQLRestriction`으로 제외.
- hard delete(물리 삭제): 본 작업 범위 밖. soft-delete만 수행한다.
  (member_key 유니크로 재소개를 막는 기존 규약을 유지하려면 행이 물리적으로 남아 있어야 한다.)
- 프론트엔드 팝업 UI 구현(본 저장소 범위 밖).
- 과거에 이미 만료됐으나 지불자 미기록(컬럼 추가 이전)인 팀 매칭의 소급 환불.

## 변경 사항

### 1) 팝업 타입 (meeple-common)

- `PopupType`에 `MEETING_FAILED_REFUND("미팅 매칭 실패 환불")` 추가. (솔로 `MATCH_FAILED_REFUND`와 구분)

### 2) 팀 지불자 추적 — `applicant_user_id` (meeple-core 도메인 + meeple-infra)

신청 시점에 코인을 지불한 유저를 `matched_teams`에 기록해, 만료 환불 대상을 정확히 식별한다.

- **DB 마이그레이션**: `docs/migration/`에 `ALTER TABLE matched_teams ADD COLUMN applicant_user_id BIGINT NULL;`
- **엔티티**: `MatchedTeamEntity`에 `@Column(name = "applicant_user_id") val applicantUserId: Long? = null`.
  `PrivateMatchedTeamMapper`(또는 해당 mapper)의 `toEntity()`/`toDomain()`에 매핑 추가.
- **도메인**:
  - `MatchedTeam`에 `val applicantUserId: Long? = null` 추가.
    `apply(applicantUserId: Long): MatchedTeam` — `status = APPLY` 전이 시 `applicantUserId`도 기록.
    (기존 무인자 `apply()`는 시그니처 변경)
  - `MatchedTeams.apply(teamId: Long, applicantUserId: Long): MatchedTeams` — 해당 팀에 신청자 전파.
    `applied(): List<MatchedTeam>` 신규(솔로 `MatchMembers.applied()` 대응).
  - `TeamMatch.respond(teamId: Long, applicantUserId: Long): TeamMatch` — `matchedTeams.apply(teamId, applicantUserId)` 호출.
  - `TeamMatch.failureRefunds(): List<MatchRefund>` 신규 — 솔로 `Match.failureRefunds()` 미러:

    ```kotlin
    fun failureRefunds(): List<MatchRefund> =
        matchedTeams.applied()
            .mapNotNull { team: MatchedTeam ->
                team.applicantUserId?.let { userId: Long -> MatchRefund(userId = userId, amount = dateInitAmount / 2) }
            }
            .filter { refund: MatchRefund -> refund.amount > 0 }
    ```
- **호출부 변경**: `SendTeamInterestService`에서 `teamMatch.respond(actorTeam.id)` →
  `teamMatch.respond(actorTeam.id, userId)`. (지불자 = API를 호출한 `userId`, 기존 `spend(userId, ...)`와 동일)
- `MatchRefund`(core match 도메인)는 솔로/팀 공용으로 그대로 재사용한다.

> 참고: `activateAll()`(성사 시 ACTIVE 승격)은 `applicantUserId`를 보존한다(copy가 건드리지 않음).
> 성사 매칭은 환불 대상이 아니므로 영향 없음.

### 3) 매치별 만료 처리 (meeple-core, match/command)

도메인 교차 조율(매치 soft-delete + 코인 환불 + 팝업 생성)을 core 응용 서비스가 담당한다.
**매치 1건 = 트랜잭션 1개**로, soft-delete와 환불이 같은 트랜잭션에서 커밋돼야 다음 실행에서 재환불되지 않는다.

- **in-port** `ExpireMatchUseCase`:

  ```kotlin
  interface ExpireMatchUseCase {
      fun expireSoloMatch(matchId: Long)
      fun expireTeamMatch(teamMatchId: Long)
  }
  ```

- **서비스** `ExpireMatchService`(@Service, 각 메서드 `@Transactional`). 주입:
  `GetMatchPort`, `SaveMatchPort`, `GetTeamMatchPort`, `SaveTeamMatchPort`,
  `AcquireCoinUseCase`, `CreateRefundPopupUseCase`, `TimeGenerator`.

  ```kotlin
  @Transactional
  override fun expireSoloMatch(matchId: Long) {
      val match: Match = getMatchPort.findById(matchId) ?: return
      val now: LocalDateTime = timeGenerator.now()
      val refunds: List<MatchRefund> = match.failureRefunds()   // 삭제 전 산정
      saveMatchPort.save(match.delete(now))                     // 헤더+멤버 soft-delete
      refunds.forEach { refund: MatchRefund ->
          acquireCoinUseCase.acquire(refund.userId, AcquireCoinCommand(refund.amount, CoinGetType.REFUND))
          createRefundPopupUseCase.createMatchFailedRefund(refund.userId, refund.amount)
      }
  }
  ```

  `expireTeamMatch`는 동일 구조: `getTeamMatchPort.findById` → `teamMatch.failureRefunds()` →
  `saveTeamMatchPort.save(teamMatch.delete(now))` → 환불 + `createMeetingFailedRefund`.

- `findById`가 null(이미 제거됨)이면 조용히 반환(동시 처리·재실행 안전).
- `AcquireCoinUseCase`/`CreateRefundPopupUseCase`는 다른 도메인 in-port를 주입하는 기존 규약을 따른다.
  둘 다 `@Transactional`이므로 매치별 트랜잭션에 합류해 원자적으로 커밋된다.

### 4) 환불 팝업 (meeple-core, popup/command)

- `Popup.meetingFailedRefund(userId, refundAmount, now)` 팩토리 추가(`matchFailedRefund` 미러,
  문구만 미팅 기준, `PopupType.MEETING_FAILED_REFUND`, 7일 노출).
- `CreateRefundPopupUseCase`에 `fun createMeetingFailedRefund(userId: Long, refundAmount: Int)` 추가.
- `CreateRefundPopupService`에 구현 추가(`savePopupPort.save(Popup.meetingFailedRefund(...))`).

### 5) 배치 트리거 (meeple-scheduler + meeple-infra + meeple-api)

기존 매칭 배치 컨벤션(API @Scheduled → scheduler BatchJob/Service → out-port → infra 어댑터)을 따른다.

- **meeple-scheduler**
  - `domain/ExpireMatchBatchResult` — 집계 결과(soloDeleted, teamDeleted, soloFailed, teamFailed 등).
  - `port/in/RunExpireMatchBatchUseCase { fun run(): ExpireMatchBatchResult }`.
  - `port/out/GetExpiredMatchPort { fun findExpiredSoloMatchIds(now): List<Long>; fun findExpiredTeamMatchIds(now): List<Long> }`.
  - `port/out/ExpireMatchPort { fun expireSoloMatch(id: Long); fun expireTeamMatch(id: Long) }`.
  - `application/ExpireMatchBatchService`(implements RunExpireMatchBatchUseCase): `timeGenerator.now()`로
    만료 id 조회 → id마다 `expireMatchPort.expireSoloMatch/expireTeamMatch` 호출, **건별 try/catch로 격리**,
    성공/실패 집계해 결과 반환. (기존 `SoloMatchBatchService` 루프·격리 패턴과 동일)
  - `adapter/ExpireMatchBatchJob`(@Component, `AtomicBoolean` 가드) → `RunExpireMatchBatchUseCase.run()`.
- **meeple-infra**
  - `GetExpiredMatchPort` 구현(QueryDSL): `deleted_at IS NULL AND expires_at < :now AND status IN (PROPOSED, PARTIALLY_ACCEPTED)`인
    `solo_matches`/`team_matches`의 id를 반환.
  - `ExpireMatchPort` 구현: core `ExpireMatchUseCase`에 위임하는 얇은 브리지(트랜잭션 경계는 core 서비스).
- **meeple-api**
  - `scheduler/match/ExpireMatchBatchScheduler`(@Component, `@Scheduled(cron=..., zone="Asia/Seoul")`) → `ExpireMatchBatchJob.run()`.
  - `application.yml`: `meeple.match.expire-batch.cron` 추가 — 운영 매시간(`0 0 * * * *`), `local` 프로파일은 매분.

### 6) 인덱스

배치 조회 `WHERE deleted_at IS NULL AND expires_at < :now AND status IN (...)`를 인덱스 seek로 받친다.

- `solo_matches`·`team_matches`에 현재 `expires_at` 인덱스가 없다. 만료 행은 소수일 가능성이 높으므로
  선택적 `status`(동등) → `expires_at`(범위) 순서의 복합 인덱스 `(status, expires_at)` 추가를 검토한다.
  마이그레이션 SQL에 DDL 포함, 쓰기 비용 대비 효용을 함께 판단한다.

### 7) 프론트엔드 영향 (본 저장소에서 직접 수정하지 않음)

- 팝업 응답에 신규 `popupType = "MEETING_FAILED_REFUND"`가 내려갈 수 있다. 프론트가 popupType별 분기·문구·
  스타일을 가진다면 새 값 처리가 필요하다. 작업 완료 후 대응 위치(팝업 타입 enum/매퍼)와 내용을 안내한다.

## 테스트 전략

- **도메인 유닛(Kotest, meeple-core)**
  - `TeamMatch.failureRefunds()`: APPLY 팀의 `applicantUserId`에게 `dateInitAmount/2` 환불, 미신청/`applicantUserId` null 제외.
  - `MatchedTeam.apply(applicantUserId)` / `MatchedTeams.apply(teamId, applicantUserId)` / `MatchedTeams.applied()`.
  - (회귀 확인) `Match.failureRefunds()`.
- **E2E(meeple-api, AbstractIntegrationSupport)**
  - 만료된 PARTIALLY_ACCEPTED 솔로 매칭 → 배치 실행 후: 헤더·멤버 soft-delete, 신청자 잔액 +16,
    `MATCH_FAILED_REFUND` 팝업 노출.
  - 만료된 PARTIALLY_ACCEPTED 팀 매칭 → 배치 실행 후: soft-delete, 지불자 잔액 +20, `MEETING_FAILED_REFUND` 팝업.
  - 만료된 PROPOSED(미신청) 매칭 → soft-delete만, 환불·팝업 없음.
  - 성사(MATCHED) 매칭 → 배치 후 무변경(soft-delete·환불 없음).
  - 배치 잡은 테스트에서 `ExpireMatchBatchJob` 빈을 직접 호출해 트리거(기존 배치 E2E와 동일 방식).

## 검증 기준 (성공 정의)

1. 만료 PARTIALLY_ACCEPTED 솔로/팀이 soft-delete되고 신청자만 절반 환불 + 해당 팝업 생성.
2. 만료 PROPOSED는 soft-delete만, MATCHED는 무변경 — E2E로 확인.
3. 같은 배치를 연속 2회 실행해도(또는 매시간 재실행) 중복 환불이 없다(soft-delete 같은 트랜잭션 커밋으로 재조회 제외).
4. 팀 환불이 `applicant_user_id`로 식별된 실제 지불자에게 간다.
5. 도메인 유닛 + E2E 전부 통과.
