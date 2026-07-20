# 회원 탈퇴(Account Withdrawal) 설계 — 10일 유예·복구·파기

작성일: 2026-07-01 · 도메인: `user` (+ `auth` 로그인 흐름, `scheduler` 배치)

## 1. 목적 / 핵심 모델

로그인 사용자가 자신의 계정을 탈퇴할 수 있게 한다. 단순 즉시 파기가 아니라 **10일 유예(grace) + 계정 복구**를 지원한다.

1. **탈퇴(즉시)**: 계정을 비활성(소프트삭제)한다. 프로필·코인 등 데이터는 **그대로 보관**한다.
2. **복구(10일 내)**: 같은 소셜(카카오) 계정으로 다시 로그인하면 **기존 계정·데이터가 그대로 복구**된다.
3. **파기(10일 경과)**: 배치가 보관 데이터의 개인정보(PII)를 **익명화(파기)** 한다. 이후에는 복구 불가, 같은 카카오로는 새 계정으로만 가입된다.

법적 관점: 탈퇴 처리(파기)를 10일간 유예하는 "탈퇴 철회 가능 기간" 패턴. 유예 동안 계정은 비활성(숨김) 상태이며, 10일 경과 시 개인정보를 파기한다. 법령 보존의무 데이터(코인 원장 등)는 파기 단계에서도 비식별 형태로 보존한다.

## 2. 상태 모델

`users.deleted_at`(소프트삭제 시각)을 **유예 시계**로 사용한다.

| 상태 | users 행 | 복구 | PII |
|---|---|---|---|
| 활성 | `deleted_at = null`, `status ∈ {ONBOARDING…ACTIVE}` | - | 보유 |
| 탈퇴-유예중 | `deleted_at = 탈퇴시각`, **status 원본 보존** | 가능(원본 status로 복구) | 보관(보유) |
| 파기됨 | `deleted_at` 유지, `status = WITHDRAWN`, `email = null`, `provider_id = "withdrawn_{id}"` | 불가 | 익명화됨 |

- **탈퇴 시 status를 바꾸지 않는다.** 복구 시 온보딩 중이었는지 ACTIVE였는지를 그대로 되살리기 위함. `UserStatus.WITHDRAWN`은 **파기 시점에만** 부여하는 종단 마커.
- 파기 시 `provider_id`를 `"withdrawn_{userId}"`로 치환 → 원본 provider_id 조회가 더는 매칭되지 않아 (a) 복구 불가, (b) `(provider, provider_id)` 유니크 제약 해제로 같은 카카오 **새 계정** 가입 가능. userId가 유일하므로 결정적(랜덤 불필요).

`UserStatus`에 `WITHDRAWN` 추가. `isRegistered()`/`isMatchable()`는 false 유지.

**10일 경계의 강제 주체 = 파기 배치.** 로그인 복구 경로는 "원본 provider_id로 소프트삭제 행이 발견되면 복구"만 한다(유예 일수 계산 안 함). 배치가 `deleted_at < now-10d`인 행의 `provider_id`를 치환·익명화하면, 그 뒤로는 복구 조회에 더는 잡히지 않아 자동으로 복구 불가가 된다. 유예 기간(10일) 값은 **배치 모듈의 설정(cron + 보존일수 config)** 에만 둔다(scheduler는 core에 의존하지 않음). 효과적 보장: "배치가 파기하기 전(≈10일, 배치 주기 오차 내)까지 복구 가능".

## 3. 흐름 A — 탈퇴 (동기)

**HTTP**: `DELETE /users/v1/account` (인증 필수, `@LoginUser AuthUser`). 신규 `UserAccountController`(`oneulsogae-api`).

**Service**: `WithdrawUserService` (core, `@Transactional`), in-port `WithdrawUserUseCase.withdraw(userId)`.

```
1. user = getUserPort.findById(userId) ?: throw USER_NOT_FOUND   // 재탈퇴 시 소프트삭제로 조회 불가 → 404
2. now = timeGenerator.now()
3. softDeleteUserPort.softDelete(userId, now)   // users.deleted_at = now (status·email·provider_id·user_details·coin 모두 보존)
4. revokeUserTokensPort.revokeAll(userId)       // refresh token 전부 revoke
5. syncMatchUserUseCase.sync(userId, null)      // match_user(매칭 읽기모델) hard delete → 매칭 풀에서 즉시 제거
```

- 컨트롤러는 성공 후 인증 쿠키(access/refresh) 삭제(로그아웃과 동일).
- 탈퇴 직후 잔여 access token(stateless)으로 다른 API 호출 시 `findById`가 소프트삭제로 null → `USER_NOT_FOUND`. (TTL 짧아 허용)

## 4. 흐름 B — 복구 (OAuth 재로그인 시 자동)

별도 엔드포인트 없음. 기존 OAuth 로그인 진입점 `RegisterUserService.registerIfAbsent(provider, providerId, email)`에 복구 분기를 추가한다.

```
1. active = getUserPort.findByProviderAndProviderId(provider, providerId)   // @SQLRestriction: 활성만
   if (active != null) return recordLogin(active)                            // 기존 정상 로그인
2. withdrawnId = getUserPort.findWithdrawnUserId(provider, providerId)
   // 소프트삭제 '포함' 조회(네이티브). 원본 provider_id가 남아있는 유예중 행만 잡힌다(파기 행은 provider_id 치환돼 매칭 안 됨).
   if (withdrawnId != null) {
       now = timeGenerator.now()
       restored = restoreUserPort.restore(withdrawnId, now)   // users.deleted_at = null, last_login_at = now
       publish(UserLoggedIn(restored.id))                     // 기존 로그인 이벤트 → UserEventHandler가 match_user 재적재
       return restored
   }
3. // 미존재 → 기존 신규 가입 로직
```

- 복구 = `deleted_at` 해제 + 로그인 기록 + 로그인 이벤트 재발행(매칭 읽기모델 재적재). status 원본이 보존돼 있으므로 온보딩/ACTIVE가 그대로 이어진다.
- `findWithdrawnUserId`/`restore`는 소프트삭제 행을 다뤄야 하므로 `@SQLRestriction`을 우회하는 **네이티브 쿼리**로 구현한다(엔티티 매퍼 경로는 deleted_at을 다루지 못함).
- **로그인 경로는 유예 일수를 계산하지 않는다.** 원본 provider_id로 소프트삭제 행이 잡히면 곧 복구한다. 10일 경계는 배치가 파기(=provider_id 치환)로 강제한다(2장). 따라서 "신규 가입 분기 진입 시 원본 provider_id 행이 남아 유니크 충돌"하는 상황 자체가 발생하지 않는다(있으면 항상 복구되므로).

## 5. 흐름 C — 파기 배치 (10일 경과)

기존 배치 패턴(`ExpireMatch*`)을 그대로 따른다. 익명화 로직은 core가 소유하고, scheduler는 infra **Bridge 어댑터**를 통해 core UseCase에 위임한다.

- **core**: `PurgeWithdrawnUserUseCase.purge(userId)` + `PurgeWithdrawnUserService`(`@Transactional`).
  ```
  1. anonProviderId = "withdrawn_$userId"
  2. anonymizeUserPort.anonymize(userId, anonProviderId, now)
       // users: email=null, provider_id=anonProviderId, status=WITHDRAWN (deleted_at 유지) — 네이티브
  3. anonymizeUserDetailPort.anonymize(userId, now)
       // user_details: 전 PII 필드 null + deleted_at=now — 네이티브
  ```
  익명화 규칙은 도메인이 소유: `User.purge()`(상태/이메일/providerId 치환), `UserDetail.anonymize()`(id/userId 외 전부 null). 단 영속화는 소프트삭제 행 대상이라 네이티브 out-port로 반영.
- **scheduler**(`oneulsogae-scheduler`): `PurgeWithdrawnUserBatchJob`(진입점, 중복실행 방지) + `PurgeWithdrawnUserBatchService` + out-port `GetPurgableWithdrawnUserPort.findIdsWithdrawnBefore(cutoff)` / `PurgeWithdrawnUserPort.purge(userId)` + `TimeGenerator`.
- **infra**: `GetPurgableWithdrawnUserDaoImpl`(네이티브, `deleted_at < cutoff AND status <> 'WITHDRAWN'`인 id 목록 — 이미 파기된 행 제외=멱등) / `PurgeWithdrawnUserBridgeAdapter`(scheduler `PurgeWithdrawnUserPort` → core `PurgeWithdrawnUserUseCase` 위임) / `anonymize*Port` 네이티브 구현.
- **oneulsogae-api**: `PurgeWithdrawnUserBatchScheduler`(`@Scheduled(cron=…, zone="Asia/Seoul")`, 하루 1회) + cron config 프로퍼티.

보존: `coin_histories`/`coin_balances`는 파기 단계에서도 건드리지 않는다. user_id 링크는 익명화된 user를 가리켜 비식별. (전자상거래법 재화 원장 보존)

## 6. 아키텍처 요약 (헥사고날 경계 준수)

- 탈퇴: `UserAccountController` → `WithdrawUserUseCase`(core). Service는 out-port(`SoftDeleteUserPort`/`RevokeUserTokensPort`/`GetUserPort`) + 타 도메인 in-port(`SyncMatchUserUseCase`) + `TimeGenerator` 주입.
- 복구: `RegisterUserService`(core) — out-port(`GetUserPort.findWithdrawnUserId`, `RestoreUserPort.restore`) 추가. 로그인 흐름 자체는 기존 유지.
- 파기: scheduler(자체 out-port) → infra(bridge) → core(`PurgeWithdrawnUserUseCase`). 기존 `ExpireMatch` 배치와 동형.
- 신규 out-port 구현 위치: users 계열은 `UserRepositoryAdapter`, user_details 계열은 `UserDetailCoreAdapter`, refresh token은 신규 `infra/auth` 어댑터(`refreshTokenRepository.revokeAllByUserId`).
- **`UserEntity.providerId`를 `val → var`로 변경**(파기 시 치환 위해). 소프트삭제 행을 다루는 조회/갱신은 네이티브 쿼리로 `@SQLRestriction` 우회.

## 7. 엣지 / 알려진 한계

- **10일 경계는 배치 주기에 근사한다**: 복구 조회는 유예 일수를 따지지 않고 "소프트삭제 행이 있으면 복구"하므로, 배치가 파기하기 전(예: 배치가 하루 지연되면 10~11일차)에 재로그인하면 복구된다. "재가입 가능"을 더 허용하는 방향이라 요구사항에 어긋나지 않으며, 유니크 충돌 race가 원천적으로 없다. 엄격한 정각 10일 차단이 필요하면 배치 주기를 짧게(예: 매시간) 한다.
- access token stateless: 탈퇴/파기 후 잔여 토큰은 `findById` null로 차단(USER_NOT_FOUND). 복구 시 OAuth가 새 토큰 발급.
- 유예 동안 `user_details` PII는 보관된다(계정은 비활성·숨김). 파기 단계에서 익명화. 진행 중 채팅/매칭 멤버십 행은 이번 범위에서 정리하지 않음(future work).
- 복구는 같은 (provider, providerId) 기준. 파기 후에는 provider_id가 치환돼 복구 대상이 아니다.

## 8. 도메인 로직 (core)

- `UserStatus.WITHDRAWN` 추가(파기 종단 마커, isRegistered/isMatchable=false).
- `User.purge(): User = copy(status = WITHDRAWN, email = null, providerId = "withdrawn_$id")` (파기 서비스용).
- 유예 일수(10일)는 도메인이 아니라 **scheduler 설정**에 둔다(로그인 복구 경로는 일수 계산 안 함).
- `UserDetail.anonymize(): UserDetail` = id/userId만 보존, 나머지 전부 null/빈값.
- 탈퇴 자체는 도메인 익명화 없이 소프트삭제(인프라 포트)로 처리 → 탈퇴용 도메인 메서드는 불필요.

## 9. 테스트 전략

- **도메인 유닛(Kotest)**: `User.purge()`(status/email/providerId 치환), `UserDetail.anonymize()`(전 필드 null). 유예 경계 계산이 도메인에 있으면 함께.
- **E2E(oneulsogae-api)**:
  - 탈퇴: ACTIVE 사용자 `DELETE /users/v1/account` → 200, users `deleted_at` set·status 원본 유지, user_details/coin 보존, refresh_token revoked, match_user 제거, 쿠키 삭제.
  - 온보딩 단계 사용자 탈퇴 → 200.
  - 재탈퇴(잔여 토큰 직접 호출) → 404(`USER-001`).
  - 복구: 탈퇴한 사용자가 같은 provider/providerId로 재로그인 → 계정 복구(deleted_at=null), 데이터·status 유지, match_user 재적재. (로그인 유스케이스 레벨 통합 테스트)
  - 파기 후 동일 카카오 재로그인 → 새 계정 생성(복구 아님).
- **파기 배치**: core `PurgeWithdrawnUserService` 동작(익명화 반영), scheduler→bridge→core 경로. 유예 경과 행만 대상·멱등(이미 WITHDRAWN 제외) 검증.

## 10. 보류(문서화) / 프론트엔드 영향

- 보류: 진행 중 매칭/채팅 멤버십 정리, 카카오 unlink API 호출, 코인 원장의 보존기간(예: 5년) 경과 후 2차 완전 파기.
- 프론트(백엔드 외 안내): `DELETE /users/v1/account` 연동(탈퇴 버튼) — 성공 시 토큰/세션 정리 후 로그인 화면으로. 탈퇴 후 10일 내 같은 소셜로 로그인하면 계정이 복구된다는 안내 문구 권장. 신규 에러코드는 없음(404 USER-001 재사용).
