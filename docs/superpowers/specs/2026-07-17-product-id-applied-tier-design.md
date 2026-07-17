# productIdFor 적용 티어 반환 설계

- 날짜: 2026-07-17
- 상태: 승인됨 (인라인 구현)
- 전제: 체크아웃·결제완료 productId 전환(`2026-07-16-checkout-product-id-design.md`), payments product_id 컬럼(`2026-07-17-payments-product-id-column-design.md`)

## 목적

모임 상세 응답의 `schedules[].productId`가 NORMAL 고정이 아니라 **적용 티어의 상품 id**를 담는다.
프론트가 넘기는 productId와 사용자에게 보이는 가격(salePrice)이 항상 같은 상품 행을 가리키게 한다.

## 동작 규칙 (salePriceFor와 동일 체인)

- 얼리버드 유효(티어 존재·미소진) & EARLY_BIRD 행 존재 → EARLY_BIRD 상품 id
- 얼리버드 소진 & DISCOUNT 행 존재 → DISCOUNT 상품 id
- 그 외 → NORMAL 상품 id (부재 시 checkNotNull "정가 상품이 없습니다: $id" 실패)

## 구현

- `GatheringScheduleView`에 private `appliedProductFor(gender): GatheringProductView?`(적용 프로모션 티어 행 선택,
  NORMAL 제외)를 도입해 `salePriceFor`·`productIdFor`가 같은 선택 로직을 공유한다.
  `salePriceFor` 외부 동작 불변. `feeFor`/`earlyBirdFeeFor`/`discountFeeFor`(티어별 노출용)는 유지.
- KDoc 갱신: `GatheringProductView.id`, `GatheringDetailResponse.Schedule`의 productId 설명을 "적용 티어 상품 id"로.

## 파급 없음 확인

- 백엔드 체크아웃·결제완료: `getProduct`는 타입 무관 식별, 실결제가는 서버 확정 — 무수정.
- payments.product_id: 프론트가 적용 티어 id를 보내므로 자연히 가격 근거(적용 티어)를 가리킴.
  체크아웃~접수 사이 얼리버드 소진 시 product_id(EARLY_BIRD)와 amount(할인가/정가) 불일치 가능 —
  금액은 서버 확정이라 정확하고 product_id는 "요청 시점 근거"(기존 NORMAL 고정 저장과 동일 성질, 신규 리스크 아님).
- 프론트: 코드 무수정(받은 값 전달). 주석 2곳("정가(NORMAL) 상품 id")만 낡음 — 안내 대상.

## 테스트

- `GatheringScheduleViewTest.productIdFor`: 얼리버드 유효 → EARLY_BIRD id / 소진+할인가 → DISCOUNT id /
  소진+할인가 없음 → NORMAL id / 티어 없음 → NORMAL id / NORMAL 부재 실패.
- `OfflineGatheringDetailE2ETest`: 얼리버드 유효 컨텍스트의 productId 기대값을 EARLY_BIRD 상품 id로 변경.
