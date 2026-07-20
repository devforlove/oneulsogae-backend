# gathering_products 도입 + 일정 가격의 product 이관 설계

- 날짜: 2026-07-16
- 상태: 승인됨

## 목적

일정(gathering_schedules)에 컬럼으로 흩어져 있던 가격 정보(남/녀 정상가·얼리버드 할인율·남/녀 할인가)를
**성별·티어별 상품 행(gathering_products)** 으로 이관한다. 한 행 = 한 가격(성별 × 타입)이며,
얼리버드 가격은 조회 시점 계산이 아니라 **일정 생성 시점에 계산해 확정 금액으로 저장**한다.
일정 생성 시 products가 함께 생성된다.

얼리버드 **선착순 수량(early_bird_capacity / early_bird_remaining)** 은 남/녀가 공유하는 카운터이므로
products로 옮기지 않고 gathering_schedules에 남긴다.

## 결정 사항 (Q&A로 확정)

| 쟁점 | 결정 |
|---|---|
| 성별 가격 | product에 `gender` 컬럼을 두고 남/녀 × 타입 조합으로 행 생성 |
| 기존 스케줄 가격 컬럼 | 제거(male_fee·female_fee·early_bird_discount_rate·discount_male_fee·discount_female_fee). 선착순 카운터는 유지 |
| 할인가(얼리버드 소진 후 가격) | `DISCOUNT` 타입으로 product에 포함 (타입 3종: NORMAL / EARLY_BIRD / DISCOUNT) |
| 어드민 생성 API 입력 | 기존 유지(정상가 남/녀 + 할인율/인원 + 할인가 남/녀). 서버가 product 행을 계산·생성 |
| product 생성 로직 위치 | `GatheringSchedule.create()` 도메인이 `GatheringProducts`(일급 컬렉션)를 구성. 서비스는 저장만 오케스트레이션 |
| 운영 DDL | 이번엔 코드만. 운영 반영 SQL은 본 문서에 기록해두고 별도 세션에서 적용 |

## 1. 공용 타입 (oneulsogae-common)

`common/gathering/GatheringProductType.kt`:

- `NORMAL` — 정가
- `EARLY_BIRD` — 얼리버드가(선착순 유효 시 적용)
- `DISCOUNT` — 얼리버드 소진 후 적용되는 할인가

## 2. 신규 엔티티 (oneulsogae-infra)

`GatheringProductEntity` → 테이블 `gathering_products`:

| 컬럼 | 타입 | 비고 |
|---|---|---|
| gathering_id | bigint not null | 소속 모임(비정규화) |
| schedule_id | bigint not null | 소속 일정 |
| gender | varchar(50) not null | MALE / FEMALE |
| type | varchar(50) not null | NORMAL / EARLY_BIRD / DISCOUNT |
| price | int not null | 계산 완료된 확정 금액(원). EARLY_BIRD = 정가 × (100 − 할인율) / 100 버림 |
| + BaseEntity | | created_at / updated_at / deleted_at, `@SQLRestriction("deleted_at is null")` |

인덱스: `idx_schedule_id (schedule_id)`. 조회는 전부 "일정의 products"(단건 또는 `in` 목록)라 이것으로 충분.
한 일정당 행 수는 2(정가만)~6(3티어 모두).

## 3. gathering_schedules 변경

- **제거**: male_fee, female_fee, early_bird_discount_rate, discount_male_fee, discount_female_fee
- **유지**: 남/녀 정원·여분, early_bird_capacity, early_bird_remaining, startAt/endAt, status
- 할인율(%)은 어디에도 저장하지 않는다. 버림 나눗셈 때문에 금액에서 율을 정확히 역산할 수 없으므로,
  어드민 상세 응답의 `earlyBirdDiscountRate`는 남/녀 얼리버드가(저장 금액)로 대체된다. (어드민 프론트 수정 필요)

## 4. 생성 흐름 (oneulsogae-admin)

- 요청/커맨드(`CreateGatheringScheduleRequest`/`CreateGatheringScheduleCommand`)는 기존 형태 유지.
- `GatheringSchedule.create()`가 기존 검증(시간 범위·얼리버드 세트·범위) 후
  `products: GatheringProducts`를 함께 구성한다:
  - MALE/FEMALE × NORMAL — 필수
  - MALE/FEMALE × EARLY_BIRD — 할인율이 있을 때. 가격 = 정가 × (100 − 율) / 100 (버림)
  - MALE/FEMALE × DISCOUNT — 할인가가 있을 때
- `GatheringProduct`(admin command 도메인) + `GatheringProducts` 일급 컬렉션(`withScheduleId(id)` 제공).
- 서비스: `saveGatheringSchedulePort.save(schedule)` → `saveGatheringProductPort.saveAll(saved.products.withScheduleId(saved.id))`.
  같은 `@Transactional`이라 원자적.
- infra: `GatheringProductAdapter` + `GatheringProductJpaRepository` + 매퍼 신설(엔티티당 어댑터 1개).

## 5. 읽기 경로 (core query / admin query)

- `GatheringScheduleView`: 가격 스칼라 제거, `products: List<GatheringProductView>(gender, type, price)` 보유.
  기존 공개 API(`feeFor`/`earlyBirdFeeFor`/`discountFeeFor`/`salePriceFor`/`soldOutFor`/`earlyBirdSoldOut`)
  시그니처는 유지하고 내부 구현만 products 탐색으로 교체.
  → offline 상세·체크아웃 응답(`GatheringDetailResponse`, `ProductResponse`)은 형태 불변, 유저 프론트 무영향.
- `GetGatheringDaoImpl`: 일정 조회 후 `schedule_id in (...)`으로 products 일괄 조회·그룹핑(N+1 없음).
- 어드민 `GetAdminGatheringDaoImpl` / `AdminGatheringScheduleView`도 동일 방식.
  `AdminGatheringDetailResponse.Schedule`은 `earlyBirdDiscountRate`(율%)를 없애고
  `earlyBirdMaleFee`/`earlyBirdFemaleFee`(저장 금액, 없으면 null)로 대체한다.
  나머지 필드명(maleFee/femaleFee/discountMaleFee/discountFemaleFee/earlyBirdCapacity/earlyBirdRemaining)은 유지하되
  값의 출처가 products로 바뀐다.

## 6. 참가(명령) 경로 (core command)

- `JoiningSchedule`: 가격 필드를 성별·티어별 저장 가격으로 교체
  (정가 남/녀 필수, 얼리버드가 남/녀 선택, 할인가 남/녀 선택).
- `register()`의 확정가 규칙은 동일: 얼리버드 유효 → 얼리버드가, 소진 & 할인가 존재 → 할인가, 그 외 정가.
  얼리버드가는 율 계산 대신 저장값을 읽는다. 여분 차감 로직 불변.
- `GatheringScheduleAdapter`의 `GetJoiningSchedulePort` 구현이 products를 함께 로드해 조립.

## 7. 테스트

- **유닛(Kotest)**: `GatheringSchedule.create()` — 티어 유무별 products 행 수·얼리버드가 버림 계산·기존 검증 유지.
  `JoiningSchedule` — 저장 가격 기반 확정가 티어 전환(기존 테스트 갱신).
- **E2E**: 어드민 일정 생성(products 생성 확인), 어드민 상세, offline 상세, 체크아웃, 결제완료 접수 — 픽스처와 함께 갱신.
- **픽스처**: `GatheringScheduleEntityFixture` 수정 + `GatheringProductEntityFixture` 신설.

## 8. 운영 DB 반영 SQL (이번 세션 미적용, 배포 전 순서대로 적용)

**배포 창 주의**: 백필(2~4단계) 실행 후 신 코드가 뜨기 전까지 구 코드로 생성된 일정은 products가 없어,
신 코드 전환 후 그 일정의 조회·접수가 실패한다("정가 상품이 없습니다"). 백필 SQL은 `not exists` 가드로
멱등이므로 **신 코드 배포·검증 직후 2~4단계를 한 번 더 실행**한다. (또는 배포 창 동안 어드민 일정 생성을 중단)

```sql
-- 1) 테이블 생성
create table gathering_products (
    id bigint not null auto_increment primary key,
    gathering_id bigint not null,
    schedule_id bigint not null,
    gender varchar(50) not null,
    type varchar(50) not null,
    price int not null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    deleted_at datetime(6) null,
    index idx_schedule_id (schedule_id)
);

-- 2) 기존 일정 백필 (NORMAL 남/녀) — not exists 가드로 멱등(재실행 안전)
insert into gathering_products (gathering_id, schedule_id, gender, type, price, created_at, updated_at)
select s.gathering_id, s.id, g.gender, 'NORMAL',
       case g.gender when 'MALE' then s.male_fee else s.female_fee end, now(6), now(6)
from gathering_schedules s
join (select 'MALE' as gender union all select 'FEMALE') g
where s.deleted_at is null
  and not exists (
    select 1 from gathering_products p
    where p.schedule_id = s.id and p.gender = g.gender and p.type = 'NORMAL' and p.deleted_at is null
  );

-- 3) 얼리버드 백필 (할인율 있는 일정) — 멱등
insert into gathering_products (gathering_id, schedule_id, gender, type, price, created_at, updated_at)
select s.gathering_id, s.id, g.gender, 'EARLY_BIRD',
       floor(case g.gender when 'MALE' then s.male_fee else s.female_fee end * (100 - s.early_bird_discount_rate) / 100),
       now(6), now(6)
from gathering_schedules s
join (select 'MALE' as gender union all select 'FEMALE') g
where s.deleted_at is null and s.early_bird_discount_rate is not null
  and not exists (
    select 1 from gathering_products p
    where p.schedule_id = s.id and p.gender = g.gender and p.type = 'EARLY_BIRD' and p.deleted_at is null
  );

-- 4) 할인가 백필 (할인가 있는 일정) — 멱등
insert into gathering_products (gathering_id, schedule_id, gender, type, price, created_at, updated_at)
select s.gathering_id, s.id, g.gender, 'DISCOUNT',
       case g.gender when 'MALE' then s.discount_male_fee else s.discount_female_fee end, now(6), now(6)
from gathering_schedules s
join (select 'MALE' as gender union all select 'FEMALE') g
where s.deleted_at is null
  and case g.gender when 'MALE' then s.discount_male_fee else s.discount_female_fee end is not null
  and not exists (
    select 1 from gathering_products p
    where p.schedule_id = s.id and p.gender = g.gender and p.type = 'DISCOUNT' and p.deleted_at is null
  );

-- 5) 신 코드 배포 전: NOT NULL 가격 컬럼을 null 허용으로 전환
--    (신 코드는 이 컬럼들 없이 INSERT하므로, 전환하지 않으면 컬럼 드롭 전까지 일정 생성이 NOT NULL 제약으로 실패한다)
alter table gathering_schedules
    modify male_fee int null,
    modify female_fee int null;

-- 6) 컬럼 드롭 (신 코드 배포·검증 + 2~4단계 재실행 후)
alter table gathering_schedules
    drop column male_fee,
    drop column female_fee,
    drop column early_bird_discount_rate,
    drop column discount_male_fee,
    drop column discount_female_fee;
```

## 프론트엔드 영향 (직접 수정하지 않고 안내)

- **유저(offline 상세·체크아웃)**: 응답 형태 불변 — 수정 불필요.
- **어드민 모임 상세**: `schedules[].maleFee/femaleFee/earlyBirdDiscountRate/discountMaleFee/discountFemaleFee`가
  성별·티어별 가격 구조로 바뀐다(할인율% 필드 소멸, 얼리버드는 금액으로 노출). 어드민 프론트의 상세 화면 매퍼 수정 필요.
- **어드민 일정 생성 폼**: 입력(정상가+할인율+할인가)은 그대로 — 수정 불필요.
