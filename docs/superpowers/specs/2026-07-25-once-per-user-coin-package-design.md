# 회원당 1회 구매 코인 패키지 설계

## 배경 / 목표

코인 상점에 **회원당 한 번만 구매할 수 있는 코인 패키지**를 추가한다. 기존 코인 상품(`coin_items`)에 플래그를 얹어 표현하며, 지급 내용은 일반 상품과 같은 **코인만**이다(보너스 구성 없음).

함께 정리하는 항목:

- **판매 채널 구분**: 상품마다 PG(웹)·IAP(앱) 중 어느 경로로 파는지 `sale_channel`로 구분한다. 앱은 IAP 상품만, 웹은 PG 상품만 노출한다(스토어 정책상 앱 내 디지털재는 IAP 필수).
- **IAP ↔ coin_item 매핑**: 현재 IAP 검증은 스토어 SKU 접미 숫자를 파싱해 코인 수만 정하고 `coin_items`와 연결되지 않는다(`VerifyIapPurchaseService.coinAmountOf` TODO). 1회 제한·채널 판정에 itemId가 필요하므로 `store_product_id`(SKU) 컬럼으로 SKU→coin_item을 해석하도록 바꾼다.
- **IAP 멱등·기록**: IAP 결제 기록이 없어 같은 영수증 재검증 시 재적립될 수 있다(`VerifyIapPurchaseService` 멱등 TODO). `iap_payments`(transaction_id 유니크) 기록을 도입해 해소한다.

비목표: 코인+보너스 구성, 어드민 패키지 CRUD, 프론트·모바일 UI(백엔드 확정 후 별도 안내).

## 결정: 1회 제한을 무엇으로 강제하나

두 결제 경로가 **서로 다른 테이블**에 기록된다(PG → `coin_payments`, IAP → 신설 `iap_payments`). 한 테이블에 유니크 제약을 걸어 "경로 무관 회원당 1회"를 원자적으로 막을 수 없다.

→ 두 경로가 공통으로 쓰는 **전용 가드 테이블 `coin_item_purchases`(`UNIQUE(user_id, item_id)`)** 를 둔다. once_per_user 상품을 실제 적립 성공한 시점에 두 경로 모두 여기에 INSERT한다. 유니크 위반이 동시 이중구매까지 막는 최종 방어선이고, 상점 필터의 단일 소스이기도 하다.

(대안 — 기존 payment 테이블 재활용: `coin_payments`(status=APPROVED)와 `iap_payments`를 UNION 검사. 두 쿼리로 나뉘고 두 경로에 걸친 단일 유니크 제약이 없어 경합 방어가 약해 채택하지 않는다.)

## 데이터 모델

### `coin_items` — 컬럼 3개 추가

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| `once_per_user` | `boolean not null default false` | 회원당 1회 구매 패키지 여부. |
| `sale_channel` | `varchar(10) not null default 'PG'` | 판매 채널. `CoinSaleChannel { PG, IAP, BOTH }`. |
| `store_product_id` | `varchar(255) null`, **unique** | 스토어 SKU. IAP가 이 값으로 SKU→coin_item 해석. PG 전용은 null. |

**불변식**: `sale_channel ∈ {IAP, BOTH}` 이면 `store_product_id`가 반드시 있어야 한다(IAP로 팔려면 SKU 필수). 도메인 `CoinItem.create`에서 검증한다(DB CHECK 대신 애플리케이션 검증 — 상품 등록 경로가 제한적이라 충분).

### `coin_item_purchases` (신설) — 1회 제한 가드

| 컬럼 | 설명 |
| --- | --- |
| `id` | PK |
| `user_id` | 구매자 |
| `item_id` | 구매한 coin_item |
| `created_at` | 적립 성공 시각 |

- **`UNIQUE(user_id, item_id)`**. once_per_user 상품을 실제 적립 성공했을 때 두 경로가 INSERT.
- 인덱스: 유니크 제약이 `(user_id, item_id)` 조회(상점 필터·선검사)를 그대로 받친다.

### `iap_payments` (신설) — IAP 멱등·감사

| 컬럼 | 설명 |
| --- | --- |
| `id` | PK |
| `user_id` | 구매자 |
| `item_id` | SKU로 해석한 coin_item |
| `platform` | `StorePlatform` (IOS/ANDROID) |
| `product_id` | 스토어 SKU |
| `transaction_id` | 스토어 거래 식별자, **unique** |
| `coin_amount` | 지급 코인 수(스냅샷) |
| `status` | `PaymentStatus` (기존 재사용) |
| `created_at` | 접수 시각 |

- **`UNIQUE(transaction_id)`**. 같은 영수증 재검증 시 재적립 방지.

## 도메인 / 포트 변경

### coin 도메인

- **`CoinItem`**(query dto) 필드 추가: `oncePerUser: Boolean`, `saleChannel: CoinSaleChannel`, `storeProductId: String?`. `create(...)`에 세 값과 불변식 검증(IAP/BOTH ⟹ storeProductId 필수) 추가.
- **`CoinSaleChannel`** enum 신설(`oneulsogae-common` `common/coin`) — `PG, IAP, BOTH`. `sellableVia(channel)` 판정(예: BOTH는 PG·IAP 모두 true) 캡슐화.
- **`GetCoinItemDao`**: `findByStoreProductId(sku: String): CoinItem?` 추가(IAP SKU 해석용). `findAll()` → 상점 필터를 받는 `findShopItems(userId: Long?, channel: CoinSaleChannel): CoinItems`로 대체(아래 상점 조회 참고).
- **`GetCoinItemBySkuUseCase.getBySku(sku): CoinItem`**(coin query in-port 신규) — IAP command 서비스가 dao를 직접 참조하지 않도록 in-port로 감싼다(없으면 `COIN_ITEM_NOT_FOUND`). 구현은 `findByStoreProductId`에 위임.
- **`GetCoinShopUseCase.getCoinShop(userId: Long?, channel: CoinSaleChannel): CoinItems`** 시그니처 변경.
- **1회 제한 가드 out-port**(coin command 신규):
  - `ExistsCoinItemPurchasePort.exists(userId, itemId): Boolean` — 선검사(이른 409).
  - `SaveCoinItemPurchasePort.save(userId, itemId)` — 가드 INSERT. 유니크 위반은 `DataIntegrityViolationException`으로 올라오고 서비스가 잡아 409로 매핑.
- **핵심: 적립·가드를 원자적으로 묶는 coin command in-port** `AcquirePurchasedCoinUseCase.acquire(userId, item: CoinItem): CoinBalance`.
  - `@Transactional` — ① 코인 적립(기존 적립 로직/포트 위임) ② `item.oncePerUser`면 `SaveCoinItemPurchasePort.save`로 가드 INSERT. **한 트랜잭션**이라 유니크 위반이면 적립까지 롤백된다(경합 이중적립 원천 차단). 위반 → `COIN_PACKAGE_ALREADY_PURCHASED`.
  - PG·IAP 구매 경로는 코인 구매 적립을 `AcquireCoinUseCase`가 아니라 이 in-port로 한다(도메인 간 참조 규칙 — payments가 coin **in-port** 호출).
- **선검사 in-port** `IsCoinItemPurchasedUseCase.isPurchased(userId, itemId): Boolean`(coin query) — PG가 **PG confirm 전에** 이미 구매를 걸러 헛된 승인·과금을 피하는 용도(이른 409). 실제 정합은 위 원자 적립이 보장한다.

### payments 도메인

- **`IapPayment`** 도메인 모델(command) + out-port `SaveIapPaymentPort`, `GetIapPaymentPort.findByTransactionId(txId): IapPayment?`.
- **에러코드** `PaymentsErrorCode.COIN_PACKAGE_ALREADY_PURCHASED("PAYMENTS-006", "이미 구매한 패키지입니다.", HttpStatus.CONFLICT)`.

## 구매 흐름

### PG — `CompleteCoinPurchaseService`

기존 흐름에 1회 제한 검사·원자 적립을 끼운다.

1. paymentKey 멱등(기존).
2. `getCoinCheckoutUseCase.getCheckout(itemId)` → CoinItem.
3. **선검사**: `item.oncePerUser`이고 `isCoinItemPurchasedUseCase.isPurchased(userId, item.id)`면 409(PG confirm 전에 막아 헛된 과금 회피).
4. PENDING 기록 저장 → PG confirm(기존).
5. 성공 시 `acquirePurchasedCoinUseCase.acquire(userId, item)` — 적립+가드 원자. 경합 유니크 위반이면 적립 롤백 + 409.
6. APPROVED 전이(기존).

(오케스트레이터라 클래스 트랜잭션은 없고 적립+가드만 한 트랜잭션. 좌초 PENDING의 수동 대사 스탠스는 기존과 동일.)

### IAP — `VerifyIapPurchaseService`

`coinAmountOf` 접미 파싱을 제거하고 SKU→CoinItem 해석으로 대체.

1. **SKU 해석**: `getCoinItemBySkuUseCase.getBySku(command.productId)` → CoinItem(없으면 `COIN_ITEM_NOT_FOUND`). `item.saleChannel.sellableVia(IAP)` 아니면 거부(IAP 상품 아님).
2. **멱등**: `getIapPaymentPort.findByTransactionId(txId)` 있으면 재생(현재 잔액 반환, 재적립 없음).
3. **선검사**: `item.oncePerUser`이고 `isPurchased` 면 409.
4. 영수증 검증(기존 `StoreReceiptVerifierPort`).
5. `acquirePurchasedCoinUseCase.acquire(userId, item)` — 적립+가드 원자.
6. `iap_payments` INSERT(transaction_id 유니크; 경합 위반 시 2번 재생 경로로 처리).

## 상점 조회 (구매한 패키지 숨김 + 채널 필터)

- **`GET /coins/v1/shop?channel=IAP`** — `channel` 필수 쿼리 파라미터(`CoinSaleChannel`). `@LoginUser AuthUser?` 추가(비로그인 허용).
- `GetCoinShopService.getCoinShop(userId, channel)`:
  - `findShopItems`는 `sale_channel = :channel OR sale_channel = BOTH` 인 상품을 반환하되,
  - `once_per_user = true` 이고 `coin_item_purchases`에 `(userId, itemId)`가 있는 상품은 제외(로그인 시). 비로그인이면 제외 없음.
- QueryDSL: `coin_items` LEFT JOIN `coin_item_purchases` on (item.id = p.item_id AND p.user_id = :userId), `where channel조건 AND NOT(once_per_user AND p.id IS NOT NULL)`.
- 응답 `CoinItemResponse`에 `oncePerUser` 필드 추가(클라이언트가 "한정" 뱃지 표시). `saleChannel`은 채널로 이미 걸러 내려가므로 노출 불필요(YAGNI).

## 영향 범위 (신규/수정)

- `oneulsogae-common`
  - 신규: `common/coin/CoinSaleChannel.kt`
- `oneulsogae-core`
  - 신규(coin): `command/application/port/out/ExistsCoinItemPurchasePort.kt`, `SaveCoinItemPurchasePort.kt`; `command/application/port/in/AcquirePurchasedCoinUseCase.kt`(적립+가드 원자)·구현 `AcquirePurchasedCoinService.kt`(`@Transactional`); `query/service/port/in/IsCoinItemPurchasedUseCase.kt`·구현 `IsCoinItemPurchasedService.kt`(선검사); `query/service/port/in/GetCoinItemBySkuUseCase.kt`·구현(IAP SKU 해석)
  - 신규(payments): `command/domain/IapPayment.kt`; `command/application/port/out/SaveIapPaymentPort.kt`, `GetIapPaymentPort.kt`
  - 수정: `coin/query/dto/CoinItem.kt`(필드·검증), `coin/query/dao/GetCoinItemDao.kt`(findByStoreProductId, findShopItems), `coin/query/service/GetCoinShopService.kt`·`port/in/GetCoinShopUseCase.kt`(userId·channel), `payments/command/application/CompleteCoinPurchaseService.kt`(선검사·가드), `payments/command/application/VerifyIapPurchaseService.kt`(SKU 해석·멱등·가드), `payments/PaymentsErrorCode.kt`(PAYMENTS-006)
- `oneulsogae-infra`
  - 신규 엔티티/어댑터: `CoinItemPurchaseEntity`·repository·어댑터(Exists/Save 구현), `IapPaymentEntity`·repository·어댑터(Save/Get 구현)
  - 수정: `CoinItemEntity`(3컬럼), `GetCoinItemDaoImpl`(findByStoreProductId, findShopItems), `CoinItemMapper`(필드)
- `oneulsogae-api`
  - 수정: `coin/CoinController.kt`(shop에 channel·@LoginUser), `coin/response/CoinItemResponse.kt`(oncePerUser)
- 테스트
  - 도메인 유닛: `CoinItem` once_per_user·채널 불변식(IAP/BOTH ⟹ SKU 필수).
  - E2E: PG 1회 패키지 구매 성공 → 2회차 409 → 상점에서 사라짐; IAP 검증 성공+`iap_payments` 기록 → 같은 txn 재검증 재생 → 다른 txn 같은 상품 409; 상점 채널 필터(IAP 요청 시 PG 전용 상품 제외, BOTH 포함) + 구매 후 숨김.

## 마이그레이션 (prod 수동 DDL)

- `alter table coin_items add column once_per_user tinyint(1) not null default 0, add column sale_channel varchar(10) not null default 'PG', add column store_product_id varchar(255) null, add unique key ux_coin_items_store_product_id (store_product_id);`
- `create table coin_item_purchases (... unique key ux_user_item (user_id, item_id) ...)`
- `create table iap_payments (... unique key ux_transaction_id (transaction_id) ...)`

(local은 ddl-auto=update로 자동 생성.)
