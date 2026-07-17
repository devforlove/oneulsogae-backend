# 결제완료(complete) — PG 승인(confirm) 전제 흐름 설계

- 작성일: 2026-07-17
- 도메인: payments (+ gathering 좌석 복원)
- 선행: [[checkout-product-id-conversion]] (요청 티어가 확정 · GATHERING-007), [[payment-complete-pending-approval]] (PENDING → 어드민 승인)

## 1. 배경 / 현재 상태

현재 `POST /payments/v1/complete`는 **무검증 접수**다. PG 연동이 없어 실제 결제 여부를 검증하지 않고, 좌석을 확보(PENDING 등록 + 여분 차감)한 뒤 서버 확정가로 결제 기록만 남긴다. 실제 입금 확인·승인은 어드민이 수동으로 한다.

본 설계는 **PG 결제가 앞단에 있다고 가정**하고 complete에 PG 승인(confirm) 단계를 붙인다. PG사는 아직 미정이므로, PG를 **out-port로 추상화하고 stub 어댑터로 구현**한다(계약·흐름을 완성하고, 실제 PG 어댑터는 나중에 교체).

## 2. 목표

- 클라이언트가 PG 결제 **인증**까지 마치고 `paymentKey`를 넘기면, 서버가 좌석을 먼저 확보한 뒤 PG **최종 승인**(confirm)을 호출해 결제를 확정한다.
- 좌석이 없으면(소진·마감) 승인을 아예 하지 않아 **돈이 빠지지 않게** 한다(환불 최소화).
- 헥사고날 경계를 지키며, 실제 PG 어댑터 교체 시 코어·포트 변경이 없도록 한다.

## 3. 확정된 결정 (사용자 승인, 2026-07-17)

| # | 결정 | 선택 |
|---|---|---|
| 1 | PG 다루는 형태 | **포트 추상화 + stub 어댑터** (실제 PG사 미정) |
| 2 | 좌석/승인 순서 | **좌석 먼저 → PG 승인** (승인 전 좌석 없으면 미청구 → 환불 불필요) |
| 3 | confirm 실패 처리 | **좌석 복원 + 에러, 기록 안 남김** (confirm 전이라 미청구) |
| 4 | confirm 성공 후 상태 | **PENDING 유지** (어드민 수동 승인 존치) |
| 5 | Stub 성공/실패 제어 | **요청 헤더 `X-Stub-Pg-Confirm`** (`fail`이면 실패), stub은 비-prod 프로파일 한정 |
| 6 | 정합성 한계 | **지금 범위 밖** (아래 8절) |

## 4. 전체 흐름

```
POST /payments/v1/complete { productId, paymentKey }
  │
  ├─[TX1] 좌석 접수 (기존 RegisterGatheringMemberUseCase)
  │        getForUpdate(잠금) → register(gender, type) → PENDING 등록 + 여분 차감 → 커밋
  │        └─ 실패: GATHERING-003(판매중 아님) / 004(정원 마감) / 005(중복) / 007(얼리버드 마감)
  │             → 롤백 → 에러 응답. **PG 승인 호출 안 함 → 돈 안 빠짐**
  │
  ├─ PaymentGatewayPort.confirm(paymentKey, 서버확정금액)     ← 트랜잭션 밖 (외부 호출)
  │        └─ 실패 → [보상 TX] 좌석 복원(여분 되돌림 + 방금 접수 취소) → 에러 응답
  │
  └─[TX2] confirm 성공 → payment 기록(paymentKey, 서버확정금액) 저장 → 커밋
           └─ 성공 응답 { amount }  (참가 상태는 PENDING 유지)
```

**트랜잭션을 하나로 묶지 않는 이유**: 좌석 확보는 `schedule` 행을 `PESSIMISTIC_WRITE`로 잠근다. 이 잠금을 외부 confirm 호출(수백 ms~수 초) 동안 유지하면 정원 경쟁 시 락 장기 점유로 동시성이 무너진다. 따라서 TX1을 짧게 커밋하고, confirm은 트랜잭션 밖에서, 결과에 따라 TX2(저장) 또는 보상(복원)을 각각 자기 트랜잭션으로 실행한다.

## 5. 컴포넌트 (헥사고날)

| 구분 | 이름 | 위치 | 역할 |
|---|---|---|---|
| out-port (신규) | `PaymentGatewayPort` | core payments `command/.../port/out` | `confirm(paymentKey: String, amount: Int): 결과` — PG 최종 승인 |
| 어댑터 (신규, stub) | `StubPaymentGatewayAdapter` | infra payments | 헤더 `X-Stub-Pg-Confirm`로 성공/실패. `@Profile`(비-prod) |
| in-port (신규) | 좌석 복원 (예: `ReleaseGatheringSeatUseCase`) | core gathering `command` | confirm 실패 시 방금 접수 취소 + 여분 복원 (admin `RejectGatheringMemberService`의 복원과 대칭) |
| 변경 | `CompletePaymentService` | core payments | `@Transactional` 제거 → **3단계 오케스트레이션**(각 단계 자기 트랜잭션 + 외부 호출) |
| 변경 | `CompletePaymentCommand` / `CompletePaymentRequest` | core / api | `+ paymentKey` |
| 변경 | `Payment` / `PaymentEntity` / payments 테이블 | core / infra | `+ paymentKey` |

`CompletePaymentService`는 트랜잭션 경계를 넘나드는 오케스트레이터가 되므로 클래스 자체에는 `@Transactional`을 두지 않고, 각 단계(register / savePayment / releaseSeat)가 자기 트랜잭션을 갖는 서비스·유스케이스를 호출한다.

## 6. 요청 / 응답

- 요청: `{ "productId": <number>, "paymentKey": "<PG 거래 식별자>" }`
- 성공 응답: `{ "success": true, "data": { "amount": <number> } }` — **불변**(PENDING 유지라 상태 응답 변화 없음).
- 실패 응답: 공통 봉투 `{ "success": false, "error": { "code", "message" } }`.

**금액 근거**: confirm에 넘기는 금액은 `register()`가 반환한 **서버 확정 티어가**(`registered.amount`)다. 클라이언트가 금액을 보내지 않으므로 조작이 불가하고, 얼리버드 소진 등 금액 변동은 이미 TX1의 좌석 단계에서 `GATHERING-007`로 걸러진 뒤라 confirm 금액은 항상 서버가 확정한 값이다.

## 7. payments 변경

- `payment_key VARCHAR(255) NOT NULL` 추가. **상태 컬럼은 두지 않는다** — confirm 성공(PAID) 건만 저장하기로 했으므로(결정 #3) 상태 다양성이 없다(YAGNI). 환불·부분취소가 실제로 필요해질 때 상태 컬럼을 도입해 재설계한다.
- `docs/migration/payments.sql`이 아직 운영 미반영이므로 별도 ALTER 없이 **CREATE TABLE 정의에 `payment_key`를 직접 포함**한다.

## 8. Stub 제어

- `PaymentGatewayPort.confirm(paymentKey, amount)` 시그니처는 **깨끗하게 유지**(테스트 힌트 파라미터 없음).
- `StubPaymentGatewayAdapter`가 `RequestContextHolder`로 요청 헤더 **`X-Stub-Pg-Confirm`** 을 읽는다. 값이 `fail`이면 confirm 실패, 없거나 그 외 값이면 성공(요청 금액을 승인).
- stub은 **비-prod 프로파일 한정**(`@Profile`)으로만 빈 등록한다(SES 어댑터/스텁 분리 관례와 동일). prod에는 stub이 없으므로 헤더로 결제를 조작할 수 없다. 실제 PG 어댑터는 prod 프로파일로 나중에 추가한다.
- 배제한 대안: 헤더를 `CompletePaymentCommand`에 실어 포트로 전달 — 프로덕션 `confirm` 계약에 테스트 전용 필드가 새므로 배제. 어댑터가 요청 컨텍스트를 읽는 지저분함은 stub 한정이며 실제 어댑터 교체 시 사라진다.

## 9. 정합성 한계 (지금 범위 밖 — 결정 #6)

- **confirm 성공 후 TX2(payment 저장) 실패**(DB 순간 장애 등): 돈은 승인됐는데 결제 기록이 남지 않는 드문 케이스. stub 단계에서는 로그로만 남기고, 실제 PG 연동 시 정산 대사(reconciliation)/재처리로 보강한다.
- **멱등성**: 같은 유저+일정 중복 접수는 `GATHERING-005`가, 이중 confirm은 좌석 단계(TX1)가 막는다. `payment_key` 유니크 제약은 지금 두지 않는다(필요 시 추가).
- **보상(좌석 복원) 실패**: confirm 실패 후 좌석 복원 트랜잭션마저 실패하면 좌석이 PENDING으로 남는다(미청구). 어드민 승인 게이트가 뒤에 있어 실피해는 걸러지며, 재시도/모니터링은 후속.

## 10. 테스트 전략

- **E2E** (`PaymentsCompleteE2ETest` 확장, Testcontainers):
  - confirm 성공 → PENDING 참가 + payment(`paymentKey`, 서버확정금액) 저장 + 여분 차감.
  - 좌석 소진(정원/얼리버드) → **confirm 미호출**(스텁 실패 헤더를 줘도 좌석 단계에서 먼저 막힘) → 아무것도 저장 안 함.
  - `X-Stub-Pg-Confirm: fail` → 좌석 확보 후 confirm 실패 → **좌석·여분 복원 확인**, payment 미저장.
  - 기존 케이스(중복 GATHERING-005, 타성별 PAYMENTS-003, 성별 미확정 PAYMENTS-002, 상품 없음 GATHERING-006)는 요청에 `paymentKey`를 추가해 유지.
- **도메인 유닛**: 좌석 복원(release) 로직에 도메인 규칙이 생기면 `JoiningSchedule`/해당 도메인 모델에 유닛 테스트 추가.

## 11. 변경 파일 (예상)

- 신규: `PaymentGatewayPort`(core), `StubPaymentGatewayAdapter`(infra), 좌석 복원 in-port/서비스(core gathering) + out-port/어댑터.
- 변경: `CompletePaymentService`, `CompletePaymentCommand`, `CompletePaymentRequest`, `Payment`, `PaymentEntity`, `docs/migration/payments.sql`.
- 테스트: `PaymentsCompleteE2ETest`, (필요 시) 도메인 유닛.

## 12. 프론트 영향

- 요청 바디에 **`paymentKey` 추가** 필요(PG 결제 인증 결과). PG 결제창 연동 후 성공 콜백의 거래 식별자를 넘긴다.
- 응답 스키마·에러 코드는 기존과 동일(단, PG 승인 실패에 대한 신규 에러 코드가 구현 단계에서 추가될 수 있음 — 계획에서 확정).

## 13. 후속 (본 설계 밖)

- 실제 PG(토스/포트원 등) 어댑터 구현 + prod 프로파일 등록.
- 환불·부분취소가 필요해지면 payments 상태 컬럼 도입 재설계.
- 정합성 보강(정산 대사, 멱등 유니크, 보상 재시도).
