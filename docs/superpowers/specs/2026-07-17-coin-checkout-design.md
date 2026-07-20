# 코인 checkout API 설계

## 배경 / 목표

코인 상점에서 유저가 특정 코인 상품을 구매하기 직전, **결제(체크아웃) 화면에 필요한 데이터**를 내려주는 조회 API를 코인 도메인에 추가한다. 응답에는 **구매하려는 코인 아이템**과 **구매방법(payment_methods)**이 포함된다.

기존 gathering 결제의 체크아웃(`GET /payments/v1/checkout` — `PaymentsController`가 payments·gathering in-port를 조합)과 대칭 구조로 만든다.

비목표: 실제 구매/결제 처리(결제완료·PG 승인)는 이번 범위 밖이다. 주문자(orderer) 정보도 이번 응답에 포함하지 않는다(요청 범위는 아이템 + 구매방법 두 가지).

## 엔드포인트

`GET /coins/v1/checkout?itemId={id}`

- 인증 필요. `/coins/v1/**`는 SecurityConfig에서 별도 permitAll이 없어 `anyRequest().authenticated()`로 이미 인증된다(코인 상점/잔액과 동일).
- 응답 내용(아이템·구매방법)은 userId를 필요로 하지 않으므로 `@LoginUser`를 주입받지 않는다(인증은 필터가 보장).

## 응답 `CoinCheckoutResponse`

```
CoinCheckoutResponse
├─ item: CoinItemResponse          // 기존 재사용: id, coinAmount, price, salePrice, pricePerCoin, discountRate
└─ paymentMethods: List<PaymentMethodResponse>   // 기존 재사용: code, name (활성만, 노출 순서)
```

- `CoinItemResponse`(api/coin/response)와 `PaymentMethodResponse`(api/payments/response)를 그대로 재사용한다. 둘 다 같은 api 모듈이라 import 가능.

## 코인 아이템 단건 조회 (coin 도메인)

- `GetCoinItemDao`에 `findById(itemId: Long): CoinItem?` 추가 (QueryDSL 구현 `GetCoinItemDaoImpl`).
- 신규 in-port `GetCoinCheckoutUseCase.getCheckout(itemId: Long): CoinItem` (`coin/query/service/port/in`).
- `GetCoinCheckoutService`(`@Transactional(readOnly = true)`)가 구현: dao 조회 후 없으면 `BusinessException(CoinErrorCode.COIN_ITEM_NOT_FOUND)`.
- 신규 에러코드 `CoinErrorCode.COIN_ITEM_NOT_FOUND("COIN-004", "코인 상품을 찾을 수 없습니다.", HttpStatus.NOT_FOUND)`.
  - (기존 코드: COIN-001, COIN-003. COIN-002는 미사용 gap이나 재사용 회피를 위해 다음 번호 COIN-004를 쓴다.)

## 구매방법 조회 (payments 도메인 in-port 신규)

`payment_methods`는 payments 도메인 소유다(엔티티·`GetPaymentMethodDao`가 payments에 있음). CLAUDE.md "다른 도메인 데이터는 그 도메인의 in-port로" 규칙에 따라 payments가 제공한다.

- 신규 in-port `GetPaymentMethodsUseCase.getActiveMethods(): PaymentMethodViews` (`payments/query/service/port/in`).
- `GetPaymentMethodsService`(`@Transactional(readOnly = true)`)가 기존 `GetPaymentMethodDao.findActiveMethods()`에 위임.
  - 기존 `GetCheckoutService`는 orderer+methods를 함께 반환하고 userId가 필요하므로 재사용하지 않는다(주문자 조회 낭비 회피). 조회 dao(`GetPaymentMethodDao`)만 공유한다.

## 조합 (api)

`CoinController`가 `GetCoinCheckoutUseCase`(아이템)와 `GetPaymentMethodsUseCase`(구매방법)를 각각 호출해 `CoinCheckoutResponse`로 조립한다. (gathering 체크아웃이 `PaymentsController`에서 payments+gathering을 조합하는 것과 동일 패턴)

## 영향 범위 (신규/수정)

- `oneulsogae-core`
  - 신규: `coin/query/service/port/in/GetCoinCheckoutUseCase.kt`, `coin/query/service/GetCoinCheckoutService.kt`, `payments/query/service/port/in/GetPaymentMethodsUseCase.kt`, `payments/query/service/GetPaymentMethodsService.kt`
  - 수정: `coin/query/dao/GetCoinItemDao.kt`(findById 추가), `coin/CoinErrorCode.kt`(COIN-004 추가)
- `oneulsogae-infra`
  - 수정: `coin/query/GetCoinItemDaoImpl.kt`(findById 구현)
- `oneulsogae-api`
  - 신규: `coin/response/CoinCheckoutResponse.kt`
  - 수정: `coin/CoinController.kt`(GET /checkout 추가, in-port 2개 주입)
- 테스트: 신규 E2E `CoinCheckoutE2ETest`

## 테스트

- E2E `CoinCheckoutE2ETest`(`AbstractIntegrationSupport`):
  - 아이템 존재 → 200, `item`(id·가격 필드) + `paymentMethods`(활성만, displayOrder 순, 비활성 제외) 반환.
  - 없는 itemId → 404 `COIN-004`.
- 도메인 신규 로직이 단순 조회뿐이라 도메인 유닛 테스트는 추가하지 않는다(E2E가 not-found 분기까지 커버).

## 잔여/후속 (범위 밖)

- 실제 코인 구매(결제완료·PG 승인·코인 지급) 흐름.
- 주문자 정보·보유 코인 잔액 등 체크아웃 부가 정보.
