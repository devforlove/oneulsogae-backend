# payments productId 컬럼 추가 설계

- 날짜: 2026-07-17
- 상태: 승인됨 (인라인 구현)
- 전제: 체크아웃·결제완료 productId 전환 완료(`2026-07-16-checkout-product-id-design.md`), payments 테이블 운영 미반영

## 목적

결제 기록(payments)에 가격 근거 — 어떤 상품(productId)으로 접수했는지 — 를 남긴다.
gathering_members의 `earlyBirdApplied`는 좌석 재고 차감 추적(거절 복원용)으로 그대로 두고,
payments는 결제 관심사인 상품 식별자를 갖는다(관심사 분리 — 이동이 아니라 추가).

## 결정 사항

| 쟁점 | 결정 |
|---|---|
| 저장 값 | **결제완료 요청의 productId**(성별 NORMAL 상품 id) 그대로. 적용 티어는 amount + member.earlyBirdApplied로 역추적 가능 — register 결과 확장 안 함(YAGNI) |
| DDL | payments 테이블이 운영 미반영이므로 `docs/migration/payments.sql`의 CREATE TABLE에 `product_id BIGINT NOT NULL` 직접 추가(ALTER 불필요). 조회 경로 없음 → 인덱스 없음 |

## 변경 (payments 도메인 내부, API·포트 변경 없음)

1. `Payment`(core command 도메인): `productId: Long` 추가(scheduleId 다음).
2. `PaymentEntity`: `product_id BIGINT NOT NULL` 컬럼 + KDoc(요청 상품 id — 가격 근거).
3. `PaymentAdapter.save`: 양방향 매핑에 productId 추가.
4. `CompletePaymentService`: `Payment(... productId = command.productId ...)`.
5. `docs/migration/payments.sql`: `product_id BIGINT NOT NULL` 추가.

## 테스트

`PaymentsCompleteE2ETest` 정상 접수 케이스에서 저장된 결제 행의 `productId`가 요청 상품 id와 일치하는지 단언(TDD: 단언 먼저 → RED → 구현 → GREEN).
