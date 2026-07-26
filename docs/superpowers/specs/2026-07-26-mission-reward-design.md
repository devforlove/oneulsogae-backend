# 확장 가능한 미션(리워드) 시스템 설계

## 배경 / 목표

앱에 **미션 → 코인 보상** 시스템을 추가한다. 첫 미션은 "자기소개를 100자 이상 작성하면 50코인". 단발성이 아니라 **유형만 늘리면 확장되는** 미션 엔티티를 만든다.

확정 방향(브레인스토밍):

- **목록 + 받기(claim)**: 사용자가 미션 목록·상태를 보고, 자격이 되면 보상을 수령한다. 자격은 claim 시점에 서버가 실제 상태(자기소개 길이 등)로 재검증한다(클라이언트 표시와 무관 — 어뷰징 차단).
- **DB `missions` 테이블에 정의**: 보상 코인·문구·노출 on/off는 운영이 배포 없이 조정. **자격 판정 로직은 코드(유형별 평가자)** — 임계값이 유형마다 이질적이라 DB 일반 컬럼으로 빼지 않는다.
- **회원당 1회**: 모든 미션 1회 완료·1회 보상. `UNIQUE(user_id, mission_id)` 가드로 강제(직전 도입한 `coin_item_purchases` 원자 가드 패턴 재활용).

비목표: 반복/일일 미션, 어드민 미션 CRUD, 진행률(0~100%) 세부 표시, 프론트·모바일 UI(백엔드 확정 후 안내).

## 결정: 자격 판정은 코드, 보상·표시는 DB

미션 유형마다 자격 조건이 제각각이다(자기소개 길이, 사진 수, 추천 수…). 하나의 DB 파라미터 컬럼으로 일반화되지 않는다.

→ **DB `missions`** = 보상 코인 + 제목·설명 + 활성/노출. **코드(유형별 `MissionEvaluator`)** = 자격 판정. 신규 미션 = `missions` 행 1개 + (조건이 코드가 필요하면) 평가자 1개 추가. 임계값(예: 100자)은 그 유형 평가자의 상수다.

## 데이터 모델

### `missions` (신설) — 미션 정의

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| `id` | PK | |
| `type` | `varchar(50)`, **unique** | `MissionType` enum name. 유형별 평가자를 고르는 자연키. |
| `reward_coin` | `int not null` | 완료 시 지급 코인. |
| `title` | `varchar(100) not null` | 목록 제목. |
| `description` | `varchar(255) null` | 목록 설명. |
| `active` | `boolean not null default true` | 노출·수령 가능 여부. |
| `display_order` | `int not null default 0` | 목록 정렬(오름차순). |

- BaseEntity(created_at·updated_at·deleted_at), `@SQLRestriction("deleted_at is null")`.
- `type` UNIQUE — 한 유형당 한 미션.

### `mission_completions` (신설) — 1회 완료 가드

| 컬럼 | 설명 |
| --- | --- |
| `id` | PK |
| `user_id` | 완료자 |
| `mission_id` | 완료한 미션 |
| `rewarded_coin` | 지급 코인 스냅샷(정의가 나중에 바뀌어도 이력 보존) |

- BaseEntity, **`UNIQUE(user_id, mission_id)`**. 완료 시 INSERT. 유니크 위반이 이중 수령을 원자적으로 막는 최종 방어선.

## 도메인 / 포트

### common
- **`MissionType`** enum(`common/mission`) — `WRITE_INTRODUCTION`. 신규 미션은 값 추가로 확장.
- **`CoinGetType.MISSION("미션 보상")`** 추가(기존 enum).

### mission 도메인 (core, 신규)

CQRS 분리: `mission/{command, query}`. **미션 정의는 조회 대상**이라 read model로 두고, 명령(claim)도 이를 query in-port로 로드한다 — coin 선례(`CoinItem`은 query dto, `CompleteCoinPurchaseService`가 `GetCoinCheckoutUseCase` query in-port로 로드)와 동일. 명령 도메인은 가드(`MissionCompletion`)만 소유한다.

**query**
- read model `Mission`(정의 — id·type·rewardCoin·title·description·displayOrder), `MissionView`(Mission + `completed: Boolean` + `eligible: Boolean`), `MissionViews`.
- in-port `GetMissionUseCase.getById(missionId): Mission`(활성만, 없으면 `MISSION_NOT_FOUND`) — claim이 미션 정의를 로드하는 데 쓴다.
- in-port `GetMissionsUseCase.getMissions(userId): MissionViews`.
- 구현 `GetMissionService`(단건), `GetMissionsService`(목록) 둘 다 `@Transactional(readOnly = true)`.
- dao `GetMissionDao.findActiveById(missionId): Mission?`·`findActiveMissions(): List<Mission>`(display_order 오름차순), `GetMissionCompletionDao.findCompletedMissionIds(userId): Set<Long>`.

**command**
- 도메인 모델 `MissionCompletion`(가드).
- in-port `ClaimMissionUseCase.claim(userId, missionId): ClaimMissionResult`(rewardedCoin·balance).
- 구현 `ClaimMissionService`(`@Transactional`).
- out-port `SaveMissionCompletionPort.save(userId, missionId, rewardedCoin)`(UNIQUE 위반 → DataIntegrityViolationException).

**평가자 (command/query 공용, `mission/application/evaluator`)**
- `MissionEvaluator { fun supports(type: MissionType): Boolean; fun isEligible(userId: Long): Boolean }`, 구현 `WriteIntroductionMissionEvaluator`(user in-port로 소개 조회, 길이 ≥ 100).
- `MissionEvaluators`(resolver, `@Component`) — 주입된 `List<MissionEvaluator>`에서 `supports(type)`인 것을 고른다. **없으면 `IllegalStateException`**(missions 행에 대응 평가자가 없는 배포 오류 — 조용히 부적격 처리하지 않는다). claim·목록 둘 다 이 resolver로 판정.

### 도메인 간 참조
- 평가자·claim은 코인 적립을 coin **in-port** `AcquireCoinUseCase`로, 소개 조회를 user **in-port** `GetUserDetailUseCase`로 한다(타 도메인 out-port·구현 직접 참조 금지).

### 에러코드 `MissionErrorCode`
- `MISSION_NOT_FOUND("MISSION-001", 404)` — 없거나 비활성.
- `MISSION_NOT_ELIGIBLE("MISSION-002", 400)` — 자격 미충족(예: 소개 100자 미만).
- `MISSION_ALREADY_COMPLETED("MISSION-003", 409)` — 이미 수령.

  (비활성은 목록에서 빠지므로 별도 코드 없이 `MISSION_NOT_FOUND`로 합친다 — findById가 활성만 반환.)

## 받기(claim) 흐름 — `ClaimMissionService`

`@Transactional`:

1. `getMissionUseCase.getById(missionId)` → 없거나 비활성이면 `MISSION_NOT_FOUND`.
2. `missionEvaluators.resolve(mission.type).isEligible(userId)` 아니면 `MISSION_NOT_ELIGIBLE`.
3. **원자 적립+가드**:
   - `acquireCoinUseCase.acquire(userId, AcquireCoinCommand(mission.rewardCoin, CoinGetType.MISSION))` — 적립.
   - `saveMissionCompletionPort.save(userId, missionId, mission.rewardCoin)` — 가드 INSERT(saveAndFlush). 선완료 경합으로 유니크 위반이면 `DataIntegrityViolationException` → catch → `MISSION_ALREADY_COMPLETED`. 같은 트랜잭션이라 적립까지 롤백(이중 지급 원천 차단).
4. `ClaimMissionResult(rewardedCoin = mission.rewardCoin, balance = 적립 후 잔액)`.

(coin 패키지의 `AcquirePurchasedCoinService`와 동형 — 가드 테이블만 mission_completions.)

## 목록 흐름 — `GetMissionsService`

`@Transactional(readOnly = true)`:

1. `getMissionDao.findActiveMissions()`(display_order 오름차순).
2. `getMissionCompletionDao.findCompletedMissionIds(userId)`.
3. 각 미션: `completed = id in 완료집합`; `eligible = if (completed) false else missionEvaluators.resolve(type).isEligible(userId)`.
4. `MissionView`로 투영.

## API (신규 컨트롤러 `MissionController`, `/missions/v1`)

- **`GET /missions/v1`** — `@LoginUser`. 활성 미션 목록 + 사용자별 completed·eligible. 응답 항목: `missionId·type·title·description·rewardCoin·completed·eligible`.
- **`POST /missions/v1/{missionId}/claim`** — `@LoginUser`. 보상 수령. 응답 `rewardedCoin·balance`. 오류: 400(MISSION-002)·404(MISSION-001)·409(MISSION-003).
- `/missions/v1/**`는 SecurityConfig permitAll 대상 아님 → 인증 필수(coin과 동일).

## 시드 (첫 미션)

```sql
INSERT INTO missions (type, reward_coin, title, description, active, display_order, created_at, updated_at)
VALUES ('WRITE_INTRODUCTION', 50, '자기소개 작성', '자기소개를 100자 이상 작성하고 50코인을 받으세요', 1, 0, NOW(6), NOW(6));
```

## 영향 범위 (신규/수정)

- `oneulsogae-common`: 신규 `common/mission/MissionType.kt`; 수정 `common/coin/CoinGetType.kt`(MISSION 추가).
- `oneulsogae-core` (mission 도메인 신규):
  - query: `query/dto/Mission.kt`·`MissionView.kt`·`MissionViews.kt`; `query/service/port/in/GetMissionUseCase.kt`·`GetMissionsUseCase.kt`; `query/service/GetMissionService.kt`·`GetMissionsService.kt`; `query/dao/GetMissionDao.kt`·`GetMissionCompletionDao.kt`.
  - command: `command/domain/MissionCompletion.kt`; `command/application/port/in/ClaimMissionUseCase.kt`(+`result/ClaimMissionResult.kt`); `command/application/ClaimMissionService.kt`; `command/application/port/out/SaveMissionCompletionPort.kt`.
  - 평가자: `application/evaluator/MissionEvaluator.kt`·`WriteIntroductionMissionEvaluator.kt`·`MissionEvaluators.kt`(resolver).
  - `mission/MissionErrorCode.kt`.
- `oneulsogae-infra`:
  - `MissionEntity`·repository; query `GetMissionDaoImpl`(findActiveById·findActiveMissions).
  - `MissionCompletionEntity`(UNIQUE user+mission)·repository; command `MissionCompletionAdapter`(SaveMissionCompletionPort, saveAndFlush); query `GetMissionCompletionDaoImpl`(findCompletedMissionIds).
- `oneulsogae-api`: `mission/MissionController.kt`; `mission/response/MissionResponse.kt`·`ClaimMissionResponse.kt`.
- 테스트:
  - 도메인 유닛: `WriteIntroductionMissionEvaluator`(소개 길이 주입 — 99자 부적격, 100자 적격), `Mission`(활성 판정).
  - E2E: 목록(소개<100 eligible=false, ≥100 true, 완료 후 completed=true); claim(적격→50코인+완료행, 재claim 409, 부적격 400, 없는 미션 404).
  - 픽스처: `MissionEntityFixture`.

## 마이그레이션 (prod 수동 DDL)

- `create table missions (... unique key ux_missions_type (type) ...)`
- `create table mission_completions (... unique key ux_mission_completions_user_mission (user_id, mission_id) ...)`
- 첫 미션 시드 INSERT(위).

(local은 ddl-auto=update로 자동 생성. 시드 INSERT는 별도 실행.)
