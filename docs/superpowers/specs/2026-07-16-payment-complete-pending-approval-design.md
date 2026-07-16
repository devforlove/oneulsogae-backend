# 결제완료 → 대기 등록 → 운영자 승인/거절 설계

- 날짜: 2026-07-16
- 상태: 승인됨

## 배경·목표

payments 도메인은 현재 조회 전용(`GET /payments/v1/checkout`)이다. 유저가 결제를 완료하면
`gathering_members`에 **승인대기(PENDING)** 로 등록하고, 운영자가 승인하면 **참가(JOINED)** 로
전환하는 결제 command 흐름을 추가한다.

핵심 결정(사용자 확인 완료):

- **무검증 접수**: PG 미연동 상태이므로 결제 수단 검증 없이 접수만 받는다. 결제 기록을 남기고
  대기 등록하는 뼈대를 구현한다.
- **참가 단위는 일정(schedule)**: `gathering_members`에 `schedule_id`를 추가하고 유니크 제약을
  `(schedule_id, user_id)`로 변경한다.
- **접수 시 검증 + PENDING 정원 포함**: 매진 차단·일정 상태 필터·성별 강제를 접수 시점에
  적용하고, 여분(remaining) 차감으로 PENDING도 정원에 포함한다. (체크아웃 구축 시 보류한
  검증의 재점검 — [[payments-checkout-scope]])
- **승인 + 거절 둘 다** 구현. 거절 시 여분을 복원한다.
- **admin 일정별 참가자 목록 조회 포함**.

## 1. 상태 모델

`GatheringMemberStatus`(meeple-common)에 두 상태 추가:

| 상태 | 설명 | 정원 포함 |
|---|---|---|
| `PENDING`(승인대기) | 결제완료 접수 직후 | 포함 |
| `JOINED`(참가) | 운영자 승인 후 (기존) | 포함 |
| `REJECTED`(거절) | 운영자 거절 | 제외(여분 복원) |
| `CANCELED`(참가취소) | 기존 유지 | 제외 |

- 상태 전이는 `PENDING → JOINED | REJECTED`만 허용한다. `PENDING`이 아닌 행에 승인/거절
  호출 시 에러.
- `REJECTED`/`CANCELED` 행이 있는 유저가 재접수하면 **기존 행을 PENDING으로 되살린다**
  (유니크 제약 때문에 새 행 insert 불가. soft delete 행도 DB 유니크에 걸린다).

## 2. 스키마 변경 (`gathering_members`)

- `schedule_id`(필수) 컬럼 추가 — 참가는 일정 단위.
- `gender`(필수) 컬럼 추가 — 거절/취소 시 어느 성별 여분 카운터를 복원할지 필요.
- `early_bird_applied`(필수, 기본 false) 컬럼 추가 — 접수 시 얼리버드 여분을 소진했는지 기록.
  거절 시 `early_bird_remaining` 복원 여부를 이 값으로 판단한다(payments 조회 없이 gathering
  도메인 안에서 복원 로직 완결).
- 유니크 제약 `(gathering_id, user_id)` → `(schedule_id, user_id)` 변경.
- prod `ddl-auto: validate`이므로 `docs/migration/gathering_members_schedule_id_gender.sql`
  마이그레이션 DDL을 추가하고 배포 전 실DB 반영한다.

## 3. 결제 기록 (`payments` 테이블 신규)

payments 도메인에 command 패키지를 신설한다.

`PaymentEntity`(meeple-infra `payments/command/entity`): `user_id`, `gathering_id`,
`schedule_id`, `gender`, `amount`(서버 확정 실결제가).

- 결제수단·PG 검증 필드는 이번 스코프에 없다(무검증 접수).
- payments에 상태 컬럼을 두지 않는다. **참가 상태의 원장은 `gathering_members.status` 하나**로
  유지한다(단순성 우선). 환불 관리가 필요해지는 시점에 결제 상태를 재설계한다.

## 4. 결제완료 API (유저)

`POST /payments/v1/complete` — body: `gatheringId`, `scheduleId`.

- `gender`는 요청으로 받지 않는다. **본인 `UserDetail` 성별을 서버가 조회해 강제**한다
  (체크아웃 때 보류한 성별 강제 적용). 성별 미확정(본인인증 전) 유저는 에러.

`CompletePaymentService`(payments command, `@Transactional`) 흐름:

1. `GetUserDetailUseCase`(user 도메인 in-port)로 본인 성별 확보.
2. gathering 도메인 command in-port **`RegisterGatheringMemberUseCase`** 호출 → 대기 등록 +
   서버 확정가 반환.
3. 확정가로 `payments` 기록 저장(자기 out-port `SavePaymentPort`).

`RegisterGatheringMemberService`(gathering command, meeple-core 신설) 규칙 — 검증은 도메인
모델에 캡슐화(서비스에 if-throw 나열 금지):

- 일정 존재 + `SCHEDULED` 상태 검증(보류했던 일정 상태 필터 적용).
- 해당 성별 여분 > 0 검증 후 차감. 얼리버드가 적용되면 `early_bird_remaining`도 차감.
  **schedule 행 `PESSIMISTIC_WRITE` 잠금**으로 동시 접수 경합을 차단한다(coin 패턴 준용).
- 중복 검증: 같은 (schedule, user)에 `PENDING`/`JOINED` 행 존재 시 에러.
  `REJECTED`/`CANCELED` 행은 PENDING으로 되살린다(status·gender·early_bird_applied 갱신,
  여분은 신규 접수와 동일하게 다시 차감).
- 확정가는 잠금 후 최신 여분 기준으로 계산한다(`salePriceFor` 티어 규칙:
  얼리버드 유효 → 얼리버드가, 소진 & 할인가 존재 → 할인가, 그 외 → 정가).
  체크아웃 표시가와 얼리버드 소진 시점이 어긋날 수 있으므로 서버 확정가가 원천이다.

## 5. 운영자 승인/거절/목록 API (admin)

기존 admin 패턴(서비스 `meeple-admin/gathering` + 컨트롤러 `meeple-api/api/admin`)을 따른다.

- `POST /admin/v1/gatherings/schedules/{scheduleId}/members/{memberId}/approve` —
  `PENDING → JOINED`.
- `POST /admin/v1/gatherings/schedules/{scheduleId}/members/{memberId}/reject` —
  `PENDING → REJECTED` + 해당 성별 여분 복원(얼리버드 차감분도 복원).
- `GET /admin/v1/gatherings/schedules/{scheduleId}/members` — 일정별 참가자 목록
  (상태·성별·결제금액·유저 정보 조인, query dao).

## 6. 에러 코드

- `PaymentsErrorCode`: 성별 미확정 등 결제 접수 불가 사유.
- `GatheringErrorCode`: 일정 없음/판매 불가 상태·매진·중복 참가·잘못된 상태 전이.

## 7. 테스트

- 도메인 유닛(Kotest): 상태 전이 규칙, 매진 판정·여분 차감/복원, 되살림 규칙, 확정가 계산.
- E2E(meeple-api): 결제완료 정상 접수(멤버 PENDING + 결제 기록 + 여분 차감), 매진 거부,
  중복 접수 거부, 승인 → JOINED, 거절 → REJECTED + 여분 복원, 거절 후 재접수.

## 스코프 외

- PG 연동·결제 검증, 환불·결제 상태 관리.
- 유저 측 참가취소 API(기존 `CANCELED`는 상태만 존재).
- 프론트엔드 변경 — 백엔드 완료 후 변경 지점을 별도 안내한다.
