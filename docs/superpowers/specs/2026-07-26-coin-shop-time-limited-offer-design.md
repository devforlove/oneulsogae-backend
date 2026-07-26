# 코인샵 기간 한정 오퍼(가입 후 N일) 설계

## 배경 / 목표

코인샵에 **1회 한정 2배 이벤트** 같은 기간 한정 상품을 넣는다. 만료가 전체 유저 공통 절대날짜가 아니라 **각 유저의 가입시각 기준 상대값**(가입 후 N일)이다. 신규 가입자는 언제 가입하든 각자 가입시각 + N일까지 그 상품을 살 수 있다.

확정 방향(브레인스토밍):

- **A안 = 계산, 저장 없음**: `coin_items` 에 유효기간 길이 컬럼(`valid_days`)만 두고, 유저별 만료시각은 저장하지 않는다. 만료는 조회·구매 시점에 `유저 가입시각 + valid_days` 로 매번 계산한다. 유저별 오퍼 테이블·가입 훅·배치 없음.
- **대상 = 모든 신규 가입자 상시**(에버그린). 특정 이벤트 기간에 가입한 유저만 거르는 가입창(available_from/until)은 두지 않는다 — 필요해지면 후속 컬럼으로 확장 가능.
- **"2배"는 데이터**: 배수(bonus_multiplier) 로직을 두지 않는다. 이벤트 상품 = `coin_amount` 를 2배로 넣은 별도 `coin_items` 행. 지급·환불·코인이력 계산은 기존과 100% 동일하다(그냥 코인 많이 주는 상품일 뿐).
- **채널 = PG 전용**. IAP는 스토어가 직접 과금해 서버 선차단이 불가하므로 이번 범위에서 제외한다. IAP 이벤트 상품이 필요하면 스토어 콘솔 가용기간으로 별도 관리한다.
- **1회 한정 = 기존 `once_per_user` 재활용.**

비목표: 유저별 오퍼 부여 테이블, 개별 만료 연장·회수, 가입창(기간 한정 가입자만), 배수 곱셈 로직, IAP 이벤트 상품, 클라이언트 "2배" 배지·카운트다운 UX.

## 결정: 만료는 계산, "2배"는 데이터, 게이트는 두 겹

만료를 저장하지 않는 이유: 기준이 `user.created_at`(이미 존재)의 순수 함수이고 개별 조정이 없으므로, 저장하면 `created_at + valid_days` 를 중복 저장하고 동기화하는 상태가 된다. 매 조회/구매 시 계산이 더 단순하고 상태가 없다.

게이트 두 겹:
1. **노출 게이트(주력)** — 상점 목록에서 만료 오퍼를 숨긴다. 정상 흐름의 99%를 차단한다.
2. **결제-전 게이트(money 안전)** — 체크아웃 시점에 서버가 만료를 재검증한다. 상점 캐시 스테일·API 직접 호출로 만료 상품 구매를 시도해도 **PG 승인(capture) 전에** 400으로 막아 헛된 과금을 피한다.

핵심 근거: `CompleteCoinPurchaseService` 는 이미 `once_per_user` 선검사를 **PG `confirm`(실제 캡처) 전에** 수행해 "헛된 과금"을 막는다. 만료 검증을 같은 지점(체크아웃 아이템 로드)에 얹으면 체크아웃 화면과 구매 확정 양쪽이, 캡처 전에, 한 곳에서 차단된다 — 결제 후 거절/환불이 없다.

## 데이터 모델

### `coin_items` (수정) — 컬럼 1개 추가

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| `valid_days` | `int null` | 유저 가입시각 기준 유효일수. **NULL = 상시 판매**(기존 상품 하위호환). N이면 `가입시각 + N일` 까지만 노출·구매 가능. |

- 기존 컬럼(`coin_amount`·`price`·`sale_price`·`once_per_user`·`sale_channel`·`store_product_id`)·제약 불변.
- 유저별 만료시각을 저장하는 테이블·컬럼은 없다(A안).

## 도메인 / 포트

### coin 도메인 (core)

**read model `CoinItem`** (`coin/query/dto`)
- 필드 `validDays: Int? = null` 추가.
- `create(...)` 팩토리에 `validDays` 파라미터 추가(기본 null). `validDays` 값 검증은 두지 않는다(NULL 허용, 양수 가정 — 시드 데이터 책임).
- 만료 판정 메서드:
  ```kotlin
  fun isOfferActiveAt(userCreatedAt: LocalDateTime, now: LocalDateTime): Boolean =
      validDays?.let { now.isBefore(userCreatedAt.plusDays(it.toLong())) } ?: true
  ```
  - `validDays` 가 NULL이면 항상 활성(상시).
  - 경계: `now < 가입시각 + N일` (만료 시각은 exclusive — 가입 후 정확히 N일이 되는 순간 만료).

**일급 컬렉션 `CoinItems`** (`coin/query/dto`)
- 메서드 `activeOffersAt(userCreatedAt: LocalDateTime, now: LocalDateTime): CoinItems` 추가 — `values` 중 `isOfferActiveAt` 인 항목만 남긴 새 `CoinItems` 반환. (서비스가 `values` 를 직접 순회하지 않도록 캡슐화)

**조회 서비스 `GetCoinShopService`** (`coin/query/service`) — 노출 게이트
- 기존: `getCoinItemDao.findShopItems(userId, channel)` 위임.
- 변경: dao 결과에 기간 한정 상품(`validDays != null`)이 하나라도 있을 때만 유저 가입시각(user in-port)·`now`(TimeGenerator)를 얻어 `activeOffersAt(userCreatedAt, now)` 적용해 만료 오퍼 제거. 기간 한정 상품이 없으면 유저 조회 없이 그대로 반환.
- **가입시각 조회를 지연(lazy)하는 이유**: 상시 상품(validDays 전부 null)만 있는 정상 케이스에서 불필요한 user 조회를 피하고, 미가입 userId로도 동작하던 기존 흐름·테스트를 깨지 않는다.
- dao의 채널 필터·`once_per_user` 구매분 제외는 그대로(SQL). 만료 필터만 서비스에서 도메인 술어로 적용(상점은 작은 목록이라 메모리 필터로 충분).
- `@Transactional(readOnly = true)` 유지.

**체크아웃 서비스 `GetCoinCheckoutService`** (`coin/query/service`) — 결제-전 게이트
- in-port 시그니처 변경: `getCheckout(itemId: Long): CoinItem` → `getCheckout(userId: Long, itemId: Long): CoinItem`.
- 로직: `findById(itemId)` (없으면 `COIN_ITEM_NOT_FOUND`) → 상품이 기간 한정(`validDays != null`)일 때만 유저 가입시각·`now` 로 `isOfferActiveAt` 검사 → 만료면 `COIN_ITEM_OFFER_EXPIRED`(신규). 상시 상품이면 유저 조회 없이 그대로 반환. 활성이면 `CoinItem` 반환.
- 호출자 2곳 모두 userId 전달:
  - `oneulsogae-api` `PaymentsController`(체크아웃 화면 조회) — `@LoginUser` userId.
  - `CompleteCoinPurchaseService.complete` — 이미 보유한 `userId`.
- 둘 다 PG `confirm` 전이라 만료 거절이 과금을 유발하지 않는다.

### user 도메인 (core) — 가입시각 노출

- read model `UserView` (`user/query/dto`) 에 `createdAt: LocalDateTime`(가입시각) 필드 추가 + 해당 조회 dao 투영에 `UserEntity.createdAt`(BaseEntity) 반영.
- 기존 in-port `GetUserByIdUseCase.getById(id): UserView` 재사용해 coin 서비스가 가입시각을 얻는다. (신규 포트 신설하지 않음 — 필드 additive)

### 도메인 간 참조

- coin 조회/체크아웃 서비스는 user **in-port** `GetUserByIdUseCase` 로 가입시각을 얻는다(타 도메인 out-port·구현 직접 참조 금지).
- 현재 시각은 `TimeGenerator.now()` 주입(직접 `LocalDateTime.now()` 금지). 도메인 메서드엔 `now`·`userCreatedAt` 파라미터로 전달(테스트 시각 고정).

### 에러코드 `CoinErrorCode`

- 신규 `COIN_ITEM_OFFER_EXPIRED("COIN-005", "판매 기간이 종료된 상품입니다.", HttpStatus.BAD_REQUEST)`.
  (기존 사용 코드: COIN-001·003·004. COIN-005 신규.)

## 영속성 어댑터 (infra)

- `CoinItemEntity` 에 `validDays: Int?`(컬럼 `valid_days`) 필드 추가.
- `GetCoinItemDaoImpl` 의 `CoinItem` 투영을 **8-arg**(…, `coinItem.validDays`)로 확장. `findShopItems`·`findById`·`findByStoreProductId` 모두 같은 `projection(...)` 사용하므로 한 곳 수정.
- `findShopItems` SQL은 채널·`once_per_user` 필터 유지(만료 필터는 서비스 도메인 술어가 담당 — dao 무변경, 투영만 확장).
- `UserView` 를 투영하는 user 조회 dao(들)에 `createdAt` 추가.

## 흐름

### 상점 목록 — `GetCoinShopService`

`@Transactional(readOnly = true)`:

1. `items = getCoinItemDao.findShopItems(userId, channel)` (채널·미구매 1회패키지).
2. `items.values` 에 `validDays != null` 이 없으면 `items` 그대로 반환(유저 조회 생략).
3. 있으면 `now = timeGenerator.now()`, `userCreatedAt = getUserByIdUseCase.getById(userId).createdAt` 로드 → `items.activeOffersAt(userCreatedAt, now)` 로 만료 오퍼 제거해 반환.

### 체크아웃 / 구매 확정 — `GetCoinCheckoutService.getCheckout(userId, itemId)`

1. `item = getCoinItemDao.findById(itemId)` — 없으면 `COIN_ITEM_NOT_FOUND`(404).
2. `item.validDays == null` 이면 유저 조회 없이 `item` 반환(상시 상품).
3. 기간 한정이면 `now`·`userCreatedAt` 로 `item.isOfferActiveAt(...)` — 만료면 `COIN_ITEM_OFFER_EXPIRED`(400), 활성이면 `item` 반환.

`CompleteCoinPurchaseService.complete`:
- 기존 흐름 그대로. 단 `getCheckout(command.itemId)` → `getCheckout(userId, command.itemId)`. 만료면 이 시점(PG confirm 전)에 400으로 중단 → 과금·적립 없음.

## 시드 (2배 이벤트 상품 예)

```sql
-- 신규 가입자에게 가입 후 7일간, 1회 한정 2배(200코인) PG 상품
INSERT INTO coin_items (coin_amount, price, sale_price, once_per_user, sale_channel,
                        store_product_id, valid_days, created_at, updated_at)
VALUES (200, 10000, 4900, 1, 'PG', NULL, 7, NOW(6), NOW(6));
```

- `valid_days = 7` → 각 유저 가입시각 + 7일까지. 유저마다 만료가 자동으로 다름.
- 기존 상시 상품은 `valid_days = NULL`(마이그레이션 후에도 기존 행은 NULL로 남아 그대로 동작).

## 영향 범위 (신규/수정)

- `oneulsogae-core`:
  - coin: `CoinItem`(+validDays·isOfferActiveAt·create), `CoinItems`(+activeOffersAt), `GetCoinShopService`(만료 필터), `GetCoinCheckoutUseCase`/`GetCoinCheckoutService`(userId 추가·만료 검증), `CoinErrorCode`(+COIN-005).
  - user: `UserView`(+createdAt).
  - payments: `CompleteCoinPurchaseService`(getCheckout 호출 시 userId 전달).
- `oneulsogae-infra`:
  - `CoinItemEntity`(+validDays), `GetCoinItemDaoImpl`(8-arg 투영), user 조회 dao(UserView createdAt 투영), `CoinItemEntityFixture`(validDays 인자).
- `oneulsogae-api`:
  - `PaymentsController`(체크아웃 조회에 `@LoginUser` userId 전달).
- 테스트:
  - 도메인 유닛: `CoinItem.isOfferActiveAt`(가입+N일 경계 전/후, validDays NULL 상시), `CoinItems.activeOffersAt`.
  - E2E: 상점(가입 오래된 유저 → 만료 오퍼 숨김, 신규 유저 → 노출), 체크아웃/구매 확정(만료 상품 → `COIN-005` 400; 유효 → 통과). 기존 코인 상점·구매 E2E 회귀 통과.
  - 픽스처: `CoinItemEntityFixture` 에 `validDays` 기본 null 추가, `UserEntityFixture` 가입시각(created_at) 제어 가능 여부 확인.

## 마이그레이션 (prod 수동 DDL)

```sql
ALTER TABLE coin_items ADD COLUMN valid_days INT NULL;
```

- 기존 행은 `valid_days = NULL`(상시) — 하위호환. 인덱스 불필요(상점은 소규모 테이블, 만료 필터는 서비스 메모리 계산).
- 2배 이벤트 상품 시드 INSERT(위)는 별도 실행.

(local은 ddl-auto=update로 컬럼 자동 추가.)
