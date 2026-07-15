# payments 체크아웃 API 확장 설계 — 상품 정보·결제수단 포함

- 날짜: 2026-07-15
- 상태: 승인됨
- 선행 스펙: `2026-07-15-payments-checkout-api-design.md` (v1: 주문자 정보만 반환, 구현 완료)

## 배경·범위 결정

v1 checkout은 주문자 정보만 반환하고 모임·금액은 offline API가 제공하기로 했으나, 사용자 요청으로 **상품(모임 일정) 정보와 결제수단 목록을 checkout 응답에 포함**하도록 확장한다.

- **결제수단**: DB 참조 테이블 신설(`payment_methods`, image_template 패턴). 배포 없이 수단 on/off.
- **금액 형태**: 정산형 — 정가(`price`) + 서버 확정 실결제가(`salePrice`). 할인액은 프론트가 `price - salePrice`로 계산.
- **매진 처리**: 없는 일정만 404, 매진은 `soldOut` 플래그로 200 반환(차단은 향후 결제 command 책임).

## API 스펙 (변경)

**`GET /payments/v1/checkout?gatheringId={id}&scheduleId={id}&gender={MALE|FEMALE}`** — 인증 필수, 파라미터 3개 필수. (v1은 무파라미터였으나 프론트 미연동 상태라 호환성 문제 없음)

```json
{
  "success": true,
  "data": {
    "orderer": { "name": "김미플", "email": "orderer@test.com", "phoneNumber": "01012345678" },
    "product": {
      "gatheringId": 1, "scheduleId": 3, "gender": "MALE",
      "title": "보드게임 모임", "imageUrl": "https://…(presigned)", "region": "서울 강남구",
      "startAt": "2026-08-01T19:00:00",
      "price": 10000, "salePrice": 7000, "soldOut": false
    },
    "paymentMethods": [ { "code": "BANK_TRANSFER", "name": "무통장입금" } ]
  }
}
```

- **orderer**: v1과 동일(전 필드 nullable).
- **product**:
  - `price` = 해당 성별 정가(`maleFee`/`femaleFee`).
  - `salePrice` = 서버 확정 실결제가. 티어 규칙(offline 상세와 동일 계산 공유): 얼리버드 티어 존재+미소진 → `정가 × (100 - 할인율) / 100`(버림) / 얼리버드 소진 & 할인가 존재 → 할인가 / 그 외 → 정가.
  - `soldOut` = 해당 성별 잔여(`maleRemaining`/`femaleRemaining`) ≤ 0. 매진이어도 200.
  - `imageUrl` = presigned URL(모임 이미지 없으면 null).
- **paymentMethods**: `active = true`인 수단만 `display_order asc, id asc` 순.
- **404**:
  - 모임이 없거나 모집중(RECRUITING)이 아님 → 기존 `GATHERING-001` 그대로 전파.
  - `scheduleId`가 그 모임의 일정과 매칭 안 됨 → 신규 `PAYMENTS-001`(`PaymentsErrorCode.CHECKOUT_PRODUCT_NOT_FOUND`).
- 요청 `gender`가 본인 성별과 달라도 조회는 허용(조회 전용, 차단은 향후 결제 command 책임).

## 결제수단 테이블

```sql
CREATE TABLE payment_methods (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  code          VARCHAR(50)  NOT NULL,
  name          VARCHAR(100) NOT NULL,
  display_order INT          NOT NULL,
  active        BOOLEAN      NOT NULL,
  created_at    DATETIME     NOT NULL,
  updated_at    DATETIME     NOT NULL,
  deleted_at    DATETIME     NULL,
  CONSTRAINT ux_payment_method_code UNIQUE (code)
);
```

- `PaymentMethodEntity`(infra `payments/command/entity`, `BaseEntity` 상속 + `@SQLRestriction`). **앱은 읽기 전용, 행은 DB에서 직접 관리**(image_template 패턴).
- 서버는 code에 로직을 두지 않으므로 enum을 만들지 않고 String으로 유지한다.
- 운영 초기 행: `(BANK_TRANSFER, 무통장입금, 1, true)`, `(CARD, 카드, 2, false)`, `(KAKAO_PAY, 카카오페이, 3, false)` — 활성 여부는 운영 판단으로 조정.

## 아키텍처

- **주문자 + 결제수단(자기 도메인)**: `GetCheckoutService`가 dao 2개(`GetCheckoutOrdererDao`, `GetPaymentMethodDao`)를 주입받아 `CheckoutView(orderer, paymentMethods)` 반환. `PaymentMethodViews`는 일급 컬렉션(coin `CoinItems`와 대칭).
- **상품(타 도메인)**: CQRS 규칙상 payments query 서비스는 자기 dao에만 의존하므로, **`PaymentsController`가 `GetGatheringsUseCase`(gathering in-port)를 함께 주입해 컨트롤러에서 조합**한다(`OfflineGatheringController`의 유스케이스 2개 조합 선례와 동일).
- **금액 티어 계산 공유**: 현재 `GatheringDetailResponse.Schedule.of`(offline api)에 인라인된 얼리버드/할인가 계산을 core read model `GatheringScheduleView`의 메서드로 캡슐화한다 — `feeFor(gender)`, `earlyBirdFeeFor(gender)`, `discountFeeFor(gender)`, `salePriceFor(gender)`, `soldOutFor(gender)`, `earlyBirdSoldOut`. offline 응답과 checkout 응답이 같은 계산을 공유해 이중 투영 분기를 방지한다(도메인 로직 캡슐화 원칙). offline 쪽은 기존 E2E로 회귀 검증. 이때 `GatheringDetailResponse.Schedule`의 낡은 KDoc(얼리버드 소진 시 "fee null" — 실제는 fee 유지)도 실동작에 맞게 정정한다.
- **일정 매칭 캡슐화**: `GatheringDetailView.scheduleOrNull(scheduleId)` 메서드 추가(컨트롤러 인라인 순회 금지 원칙).
- 신규 인덱스 불필요: `payment_methods`는 전행 조회(참조 데이터, 행 수 극소), gathering 조회는 기존 경로 재사용.

## 테스트

E2E(`PaymentsCheckoutE2ETest`) 재작성 — 전 케이스가 파라미터 3개를 사용:
1. 정상 조회: 본인인증 유저 + 모집중 모임 + 얼리버드 유효 일정 + 결제수단(활성 2·비활성 1) → orderer 3필드 + product(제목·presigned 이미지·지역·시각·price 10000·salePrice 7000·soldOut false) + 활성 수단만 순서대로 2건.
2. 얼리버드 소진 → `salePrice` = 할인가.
3. 얼리버드 티어 없음 → `salePrice` = `price`.
4. 해당 성별 정원 소진 → `soldOut` true, 200.
5. 없는 scheduleId → 404 `PAYMENTS-001`.
6. 모집중 아닌 모임 → 404 `GATHERING-001`.
7. 프로필·본인인증 없는 유저 → orderer 전 필드 null(상품·수단은 정상).
8. 비인증 → 401.

offline 회귀: `OfflineGatheringDetailE2ETest` 기존 케이스가 리팩토링 후에도 GREEN이어야 한다.

픽스처: `PaymentMethodEntityFixture` 신설. `GatheringEntityFixture`/`GatheringScheduleEntityFixture` 재사용.

## 커밋 분리

1. `refactor(gathering): 일정 금액 티어 계산을 read model 메서드로 캡슐화` — GatheringScheduleView 메서드 + GatheringDetailView.scheduleOrNull + offline 응답 리팩토링(동작 불변).
2. `feat(payments): 체크아웃 응답에 상품 정보·결제수단 추가` — 나머지 전부.

## 프론트엔드 변경 안내 (meeple-frontend는 수정하지 않음)

- 결제 화면의 `useGathering` 호출을 checkout 한 번으로 대체 가능(제목·이미지·지역·일시·금액 포함). 주문요약 할인액 = `price - salePrice`.
- `PaymentMethodSelect`의 하드코딩 라디오(무통장/카드/카카오페이)를 `paymentMethods` 응답 기반 렌더링으로 전환(코드 계약: `BANK_TRANSFER`/`CARD`/`KAKAO_PAY`).
- 매진 일정은 `product.soldOut`으로 판단(백엔드는 조회를 막지 않음).
