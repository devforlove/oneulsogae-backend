# 이상형 설정(Ideal Type) 저장·조회 API 설계

작성일: 2026-07-01
도메인: `user`

## 1. 배경 / 목표

프론트엔드(`meeple-frontend`)에 **이상형 설정** 화면이 이미 완성돼 있으나, 현재는 값이 브라우저 로컬(zustand persist)에만 저장된다. 서버 저장/조회 API가 없어 기기·재설치 간 유지가 되지 않는다.

**목표(이번 범위):** 이상형 값을 서버에 영속화하는 **CRUD API(조회 1 + 저장 1)** 를 `user` 도메인에 추가한다. 프론트가 로컬 저장을 서버 저장으로 교체할 수 있게 한다.

**비목표(이번 범위 밖 — 후속 과제):**
- 이상형 값을 **실제 매칭 후보 선정에 반영**하는 작업. (현재 1:1 매칭은 반대 성별 + 근접 지역 순회 + 최근 로그인 + 이력 제외로 후보를 고르며, 이상형 필터는 아직 개입하지 않는다. → 후속 과제)
- 다만 **"나중 매칭에서 바로 쓰기 좋은 형태"로 저장**하는 것은 이번 범위에 포함한다(§3).

## 2. 프론트 계약 및 확정 사항

프론트 `IdealType`(`domains/home/domain/entities/IdealType.ts`) 필드와 현재 전송 형태:

| 프론트 필드 | 프론트 현재 타입/값 | 백엔드 매핑 |
|---|---|---|
| `ageRange` | `[number,number] \| null` (20~60) | `age_min`,`age_max` (Int?) |
| `heightRange` | `[number,number] \| null` (150~195) | `height_min`,`height_max` (Int?) |
| `maritalStatus` | 문자열(현재 한글 `description`) | `MaritalStatus?` enum |
| `smoking` | 문자열(현재 한글) | `SmokingStatus?` enum |
| `drinking` | 문자열(현재 한글) | `DrinkingStatus?` enum |
| `religion` | 문자열(현재 한글) | `Religion?` enum |
| `distance` | 문자열(한글: 같은 지역만/인접 지역까지/상관없음) | `DistancePreference?` enum (신규) |

**확정된 계약 결정(사용자 승인):**
1. **범위: CRUD만.** 매칭 연동은 후속 과제로 분리.
2. **값 계약: enum name으로 통일.** 백엔드는 `SINGLE`/`NON_SMOKER` 같은 **enum name**을 받고 내려준다(프로필 편집 `PUT /users/v1/profile`과 동일 컨벤션). Jackson 기본 동작으로 enum name이 그대로 역/직렬화된다.
3. **`null` = "상관없음"(필터 없음)으로 전 항목 통일.** 별도 센티넬 enum을 두지 않는다. 나이/키는 둘 다 `null`이면 상관없음.
4. **와이어 형태 유지.** 필드명(`smoking`/`drinking` 등)과 배열형(`ageRange`/`heightRange`)을 프론트 `IdealType`와 동일하게 맞춰 프론트 변경을 최소화한다. 백엔드 내부에서 `ageRange` ↔ `age_min/age_max`로 변환한다.

## 3. "매칭에 효율적인 형태"로 저장 (이번 범위 포함)

후속 매칭 쿼리가 후보(candidate) 속성과 **직접 비교 가능한 이산 값**으로 저장한다.

- **나이/키: 숫자 경계(`age_min/age_max`, `height_min/height_max`)로 저장.**
  - 매칭 시 나이는 후보 `birthday`로 비교되므로, 후속 매칭 쿼리는 이상형의 `age_min/age_max`를 기준일 기준 생년월일 범위로 변환해
    `candidate.birthday BETWEEN (기준일 - age_max년) AND (기준일 - age_min년)` 형태의 **sargable(birthday 인덱스 seek)** 조건으로 쓴다.
  - 나이는 시점에 따라 변하는 값이므로 저장 시 생년월일로 굳히지 않고 **사용자가 고른 나이(의도)** 그대로 보관한다.
- **결혼/흡연/음주/종교: 기존 `oneulsogae-common` enum 재사용**(`MaritalStatus`,`SmokingStatus`,`DrinkingStatus`,`Religion`). 후보의 동명 컬럼과 `=`로 바로 비교된다. `null`이면 해당 조건 미적용.
- **거리: `DistancePreference` enum 저장.** 현재 매칭은 "근접 지역 순서 순회"(geographic proximity, `2026-06-23-distance-based-1on1-matching-design.md`)를 쓴다. 이상형 `distance`는 그 순회 깊이를 제한하는 선호로 매핑된다.
  - `SAME_REGION` → 같은 `regionId`만(근접 순위 0)
  - `ADJACENT_REGION` → 같은 + 인접 지역까지
  - `null`(상관없음) → 근접 순회 제한 없음
  - 후속 매칭이 이 enum으로 순회 깊이를 결정한다. 이번 범위에서는 **저장만** 한다.

이상형 테이블 자체는 **탐색하는 본인의 단일 행**(`user_id` 유니크)만 로드되므로, 매칭 성능의 관건은 후보 쪽 컬럼/인덱스(이미 존재하는 `birthday`/`gender`/`region_id` 인덱스)이지 이상형 테이블 인덱스가 아니다. 따라서 이상형 테이블에는 `user_id` 유니크 인덱스만 둔다.

## 4. 데이터 모델

### 4.1 신규 enum (`oneulsogae-common`)

```kotlin
// oneulsogae-common/.../common/user/DistancePreference.kt
enum class DistancePreference(val description: String) {
    SAME_REGION("같은 지역만"),
    ADJACENT_REGION("인접 지역까지"),
    // null = 상관없음 (별도 값 두지 않음)
}
```

### 4.2 신규 테이블 `user_ideal_types` (users와 1:1)

| 컬럼 | 타입 | null=상관없음 | 비고 |
|---|---|:---:|---|
| `id` | BIGINT PK | — | `BaseEntity` |
| `user_id` | BIGINT, unique | — | 유니크 인덱스 `ux_user_ideal_type_user_id` |
| `age_min` | INT? | ✅(둘 다 null) | 20~60 |
| `age_max` | INT? | ✅ | 20~60, `age_min ≤ age_max` |
| `height_min` | INT? | ✅(둘 다 null) | 150~195 |
| `height_max` | INT? | ✅ | 150~195, `height_min ≤ height_max` |
| `marital_status` | varchar(50)? enum | ✅ | `MaritalStatus` |
| `smoking_status` | varchar(50)? enum | ✅ | `SmokingStatus` |
| `drinking_status` | varchar(50)? enum | ✅ | `DrinkingStatus` |
| `religion` | varchar(50)? enum | ✅ | `Religion` |
| `distance` | varchar(50)? enum | ✅ | `DistancePreference` |

`BaseEntity`(생성/수정시각, soft delete) 상속은 기존 엔티티(`UserDetailEntity`)와 동일하게 따른다.

### 4.3 DB 마이그레이션

- `ddl-auto: update` 환경이므로 신규 테이블은 자동 생성되나, 기존 관례대로 `docs/migration/`에 `user_ideal_types` 생성 + `ux_user_ideal_type_user_id` 유니크 인덱스 SQL을 명시 추가한다.

## 5. 도메인 모델 (`user` command)

`oneulsogae-core/.../user/command/domain/UserIdealType.kt`

```kotlin
data class UserIdealType(
    val id: Long = 0,
    val userId: Long,
    val ageMin: Int? = null,
    val ageMax: Int? = null,
    val heightMin: Int? = null,
    val heightMax: Int? = null,
    val maritalStatus: MaritalStatus? = null,
    val smokingStatus: SmokingStatus? = null,
    val drinkingStatus: DrinkingStatus? = null,
    val religion: Religion? = null,
    val distance: DistancePreference? = null,
)
```

**검증(도메인에 캡슐화 — `validateIdealType()`, 서비스에 `if…throw` 나열 금지):**
- 나이/키 범위는 **둘 다 존재하거나 둘 다 null**(한쪽만 있으면 예외).
- `ageMin ≤ ageMax`(20~60), `heightMin ≤ heightMax`(150~195). 범위 위반 시 `UserErrorCode`의 신규 코드로 `BusinessException`.
- enum 항목은 null 허용(상관없음).
- 생성/교체 경로 모두 검증을 통과한다.

**팩토리/교체:**
- `UserIdealType.of(userId, …)` — 신규 생성 시 검증.
- `update(…)` — 기존 값(id 보존)에 새 값 반영 후 검증. (upsert의 갱신 경로)

시각(`now`)은 이 도메인에 불필요하다(나이 저장은 정수 경계라 기준일 무관). `TimeGenerator` 주입 없음.

## 6. 헥사고날 구성 (`user` 도메인)

### 6.1 Command (저장/upsert)
- **UseCase(in-port):** `command/application/port/in/SaveIdealTypeUseCase` — `fun save(userId: Long, command: SaveIdealTypeCommand): UserIdealType`
  - `command/application/port/in/command/SaveIdealTypeCommand` (nullable 필드 묶음)
- **Service:** `command/application/SaveIdealTypeService`(`@Transactional`)
  - 주입: `GetIdealTypePort`(upsert용 기존 로드), `SaveIdealTypePort`
  - 흐름: `existing = getIdealTypePort.findByUserId(userId)`; `existing?.update(...) ?: UserIdealType.of(userId, ...)`; `saveIdealTypePort.save(domain)`.
  - (매칭 읽기 모델과 무관하므로 이벤트 발행 없음 — 프로필과 달리 `UserProfileChanged` 미발행)
- **out-port:**
  - `command/application/port/out/GetIdealTypePort` — `fun findByUserId(userId: Long): UserIdealType?`
  - `command/application/port/out/SaveIdealTypePort` — `fun save(idealType: UserIdealType): UserIdealType`
- **domain:** `command/domain/UserIdealType`

CQRS 관례상 command의 단건 로드(`GetIdealTypePort`)와 query의 조회 dao(`GetIdealTypeDao`)는 **공유하지 않고 각자 둔다**(coin `GetCoinBalancePort` ↔ `CoinBalanceDao` 선례와 동일).

### 6.2 Query (조회)
- **UseCase(in-port):** `query/service/port/in/GetIdealTypeUseCase` — `fun findByUserId(userId: Long): IdealTypeView?`
  - 미설정 사용자도 화면 진입이 가능해야 하므로 **없으면 null**(예외 아님). 컨트롤러가 null이면 "전 필드 null" 기본 응답을 만든다.
- **Service:** `query/service/GetIdealTypeService`(`@Transactional(readOnly = true)`) — `GetIdealTypeDao`에만 의존.
- **dao(포트):** `query/dao/GetIdealTypeDao` — `fun findByUserId(userId: Long): IdealTypeView?`
- **dto(read model):** `query/dto/IdealTypeView` (도메인과 같은 필드 구성)

### 6.3 Infra
- `infra/user/command/entity/UserIdealTypeEntity`(`@Entity`, `@Table(name="user_ideal_types", indexes=[unique user_id])`, `@Enumerated(STRING)`)
- `infra/user/command/mapper/UserIdealTypeMapper`(`toDomain()`/`toEntity()`)
- `infra/user/command/repository/UserIdealTypeJpaRepository`(`findByUserId`)
- `infra/user/command/adapter/UserIdealTypeCoreAdapter` — `GetIdealTypePort`,`SaveIdealTypePort` 구현 (upsert 시 기존 엔티티를 로드해 값 갱신 후 save — id/생성시각 보존)
- `infra/user/query/GetIdealTypeDaoImpl` — Spring Data 파생 쿼리(`findByUserId`)로 충분(조인 없음). `IdealTypeView`로 투영.

### 6.4 API (`oneulsogae-api`)
- **Controller:** `api/user/IdealTypeController` (`@RequestMapping("/users/v1/ideal-type")`)
  - `GET` — `@LoginUser` → `getIdealTypeUseCase.findByUserId(user.id)` → `IdealTypeResponse.of(view)` 또는 미설정 시 `IdealTypeResponse.empty()`
  - `PUT` — `@Valid @RequestBody SaveIdealTypeRequest` → `saveIdealTypeUseCase.save(user.id, request.toCommand())` → `IdealTypeResponse.of(domain)`
- **Request:** `api/user/request/SaveIdealTypeRequest`
  - `ageRange: List<Int>?`, `heightRange: List<Int>?`(각각 null 또는 정확히 2요소), enum 필드는 nullable enum 타입으로 직접 받음(`smoking: SmokingStatus?` 등). `toCommand()`에서 `ageRange?.get(0)`/`get(1)`로 분해.
  - 배열 크기(정확히 2)·정수 범위 등 표면 검증은 요청에서, 도메인 규칙(min ≤ max, 짝 존재)은 도메인 `validateIdealType()`에서. (검증 책임 이중화 최소화: 배열 shape만 요청에서, 값 규칙은 도메인에서)
- **Response:** `api/user/response/IdealTypeResponse`
  - 필드: `ageRange: List<Int>?`, `heightRange: List<Int>?`, `maritalStatus: MaritalStatus?`, `smoking: SmokingStatus?`, `drinking: DrinkingStatus?`, `religion: Religion?`, `distance: DistancePreference?`
  - `of(view)` / `of(domain)` / `empty()`(전 필드 null) 팩토리. `age_min/age_max`를 `ageRange = listOf(min,max)`로 조립(둘 중 하나라도 null이면 `ageRange = null`).
  - 응답 래핑은 기존 관례대로 `ApiResponse.success(...)`.

### 6.5 에러 코드
- `UserErrorCode`에 이상형 범위 검증용 코드 추가(예: `INVALID_IDEAL_TYPE_RANGE`). 기존 enum 컨벤션을 따른다.

## 7. 테스트 전략

- **도메인 유닛(Kotest):** `UserIdealType`
  - 정상 생성/교체(전 필드 null 허용).
  - 범위 위반(`ageMin > ageMax`, 한쪽만 존재, 경계 밖) → 예외.
  - `update()`가 id/userId 보존.
- **E2E(`oneulsogae-api`, `AbstractIntegrationSupport` + 픽스처 + `RestAssuredDsl`):**
  - 미설정 사용자 `GET` → 200, 전 필드 null.
  - `PUT` 후 `GET` 왕복 → 저장값 그대로 반환(enum name·배열 형태 확인).
  - `PUT` 재호출(값 변경) → upsert로 갱신(행 1개 유지).
  - 잘못된 범위(`ageRange=[40,20]`) → 검증 실패 응답.
  - 인증 없는 요청 → 401.

## 8. 프론트엔드 대응 필요 (백엔드에서 직접 수정하지 않음 — 안내)

enum name 통일 계약에 맞추려면 `meeple-frontend`에서 아래 변경이 필요하다. (리포지토리 경계 규칙상 백엔드 세션에서 프론트를 고치지 않는다)

1. **`IdealTypePage.tsx`의 `choices()`**: `o.description` 대신 `o.name`(enum name)을 값으로 사용하고, 라디오에는 `{name,description}` 옵션 객체를 그대로 넘겨 화면 라벨은 한글로 표시한다(프로필 편집 `ProfileEditSheet` 방식과 동일).
2. **"상관없음" 처리**: 저장 전송 시 "상관없음" 선택 → `null`로 매핑. 조회 응답의 `null` → 화면에서 "상관없음"으로 표시.
3. **`DISTANCE_OPTIONS`**: `{name:"SAME_REGION",description:"같은 지역만"}`, `{name:"ADJACENT_REGION",description:"인접 지역까지"}` 형태로 바꾸고 "상관없음"은 `null` 매핑.
4. **조회→표시 라벨 매핑**: `IdealType` 문자열이 enum name을 담으므로, 라벨 표기는 `/users/v1/profile/options` 응답의 `description`으로 역매핑한다.
5. **데이터소스 교체**: 로컬 `BrowserIdealTypeRepository` → `GET/PUT /users/v1/ideal-type` 호출하는 Remote 구현으로 교체하고 DI 컨테이너 갱신.

와이어 필드명(`ageRange`,`heightRange`,`maritalStatus`,`smoking`,`drinking`,`religion`,`distance`)과 배열 형태는 그대로 유지되므로, 프론트 변경은 **값 매핑(enum name·null·distance)** 과 **데이터소스 교체**에 국한된다.

## 9. 영향/신규 파일 (참고, 계획 단계에서 확정)

- **common:** 신규 `common/user/DistancePreference.kt`
- **core(user):**
  - command: 신규 `domain/UserIdealType.kt`, `application/SaveIdealTypeService.kt`, `application/port/in/SaveIdealTypeUseCase.kt`, `application/port/in/command/SaveIdealTypeCommand.kt`, `application/port/out/{GetIdealTypePort,SaveIdealTypePort}.kt`
  - query: 신규 `service/GetIdealTypeService.kt`, `service/port/in/GetIdealTypeUseCase.kt`, `dao/GetIdealTypeDao.kt`, `dto/IdealTypeView.kt`
  - `UserErrorCode.kt`(코드 추가)
- **infra(user):** 신규 `command/entity/UserIdealTypeEntity.kt`, `command/mapper/UserIdealTypeMapper.kt`, `command/repository/UserIdealTypeJpaRepository.kt`, `command/adapter/UserIdealTypeCoreAdapter.kt`, `query/GetIdealTypeDaoImpl.kt`; testFixtures에 `UserIdealTypeEntityFixture.kt`
- **api(user):** 신규 `IdealTypeController.kt`, `request/SaveIdealTypeRequest.kt`, `response/IdealTypeResponse.kt`
- **docs/migration:** 신규 `user_ideal_types` DDL SQL
- **테스트:** core 유닛 `UserIdealTypeTest.kt`, api E2E `IdealTypeE2ETest`(명명은 기존 관례 따름)
