# 추천 코드 기능 설계

2026-07-24 승인. 유저가 온보딩 시 추천 코드를 입력하면 추천인과 신규 유저 모두 50코인을 받는다.

## 정책 결정 사항

- 추천 코드: 유저별 자동 발급 랜덤 코드 (`A-Z0-9` 8자, SecureRandom).
- 입력 시점: 온보딩 완료 요청(`POST /users/v1/onboarding/complete`)에 선택 필드로 포함.
- 보상: 추천인·신규 유저 각각 50코인. 추천인 보상 횟수 무제한.
- 무효 코드: 조용히 무시하고 온보딩 정상 진행 (지급만 안 함).
- 코드 발급: 조회 시점 lazy 발급 (기존 유저 일괄 백필 없음).

## 1. 데이터 모델

`UserEntity`/`User`(user 도메인)에 컬럼 2개 추가. 신규 테이블 없음.

- `referral_code` VARCHAR(8), nullable, unique index `ux_referral_code`
- `referred_by_user_id` BIGINT, nullable (FK 제약 없이 id만 보관 — 기존 스타일)

지급 이력은 `coin_histories`의 REFERRAL 기록으로 충분.

## 2. 코드 발급 (lazy)

- `GET /users/v1/me/referral-code` → 응답 `{ referralCode }`.
- 코드 없으면 생성·저장 후 반환 (get-or-create, 멱등). 쓰기가 발생하므로 command 유스케이스 `IssueReferralCodeUseCase` / `IssueReferralCodeService`(`@Transactional`)로 구현. query 경로에 두지 않는다.
- 생성 규칙: `A-Z0-9` 8자 랜덤(SecureRandom). unique 충돌 시 재시도(상한 있음).

## 3. 온보딩 연동 (보상 지급)

- `UpdateUserDetailRequest`에 `referralCode: String?` 추가 → `CompleteOnboardingCommand`로 전달.
- `CompleteOnboardingService`에서 막 가입 완료 시점(기존 SIGNUP 지급과 같은 가드)에:
  1. 코드로 추천인 조회 — user 자기 out-port에 `findByReferralCode` 추가.
  2. 추천인 존재 + `ACTIVE` 상태 + 본인 아님 → `referredByUserId` 저장, 양쪽에 `AcquireCoinUseCase.acquire(50, REFERRAL)` 지급.
  3. 코드 없거나 무효 → 조용히 무시, 온보딩 정상 진행.
- `CoinGetType.REFERRAL` 추가, `CoinPolicy.REFERRAL_REWARD_COIN_AMOUNT = 50`.
- 셀프 추천: 신규 유저는 온보딩 전 코드 미발급이라 원천 불가하나 방어적으로 본인 체크 포함.

## 4. 테스트

- 도메인 유닛(Kotest): 코드 생성 형식(8자, 문자셋), 추천인 유효성 판정.
- E2E(`oneulsogae-api`): ① 유효 코드 온보딩 → 양쪽 잔액 +50 ② 무효 코드 → 온보딩 성공·지급 없음 ③ 코드 조회 → 발급, 재호출 시 동일 코드.

## 5. 프론트 영향 (백엔드에서 구현 안 함, 안내만)

- 온보딩 요청에 `referralCode` 선택 필드 추가.
- 코드 조회 API 연동 및 공유 화면.
- 구현 완료 후 상세 안내 예정.

## 검토한 대안

- **별도 referral 도메인 신설**: 횟수 제한·통계 확장에 유리하나 무제한·단순 정책엔 과함. 탈락.
- **코드 = userId 인코딩(저장 없음)**: 코드 추측 가능, `referred_by` 컬럼은 어차피 필요해 절약 없음. 탈락.
