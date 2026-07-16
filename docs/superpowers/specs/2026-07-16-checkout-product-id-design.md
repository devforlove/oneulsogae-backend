# 체크아웃·결제완료 productId 전환 설계

- 날짜: 2026-07-16
- 상태: 승인됨
- 전제: gathering_products 이관 완료(`2026-07-16-gathering-products-design.md`)

## 목적

체크아웃 조회와 결제완료 접수가 `gatheringId + scheduleId + gender` 조합 대신 **`productId` 하나**로 상품을
식별하게 한다. gathering_products의 한 행이 (모임, 일정, 성별)을 온전히 식별하므로 파라미터를 일원화한다.

## 결정 사항 (Q&A로 확정)

| 쟁점 | 결정 |
|---|---|
| 프론트의 productId 획득 경로 | offline 모임 상세 응답의 성별별 일정 아이템에 `productId`(해당 성별 NORMAL 상품 id) 필드 추가 |
| 범위 | 체크아웃 조회 + 결제완료 접수 둘 다 전환 |
| 결제완료 성별 불일치 | product.gender ≠ 본인 프로필 성별이면 400 에러로 거부 |
| 해석 계층 | gathering query in-port(`GetGatheringsUseCase`) 확장 — products 소유 도메인이 제공 |

## 1. gathering query 확장 (core)

- read model `GatheringProductIdentity(productId: Long, gatheringId: Long, scheduleId: Long, gender: Gender)`
  신설 (`core/gathering/query/dto`).
- `GetGatheringsUseCase.getProduct(productId: Long): GatheringProductIdentity` 추가.
  없으면(soft delete 포함) `GatheringErrorCode.GATHERING_PRODUCT_NOT_FOUND`(신규, 404).
- `GetGatheringDao.findProductById(productId: Long): GatheringProductIdentity?` + `GetGatheringDaoImpl`
  QueryDSL 구현(id 동등 단건 — PK seek, `@SQLRestriction`으로 삭제 행 제외).
- **타입은 식별에 쓰지 않는다**: EARLY_BIRD/DISCOUNT 행의 id가 와도 같은 (모임, 일정, 성별)로 해석된다.
  실결제가는 어차피 서버가 티어 규칙으로 확정한다.

## 2. offline 상세 응답에 productId 추가 (하위호환)

- `GatheringProductView`에 `id: Long` 추가(맨 앞 필드). DAO 투영은 엔티티 id를 그대로 싣는다.
- `GatheringScheduleView.productIdFor(gender: Gender): Long` — 해당 성별 NORMAL 상품 id.
  `feeFor`처럼 NORMAL 부재 시 checkNotNull로 명확히 실패.
- `GatheringDetailResponse.Schedule`에 `productId: Long` 필드 추가. 기존 필드는 전부 유지(필드 추가라 하위호환).

## 3. 체크아웃 API 변경 (breaking)

`GET /payments/v1/checkout?productId={id}` — `gatheringId`/`scheduleId`/`gender` 파라미터 제거.

컨트롤러 조합(형태 유지):

```
product   = getGatheringsUseCase.getProduct(productId)              // 없으면 404(GATHERING_PRODUCT_NOT_FOUND)
gathering = getGatheringsUseCase.getGathering(product.gatheringId)  // 모집중 검증 유지(404)
schedule  = gathering.scheduleOrNull(product.scheduleId)
            ?: throw CHECKOUT_PRODUCT_NOT_FOUND                     // 데이터 정합 방어(기존 코드 재사용)
응답      = CheckoutResponse.of(checkout, gathering, schedule, product.gender)
```

- **응답 형태 불변**(orderer·product·paymentMethods).
- 기존 "타성별 gender 파라미터 허용" 정책은 자연 소멸(성별은 product가 결정).

## 4. 결제완료 API 변경 (breaking)

- `CompletePaymentRequest`: `{gatheringId, scheduleId}` → `{productId}` (`@NotNull`).
- `CompletePaymentCommand(productId: Long)`.
- `CompletePaymentService`:
  1. 본인 프로필 성별 확정(기존 — 없으면 ORDERER_GENDER_REQUIRED)
  2. `getGatheringsUseCase.getProduct(command.productId)` (없으면 404)
  3. `product.gender != 프로필 성별` → `PaymentsErrorCode.PAYMENT_PRODUCT_GENDER_MISMATCH`(신규, 400)
     — 체크아웃에서 본 가격과 접수 가격이 달라지는 혼란 방지
  4. 기존대로 `register(product.gatheringId, product.scheduleId, userId, gender)` + Payment 저장
- payments 테이블 스키마 불변(productId는 저장하지 않는다 — YAGNI).
- payments 명령 서비스가 gathering **query in-port**를 주입하는 것은 도메인 간 참조 규칙(타 도메인은 in-port로)에 부합한다.

## 5. 에러 코드

- `GatheringErrorCode.GATHERING_PRODUCT_NOT_FOUND` — 404, "상품을 찾을 수 없습니다."
- `PaymentsErrorCode.PAYMENT_PRODUCT_GENDER_MISMATCH` — 400, "본인 성별의 상품이 아닙니다."

## 6. 테스트

- **유닛(Kotest)**: `GatheringScheduleView.productIdFor`(성별별 id 반환, NORMAL 부재 실패).
- **E2E**:
  - `PaymentsCheckoutE2ETest`: 쿼리 파라미터를 productId로 전환, 없는 productId 404 케이스 추가.
  - `PaymentsCompleteE2ETest`: body를 productId로 전환, 타성별 productId 400 케이스 추가.
  - `OfflineGatheringDetailE2ETest`: 일정 아이템의 `productId` 단언 추가(persist한 NORMAL 상품 id와 일치).

## 프론트엔드 영향 (직접 수정하지 않고 안내)

- **모임 상세**: `schedules[]` 아이템에 `productId` 추가 — 하위호환(먼저 배포해도 안전). 이후 체크아웃·결제완료에 이 값을 사용.
- **체크아웃**: 쿼리 파라미터 `gatheringId&scheduleId&gender` → `productId` (**breaking**).
- **결제완료**: body `{gatheringId, scheduleId}` → `{productId}` (**breaking**).
- breaking 2건은 프론트 반영과 배포 타이밍을 맞춰야 한다. DDL 변경은 없다.
