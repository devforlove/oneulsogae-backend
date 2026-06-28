# 가입 축하 100코인 지급 + 팝업 신호 설계

## 배경

신규 사용자가 정식 가입(온보딩 완료)하는 시점에 **가입 축하 100코인**을 자동 지급하고,
프론트엔드가 "100코인을 받았어요" **축하 팝업**을 띄울 수 있도록 백엔드가 신호를 내려준다.

온보딩 완료는 회사 이메일 인증 확인(`POST /users/v1/onboarding/company-email/verifications/confirm`) 시점에
`VerifyCompanyEmailService`가 `justOnboarded` 여부를 판정하고, 막 완료된 경우에만
`CompanyEmailVerified` 이벤트를 발행한다. `UserEventHandler.onCompanyEmailVerified`가 이를
AFTER_COMMIT / REQUIRES_NEW로 받아 첫 매칭·팀 추천을 수행한다. 이 리스너가 **사용자당 정확히 1회**
실행되므로 코인 지급 위치로 적합하다.

회사명 직접입력(`resolveCompanyName`) 경로는 **이미 이메일 인증을 마친(= 이미 코인을 받은)** 사용자의
회사명 보완 경로이며 `CompanyEmailVerified`를 발행하지 않으므로 코인이 중복 지급되지 않는다.

## 목표

1. 온보딩 완료(최초 회사 이메일 인증) 시 100코인을 1회 지급한다.
2. 인증 확인 응답에 팝업 트리거용 신호(`justOnboarded`, `rewardCoin`)를 내려준다.
3. 프론트엔드는 이 신호로 축하 팝업을 띄운다(프론트 작업, 본 백엔드 변경에 포함하지 않음).

## 비목표

- 추가적인 중복 지급 방어 가드(이력 조회 등)는 두지 않는다 — 리스너 1회 보장에 의존한다.
- 코인 지급 실패 시 재시도/보상 로직은 두지 않는다 — 기존 추천과 동일하게 AFTER_COMMIT best-effort.
- 프론트엔드 팝업 UI 구현(본 저장소 범위 밖).

## 변경 사항

### 1) 코인 적립 유형·정책 (meeple-common)

- `CoinGetType`에 `SIGNUP("가입 축하")` 추가. (출석 `DAILY`·구매 `PURCHASE`·환불 `REFUND`과 구분)
- `CoinPolicy`에 `SIGNUP_REWARD_COIN_AMOUNT: Int = 100` 상수 추가.

### 2) 코인 지급 (meeple-core, UserEventHandler)

- `UserEventHandler`에 `AcquireCoinUseCase`(coin 도메인 in-port) 주입.
- `onCompanyEmailVerified`에서 코인을 **추천보다 먼저** 지급:

  ```kotlin
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun onCompanyEmailVerified(event: CompanyEmailVerified) {
      acquireCoinUseCase.acquire(
          event.userId,
          AcquireCoinCommand(CoinPolicy.SIGNUP_REWARD_COIN_AMOUNT, CoinGetType.SIGNUP),
      )
      recommendMatchUseCase.recommend(event.userId)
      recommendTeamUseCase.recommend(event.userId)
  }
  ```

- 원장(coins) + 잔액(coin_balances)의 한 트랜잭션 정합성은 기존 `AcquireCoinService`가 보장한다.
- 도메인 간 참조 규칙 준수: 다른 도메인(coin)의 동작은 in-port `AcquireCoinUseCase`로만 호출한다.

### 3) 팝업 신호 (meeple-core 결과 + meeple-api 응답)

- `VerifyCompanyEmailResult`에 필드 추가:
  - `justOnboarded: Boolean` — 이번 호출로 온보딩이 막 완료됐는지(서비스가 이미 계산하는 값).
  - `rewardCoin: Int` — `if (justOnboarded) CoinPolicy.SIGNUP_REWARD_COIN_AMOUNT else 0`.
- `VerifyCompanyEmailService.verify`가 `justOnboarded`를 결과에 담아 반환.
- `VerifyCompanyEmailResponse`에 동일한 두 필드(`justOnboarded`, `rewardCoin`) 노출.

### 4) 프론트엔드 안내 (meeple-frontend — 직접 수정하지 않음)

- 회사 이메일 인증 확인 응답(`VerifyCompanyEmailResponse`) 대응 DTO에 `justOnboarded`, `rewardCoin` 필드 추가.
- 인증 성공 콜백에서 `justOnboarded == true && rewardCoin > 0`이면 가입 축하 팝업("축하합니다! N코인을 받았어요") 노출.

## 데이터 흐름

```
confirm 인증번호
  └─ VerifyCompanyEmailService.verify (@Transactional)
       ├─ 인증·회사명 확정·상태 전환, justOnboarded 판정
       ├─ publish UserProfileChanged (BEFORE_COMMIT: match_user 동기화)
       ├─ if justOnboarded → publish CompanyEmailVerified
       └─ return VerifyCompanyEmailResult(companyName, justOnboarded, rewardCoin)
  ── 트랜잭션 commit ──
  └─ UserEventHandler.onCompanyEmailVerified (AFTER_COMMIT, REQUIRES_NEW)
       ├─ acquireCoinUseCase.acquire(userId, SIGNUP 100)   ← 코인 지급
       ├─ recommendMatchUseCase.recommend(userId)
       └─ recommendTeamUseCase.recommend(userId)
  └─ 응답: VerifyCompanyEmailResponse(isCompanyResolved, companyName, justOnboarded, rewardCoin)
```

## 테스트

- **E2E (meeple-api)**: 회사 이메일 인증 확인 성공 시
  - 응답 `justOnboarded = true`, `rewardCoin = 100` 검증.
  - 인증 후 코인 잔액이 100인지 검증(기존 잔액 조회 유틸/픽스처 활용).
  - (있으면) 이미 가입된 사용자 재인증 시 `justOnboarded = false`, `rewardCoin = 0` 확인.
- 기존 인증 확인 E2E가 있으면 새 응답 필드에 맞춰 갱신.

## 리스크

- AFTER_COMMIT 리스너에서 코인 지급이 예외로 실패하면 Spring이 예외를 삼켜 가입은 유지되나
  코인은 미지급될 수 있다(best-effort). 응답의 `rewardCoin`은 지급 시도값을 의미하며, 실제 지급은
  추천 적재와 동일한 best-effort 보장 수준이다. 현 단계 요구사항(리스너 1회 보장 의존) 내 허용 범위.
