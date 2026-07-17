# 결제 PENDING 선저장 설계

## 배경 / 문제

현재 `CompletePaymentService`는 트랜잭션 없는 오케스트레이터로 다음 순서를 따른다.

1. `register` — 좌석 확보(자기 트랜잭션)
2. `PaymentGatewayPort.confirm(paymentKey, amount)` — PG 최종 승인(트랜잭션 밖)
3. 승인 성공 시 결제 기록 저장(`SavePaymentPort.save`)

이 구조는 "매진이면 승인 전에 실패해 미청구"는 보장하지만, **② 승인 성공(돈 차감) 이후 ③ 저장이 실패**하면 카드는 청구됐는데 결제 기록이 유실되는 틈이 남는다. `paymentKey`가 어디에도 durable하게 남지 않으므로 사후에 그 청구를 추적할 근거가 없다.

## 목표

PG 승인 **이전에** `paymentKey`를 담은 결제 기록을 `PENDING` 상태로 먼저 저장한다. 이렇게 하면 승인 성공 직후 크래시가 나도 `PENDING` 기록이 남아, 이후 PG 조회(`paymentKey`)로 실제 상태를 대조·복구할 수 있다. "청구됐는데 흔적 없음"을 제거하는 것이 목표다.

비목표: `PENDING` 잔류 기록을 실제로 대사(reconciliation)하는 배치/잡은 이번 범위 밖이다(후속 과제). 이번 작업은 **durable한 근거를 남기는 것**까지다.

## 흐름 (변경 후 `CompletePaymentService`)

```
검증(주문자 성별·상품 성별 일치) — 기존 유지
① register            좌석 확보(자기 tx)               — 매진/마감이면 여기서 실패 → 결제 기록 없음, 미청구
② save PENDING        결제 기록 insert(status=PENDING, paymentKey, 서버 확정가)  ← 승인 전 durable 기록
③ confirm             PG 최종 승인(tx 밖)
④-성공  updateStatus(paymentId, APPROVED)             — 좌석은 PENDING 유지(어드민 승인 존치)
④-실패  updateStatus(paymentId, FAILED)
        + ReleaseGatheringSeatUseCase.release(scheduleId, userId)
        + throw 402 PAYMENTS-004
```

### 순서 결정 근거

- **좌석 선확보(①) 유지**: 매진 시 결제 기록조차 만들지 않고 미청구로 끝나는 기존 이점을 보존한다.
- **PENDING 저장(②)은 register 이후**: register가 매진으로 실패하면 고아 PENDING 결제 기록이 남지 않는다. ① 성공 후 ② 실패(순수 DB 오류)는 좌석만 확보된 채 청구 없이 요청이 실패하는 것으로, 돈이 얽히지 않아 감내 가능한 최소 틈이다.
- **참가 상태(`gathering_members.status`)와 직교**: ④-성공에서도 좌석은 `PENDING`(어드민 승인 대기)으로 남는다. `payments.status`는 PG 청구 라이프사이클, `gathering_members.status`는 참가 승인 라이프사이클로 서로 다른 축이다.

## 상태 모델

- 신규 enum `PaymentStatus { PENDING, APPROVED, FAILED }`
  - 위치: `meeple-core` `payments/command/domain/PaymentStatus.kt` (payments 전용 — `meeple-common`이 아님. 현재 다른 모듈이 이 상태를 공유하지 않는다)
- `Payment` 도메인 모델에 `status: PaymentStatus` 필드 추가.
- `payments.status` 컬럼 신설. **이는 "payments에 상태 컬럼 없음"이라는 기존 결정(memory: payment-complete-pending-approval)을 이번에 뒤집는 것**이다. 사유: PG 청구 라이프사이클의 durable 원장이 필요해졌다.

### 상태 전이

| 시점 | 전이 |
|------|------|
| ② 저장 | (없음) → `PENDING` |
| ④ 승인 성공 | `PENDING` → `APPROVED` |
| ④ 승인 실패 | `PENDING` → `FAILED` (행 보존, 승인 실패 이력 추적) |

`FAILED` 행은 삭제하지 않고 남긴다(재접수 시 새 PG 인증 = 새 `paymentKey` = 새 행).

## 포트 (CQRS)

두 아웃포트 모두 `PaymentAdapter`(엔티티당 어댑터 하나)가 구현한다.

- `SavePaymentPort.save(payment: Payment): Payment` — 기존 유지. 이제 `status`를 포함해 insert(호출부가 `PENDING`으로 생성).
- 신규 `UpdatePaymentStatusPort.updateStatus(paymentId: Long, status: PaymentStatus)` — 기존 행 상태 전이. 자기 트랜잭션(`@Transactional`)으로 find + dirty checking. `PaymentEntity.status`는 `var`.

## 멱등 (payment_key 유니크)

- `PaymentEntity` / `payments.sql`에 `UNIQUE(payment_key)` 추가.
- 근거: PG 인증마다 `paymentKey`가 고유하므로 재접수(새 인증)와 무충돌이고, 같은 `paymentKey` 이중 제출(더블클릭)의 이중 기록을 DB 레벨에서 차단한다.
- **범위 한정**: 유니크는 DB 레벨 이중 기록 방지까지다. 위반 시 우아한 재생(기존 결과 반환)은 이번 범위 밖 — 위반은 예외로 표출된다(후속 과제). 실무 더블클릭은 대개 ①에서 중복 접수(GATHERING-005)로 먼저 막힌다.

## DDL (`docs/migration/payments.sql`)

기존 `payments.sql`에 다음을 반영한다.

- `status VARCHAR(50) NOT NULL` 컬럼 추가.
- `UNIQUE KEY uk_payment_key (payment_key)` 추가.

memory(payment-complete-pending-approval)상 payments 테이블은 아직 prod 미반영(배포 전 하드 게이트)이므로 `payments.sql` 파일을 직접 갱신한다. 이미 적용된 환경이 있다면 아래 `ALTER`로 대응한다(스펙에 병기, 파일에는 반영하지 않음).

```sql
ALTER TABLE payments
  ADD COLUMN status VARCHAR(50) NOT NULL,
  ADD UNIQUE KEY uk_payment_key (payment_key);
```

`ddl-auto: validate` 환경이므로 엔티티와 실 DDL이 일치해야 한다.

## 테스트

- **E2E `PaymentsCompleteE2ETest`**
  - confirm 실패 케이스 변경: 현재 "결제 기록 없음" 단언을 **`status=FAILED` 기록 존재**로 바꾼다. 좌석 CANCELED·여분 복원 단언은 유지.
  - 성공 케이스: 저장된 기록에 `status=APPROVED` 단언 추가.
  - register 이전/register 단계에서 막히는 실패(매진 GATHERING-004/007, 성별 PAYMENTS-002/003, 상품 GATHERING-006, 판매중 아님 GATHERING-003, 중복 GATHERING-005)는 결제 기록 미생성 — 기존 단언 유지.
- **도메인 유닛**: `Payment`/`PaymentStatus`에 의미 있는 도메인 로직이 생기면 Kotest 유닛으로 커버한다. 단순 상태 필드 보유에 그치면 유닛 테스트를 새로 만들지 않는다.

## 영향 범위

- `meeple-core`: `Payment`, `PaymentStatus`(신규), `SavePaymentPort`(변화 없음, 의미만 확장), `UpdatePaymentStatusPort`(신규), `CompletePaymentService`.
- `meeple-infra`: `PaymentEntity`(status var + unique), `PaymentAdapter`(save에 status, updateStatus 구현).
- `docs/migration/payments.sql`.
- `meeple-api` 테스트: `PaymentsCompleteE2ETest`.
- **API 응답 계약 변화 없음** — `CompletePaymentResult`(amount)는 그대로. 프론트엔드 대응 불필요.

## 잔여 한계(범위 밖, 후속)

- PENDING 잔류 기록 대사(reconciliation) 배치 미구현.
- `confirm`이 false가 아니라 **예외**를 던지면 ④ 분기를 타지 못해 `release` 미호출 + 기록이 `PENDING` 잔류(실 PG 어댑터 도입 시 예외→보상 매핑 필요).
- payment_key 유니크 위반의 우아한 재생 처리 미구현(현재는 예외 표출).
