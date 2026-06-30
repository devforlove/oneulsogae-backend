# 회원 탈퇴(Account Withdrawal) 설계

작성일: 2026-07-01 · 도메인: `user`

## 1. 목적 / 범위

로그인한 사용자가 자신의 계정을 탈퇴할 수 있는 기능을 추가한다. 한국 개인정보보호법의 **"탈퇴 시 개인정보 지체 없이 파기"** 원칙을 따르되, 법령상 보존의무가 있는 데이터는 비식별 형태로 보존한다.

**이번 범위(동기 처리)**: 탈퇴 요청 시 즉시 실행되는 부분만 구현한다.
**보류(문서화만, future work)**:
- 보존기간 경과 후 보존 데이터(코인 원장 등) 완전 파기 배치(`meeple-scheduler`)
- 카카오 등 소셜 연결 해제(unlink) API 호출
- 진행 중인 매칭/채팅방 정리

## 2. 법적 배경 / 데이터 처리 매핑

원칙: 개인정보보호법상 탈퇴 시 개인정보는 **지체 없이 파기**. 단, 법령상 보존의무 데이터(전자상거래법: 대금결제·재화공급 5년, 계약·청약철회 5년, 소비자 불만·분쟁처리 3년 / 통신비밀보호법: 접속기록 3개월)는 해당 기간 보존.

데이터 처리 전략: **소프트삭제 + 개인정보 즉시 익명화**.

| 데이터(테이블) | 탈퇴 시 처리 | 근거 |
|---|---|---|
| `users` (식별정보) | `status=WITHDRAWN`, `deleted_at=now`, `email→null`, `provider_id→"withdrawn_{userId}"` | PII 파기 + 유니크 제약 해제(재가입) |
| `user_details` (프로필 PII) | 닉네임·전화·생일·키·성별·회사/학교 이메일·회사/학교명·소개·직업·지역·특성·관심사·프로필이미지·결혼/흡연/종교/음주/체형 등 **전 PII 필드 null** + `deleted_at=now` | 개인정보 지체 없이 파기 |
| `refresh_tokens` | 해당 user의 토큰 전부 `revoke` | 세션 무효화 |
| `match_user` (매칭 읽기모델) | **hard delete** (`SyncMatchUserUseCase.sync(userId, null)`) | 매칭 풀에서 즉시 제거 |
| `coin_histories` / `coin_balances` | **그대로 보존**. user_id 링크는 익명화된 user를 가리키므로 비식별 상태 | 전자상거래법(재화 원장) 보존 |
| alarm·chat_room_member·report·*_email_verification·notification_preference·popup·inquiry 등 | 이번 범위에서 **변경 없음** (user_id 참조만 잔존, 직접 PII 아님) | 최소 변경 원칙 |

### 재가입 정책
탈퇴 후 같은 소셜(카카오) 계정으로 **즉시 재가입 허용**. `(provider, provider_id)` 유니크 제약은 소프트삭제된 행에도 남으므로, 탈퇴 시 `provider_id`를 `"withdrawn_{userId}"`로 치환해 제약을 해제한다. userId가 유일하므로 결정적(랜덤 불필요)이며 테스트가 용이하다.

## 3. 아키텍처 (헥사고날)

### 핵심 기술 제약(왜 전용 포트인가)
- 기존 `User.toEntity()`/`UserDetail.toEntity()` 매퍼는 새 엔티티를 만들어 `id`만 복사하고 `deleted_at`을 보존하지 못한다. `BaseEntity.deletedAt`은 `protected set` + `softDelete()`로만 설정 가능. 따라서 기존 `SaveUserPort`/`SaveUserDetailPort` 경로로는 소프트삭제가 불가능하다.
- `UserEntity.providerId`가 `val`이라 익명화 불가.

→ 결론: **탈퇴 전용 out-port**를 두고, infra 어댑터가 **관리(managed) 엔티티를 로드 → 필드 변경 → `softDelete(at)`** 한다. `UserEntity.providerId`를 `val → var`로 1줄 변경한다. (대안인 "도메인에 deletedAt 추가 + 매퍼 확장"은 모든 저장 경로에 영향을 주어 과함, "raw @Modifying UPDATE"는 도메인 우회로 컨벤션 이탈 → 채택하지 않음)

### 구성 요소

- **in-port**: `WithdrawUserUseCase.withdraw(userId: Long)`
- **Service**: `WithdrawUserService` (core, `@Transactional`) — 오케스트레이션
- **out-ports (core 신규)**:
  - `WithdrawUserPort.withdraw(user: User, at: LocalDateTime)` — users 행 익명화 + 소프트삭제. 구현: `UserRepositoryAdapter`
  - `AnonymizeUserDetailPort.anonymize(userId: Long, at: LocalDateTime)` — user_details PII null + 소프트삭제. 구현: `UserDetailCoreAdapter`
  - `RevokeUserTokensPort.revokeAll(userId: Long)` — refresh token 전부 폐기. 구현: 신규 `infra/auth` 어댑터 → `refreshTokenRepository.revokeAllByUserId(userId)`
- **재사용 in-port**: `SyncMatchUserUseCase.sync(userId, null)` (match_user 하드삭제. user 도메인이 이미 의존 중)
- **HTTP 경계**: `UserAccountController` (`meeple-api`)

### 의존 방향
`UserAccountController` → `WithdrawUserUseCase`(core in-port). Service는 out-port(`WithdrawUserPort`/`AnonymizeUserDetailPort`/`RevokeUserTokensPort`/`GetUserPort`) + 다른 도메인 in-port(`SyncMatchUserUseCase`) + `TimeGenerator`를 주입. 기존 헥사고날 경계를 준수한다.

## 4. 도메인 로직

- `UserStatus`에 `WITHDRAWN` 추가(소프트삭제된 행의 감사 마커). `isRegistered()`/`isMatchable()`는 false 유지(자동으로 매칭·정식기능에서 제외).
- `User.withdraw(): User` = `copy(status = WITHDRAWN, email = null, providerId = "withdrawn_$id")`
- `UserDetail.anonymize(): UserDetail` = `id`/`userId`만 보존, 나머지 모든 필드 null/빈 리스트
- 탈퇴 가능 상태: **모든 상태(온보딩 포함)**. `user_details`가 아직 없으면(온보딩 초기) 익명화 단계는 graceful skip.
- **재탈퇴는 별도 검증이 불필요하다**: 탈퇴 시 `users.deleted_at`이 설정되어 `@SQLRestriction`이 해당 행을 조회에서 제외하므로, 재탈퇴 시도는 `getUserPort.findById`가 null을 반환해 `USER_NOT_FOUND`로 자연히 차단된다. (이미 탈퇴 상태를 조회로 다시 볼 일이 없으므로 `WITHDRAWN` 검증/전용 에러코드는 두지 않는다)

## 5. 서비스 흐름 (`WithdrawUserService.withdraw`)

```
@Transactional
1. user = getUserPort.findById(userId) ?: throw USER_NOT_FOUND  // 재탈퇴 시 여기서 차단
2. now = timeGenerator.now()
3. withdrawUserPort.withdraw(user.withdraw(), now)        // users 익명화 + 소프트삭제
4. anonymizeUserDetailPort.anonymize(userId, now)         // user_details PII null + 소프트삭제 (없으면 skip)
5. revokeUserTokensPort.revokeAll(userId)                 // refresh token 폐기
6. syncMatchUserUseCase.sync(userId, null)                // match_user 하드삭제
```

컨트롤러는 성공 후 **인증 쿠키(access/refresh) 삭제**를 로그아웃과 동일하게 수행한다.

## 6. HTTP 경계

- `DELETE /users/v1/account` (인증 필수, `@LoginUser AuthUser`)
- 성공: `ApiResponse.success()` (200) + 인증 쿠키 clear
- 에러: `USER_NOT_FOUND` (USER-001, 404) — 존재하지 않거나 이미 탈퇴한 계정. (신규 에러코드 없음)

`SecurityConfig`상 `/users/v1/**`는 이미 인증 필수이므로 별도 설정 변경 없음.

## 7. 엣지 / 알려진 한계

- access token은 stateless(JWT)라 즉시 폐기 불가. 탈퇴 후 잔여 access token으로 다른 인증 API 호출 시 `GetUserPort.findById`가 소프트삭제로 null을 반환 → `USER_NOT_FOUND`로 차단됨. access token TTL이 짧아 허용 가능.
- 진행 중인 채팅방·매칭 멤버십 행은 이번 범위에서 정리하지 않는다(참조만 잔존). future work.
- 코인 잔액/이력은 보존하므로, 즉시 재가입해도 과거 코인과는 분리된 새 계정으로 시작한다(같은 userId가 아님).

## 8. 테스트 전략

- **도메인 유닛(Kotest, `meeple-api/src/test/.../domain/user`)**:
  - `User.withdraw()` — status=WITHDRAWN, email=null, providerId="withdrawn_{id}"
  - `UserDetail.anonymize()` — id/userId 외 전 필드 null/빈값
- **E2E(`meeple-api`, AbstractIntegrationSupport)**:
  - ACTIVE 사용자 탈퇴 → 200, 검증: users(`deleted_at` set·`status=WITHDRAWN`·`provider_id="withdrawn_{id}"`·`email=null`), user_details(PII null·`deleted_at` set), refresh_token revoked, match_user 제거, 인증 쿠키 삭제
  - 온보딩 단계(user_details 없음) 사용자 탈퇴 → 200 (익명화 skip, 예외 없음)
  - 이미 탈퇴한 사용자 재탈퇴 시도(잔여 access token으로 직접 호출) → 404 (`USER-001`, 소프트삭제로 조회 불가)

## 9. 프론트엔드 영향(백엔드 외 안내)

- 신규 엔드포인트 `DELETE /users/v1/account` 연동(탈퇴 버튼). 성공 시 클라이언트 토큰/세션 정리 후 로그인 화면으로.
