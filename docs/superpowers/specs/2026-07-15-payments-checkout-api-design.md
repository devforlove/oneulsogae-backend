# payments 도메인 체크아웃 조회 API 설계

- 날짜: 2026-07-15
- 상태: 승인됨

## 배경

프론트엔드(`meeple-frontend`)의 오프라인 모임 참가비 결제 화면(`/offline/[id]/payment`)은 진입 시 다음 데이터를 표시한다.

| 데이터 | 현재 상태 |
|---|---|
| 모임 제목·이미지·지역·일정·금액 | offline 도메인 API(`useGathering`)로 실조회 중 |
| 주문자 정보(이름·이메일·휴대폰) | 프론트 하드코딩(`MOCK_ORDERER`) |
| 보유 쿠폰 목록 | 프론트 하드코딩 |
| 무통장 입금 계좌 안내 | 프론트 하드코딩(결제 완료 화면 영역) |

이 중 **주문자 정보**를 백엔드에서 조회하는 payments 도메인 API를 신설한다.

## 범위 결정

- **체크아웃 화면 전용 집계 API 1개**를 payments 도메인에 신설한다. 모임·일정·금액은 기존 offline API를 계속 사용한다(중복 제공하지 않음).
- **쿠폰은 이번 범위에서 제외**한다(백엔드에 쿠폰 도메인이 없어 별도 설계 필요). 응답에 빈 필드도 예약하지 않는다.
- 무통장 입금 계좌 안내는 "결제 제출" 영역의 데이터이므로 이번 조회 API 범위 밖이다.

## 데이터 소스 (조사로 확정)

- **이름**: `identity_verifications.real_name` — 본인인증(KCP) 확정 시에만 저장된다. `UserDetail`에는 실명이 없다(닉네임만 존재).
- **휴대폰**: `user_details.phone_number` — 본인인증 확정 시 `ConfirmIdentityVerificationService.reflectToUserDetail`이 신뢰값으로 반영한다.
- **이메일**: `users.email` — OAuth 로그인 이메일.

## API 스펙

**`GET /payments/v1/checkout`** — 인증 필수(`@LoginUser user: AuthUser`), 파라미터 없음.

```json
{
  "success": true,
  "data": {
    "orderer": {
      "name": "김미플",
      "email": "meeple@example.com",
      "phoneNumber": "01012345678"
    }
  },
  "error": null
}
```

- `name`: 해당 유저의 `identity_verifications` 중 **최신 VERIFIED 행**의 `real_name`. 본인인증 전 유저는 `null`.
- `email`·`phoneNumber`: 각각 `users.email`, `user_details.phone_number`. 미설정 시 `null`.
- **null 정책**: 세 필드 모두 nullable로 내려주고 에러를 던지지 않는다(주문자 정보 미비가 화면 진입을 막을 이유가 없음). 표시는 프론트가 결정한다.
- 응답을 `orderer` 객체로 한 겹 감싸 추후 체크아웃 데이터(쿠폰 등) 확장 여지를 남긴다.

## 백엔드 구조

coin 도메인의 query 패턴을 그대로 따른다. payments는 당분간 조회만 있으므로 **`query` 패키지만** 둔다(`matchuser`가 command만 두는 것과 대칭).

```
meeple-core/…/core/payments/query/
  service/GetCheckoutService.kt           @Service @Transactional(readOnly = true)
  service/port/in/GetCheckoutUseCase.kt   fun getCheckout(userId: Long): CheckoutView
  dao/GetCheckoutOrdererDao.kt            fun findOrdererByUserId(userId: Long): OrdererView?
  dto/CheckoutView.kt                     data class CheckoutView(val orderer: OrdererView)
  dto/OrdererView.kt                      name/email/phoneNumber 모두 String?

meeple-infra/…/infra/payments/query/
  GetCheckoutOrdererDaoImpl.kt            QueryDSL 2쿼리(JPAQueryFactory만 주입)

meeple-api/…/api/payments/
  PaymentsController.kt                   @RequestMapping("/payments/v1"), GET /checkout
  response/CheckoutResponse.kt            companion object of(CheckoutView)
  response/OrdererResponse.kt             companion object of(OrdererView)
```

- **DAO 쿼리**: 2개의 단순 쿼리로 나눈다. ① `users` ⟕ `user_details`(user_id, 명시 조인 `join … on`) → 이메일·휴대폰, ② `identity_verifications`(user_id 동등 + status = VERIFIED, `id desc` 첫 행) → 실명. (최신 1건 상관 서브쿼리를 ON절에 넣는 단일 쿼리는 JPQL/Hibernate 지원이 불확실해 배제 — 두 쿼리 모두 기존 인덱스로 seek된다)
- **도메인 간 참조**: CQRS 규칙상 payments query는 user 도메인의 포트·도메인을 참조하지 않고, 자기 DaoImpl이 infra에서 user 계열 엔티티를 직접 조인해 자기 read model로 투영한다(기존 "표시용 프로필 조인" 패턴과 동일).
- **인덱스**: 조회 경로는 기존 `findLatestByUserId`와 동일한 `identity_verifications.user_id` 동등 조건이다. 구현 시 실제 인덱스 존재를 확인하고, 없으면 추가를 검토한다.
- 에러코드·도메인 모델은 만들지 않는다(검증·상태 변경이 없는 순수 조회 — YAGNI). DAO가 null을 반환하면(이론상 인증 유저는 users 행이 항상 존재) 모든 필드가 null인 `OrdererView`로 대체한다.

## 테스트

- **E2E**(`meeple-api`, `AbstractIntegrationSupport` + Testcontainers + 엔티티 픽스처 + `RestAssuredDsl`):
  1. 본인인증 완료 유저(VERIFIED `identity_verifications` 행 + `user_details.phone_number` + `users.email` 픽스처) → 세 필드 정상 반환.
  2. 프로필·본인인증 없는 유저 → `orderer` 각 필드 `null` 반환, 200.
  3. 비인증 요청 → 401.
- 도메인 유닛 테스트는 없음(도메인 로직이 없는 read model).

## 프론트엔드 변경 안내 (meeple-frontend는 이 작업에서 수정하지 않음)

- `src/domains/payment/presentation/paymentMockOrderer.ts`의 `MOCK_ORDERER` 제거.
- `GET /payments/v1/checkout`을 호출하는 remote datasource + DTO(`{ orderer: { name, email, phoneNumber } }`) 추가, `PaymentScreen`이 조회 결과를 `PaymentOrdererCard`에 주입.
- `name`/`email`/`phoneNumber`가 `null`일 수 있으므로 표시 정책(빈 값 표기 또는 입력 유도) 필요.
- `phoneNumber`는 저장값 그대로(하이픈 없는 `01012345678`) 내려간다 — 표시 포맷팅(하이픈 삽입)은 프론트 책임.
- 쿠폰 카드(`PaymentCouponCard`)·무통장 입금 계좌는 이번 범위 밖이라 목업 유지.
