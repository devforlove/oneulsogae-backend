# 알림 설정(Notification Preference) + 알림톡 전송 게이트 설계

## 목적

사용자가 마이탭에서 켜고 끈 알림 설정을 백엔드에 저장하고, 알림이 발생할 때 그 설정을 조회해 **카카오 알림톡 전송 여부를 결정**한다. 알림톡 실제 전송 어댑터는 아직 없으므로(나중에 추가) 이번에는 **전송 게이트까지 연결하고 실제 send는 stub(로그)** 로 둔다.

프론트는 현재 알림 설정을 localStorage에만 저장한다(API 없음). 백엔드가 설정의 진실 원천이 되어야 알림톡 발송 시점에 서버가 사용자의 선택을 읽을 수 있다.

## 범위

- 신규 `notification` 도메인(core, CQRS: command/query).
- 설정 저장/조회 API(`GET`/`PUT /notification-preferences/v1`).
- `SaveAlarmService`가 알림 저장 후 notification in-port에 위임해 알림톡 전송을 시도하는 게이트.
- `AlarmTalkSenderPort` infra 구현체는 **로그만 남기는 stub**(실제 카카오 API 호출은 범위 밖).
- 프론트 변경은 **하지 않고 안내만** 한다(리포지토리 경계 규칙).

## 알림 단위 결정

프론트의 6개 토글을 **그대로** 백엔드 저장 단위로 쓴다(1:1 매핑, 변환 불필요).

- `push` — 마스터 스위치(끄면 모든 알림톡 차단)
- `oneToOne` `meeting` `team` `message` `marketing` — 5개 카테고리

`push`를 제외한 13종 `AlarmType`은 **카테고리로 그룹화**해서 게이트에 건다.

## AlarmType ↔ 카테고리 매핑

`oneulsogae-common`에 `NotificationCategory` enum 신설(`AlarmType` 옆).

```kotlin
enum class NotificationCategory {
    ONE_TO_ONE, MEETING, TEAM, MESSAGE, MARKETING
}
```

`AlarmType.category(): NotificationCategory` 매핑:

| AlarmType | 카테고리 |
|---|---|
| `ONE_TO_ONE_INTEREST_RECEIVED` / `ONE_TO_ONE_MATCHED` / `ONE_TO_ONE_MATCH_ENDED` / `ONE_TO_ONE_NO_MATCH_TODAY` | `ONE_TO_ONE` |
| `MANY_TO_MANY_INTEREST_RECEIVED` / `MANY_TO_MANY_MATCHED` / `MANY_TO_MANY_MATCH_ENDED` / `MANY_TO_MANY_NO_MATCH_TODAY` | `MEETING` |
| `TEAM_INVITATION_RECEIVED` / `TEAM_INVITATION_DECLINED` / `TEAM_INVITATION_CANCELED` / `TEAM_INVITATION_ACCEPTED` / `TEAM_DISBANDED` | `TEAM` |

- `NO_MATCH_TODAY`(일일배치 "오늘 소개 없음")는 접두사 기준으로 각각 `ONE_TO_ONE`·`MEETING`에 매핑한다.
- **`MESSAGE`·`MARKETING`은 현재 대응하는 `AlarmType`이 없다.** 설정값은 저장하되, 알림톡 게이트에 걸리는 타입이 아직 없는 **예약 슬롯**이다(채팅·마케팅 알림톡 추가 시 사용). `AlarmType.category()`는 이 두 값을 반환하지 않는다.

> 매핑은 `AlarmType`(common)에 두므로 command·query 양쪽이 공유한다(도메인 모델/포트가 아닌 common enum 위의 순수 함수).

## notification 도메인 (core, CQRS)

```
core/notification/
├─ command/
│  ├─ application/
│  │  ├─ SaveNotificationPreferenceService.kt    // upsert (PUT)
│  │  ├─ SendAlarmTalkService.kt                 // 게이트 + 전송 stub 위임
│  │  └─ port/
│  │     ├─ in/
│  │     │  ├─ SaveNotificationPreferenceUseCase.kt
│  │     │  ├─ SendAlarmTalkUseCase.kt
│  │     │  └─ command/
│  │     │     ├─ SaveNotificationPreferenceCommand.kt
│  │     │     └─ SendAlarmTalkCommand.kt
│  │     └─ out/
│  │        ├─ GetNotificationPreferencePort.kt     // upsert·게이트용 단건 조회
│  │        ├─ SaveNotificationPreferencePort.kt
│  │        └─ AlarmTalkSenderPort.kt               // stub 대상
│  └─ domain/
│     └─ NotificationPreference.kt
└─ query/
   ├─ service/
   │  ├─ GetNotificationPreferenceService.kt
   │  └─ port/in/GetNotificationPreferenceUseCase.kt
   ├─ dao/GetNotificationPreferenceDao.kt
   └─ dto/NotificationPreferenceView.kt
```

### 도메인 모델 `NotificationPreference` (command/domain)

```kotlin
data class NotificationPreference(
    val id: Long = 0,
    val userId: Long,
    val push: Boolean = true,
    val oneToOne: Boolean = true,
    val meeting: Boolean = true,
    val team: Boolean = true,
    val message: Boolean = true,
    val marketing: Boolean = false,
) {
    fun allows(category: NotificationCategory): Boolean =
        push && when (category) {
            NotificationCategory.ONE_TO_ONE -> oneToOne
            NotificationCategory.MEETING -> meeting
            NotificationCategory.TEAM -> team
            NotificationCategory.MESSAGE -> message
            NotificationCategory.MARKETING -> marketing
        }

    companion object {
        // 행이 없는 유저의 기본값. 프론트 DEFAULT_NOTIFICATIONS와 일치.
        fun default(userId: Long): NotificationPreference = NotificationPreference(userId = userId)
    }
}
```

- 게이트 판단(`push && 카테고리 플래그`)을 도메인 모델 `allows()`에 캡슐화한다. 서비스에 `if…throw`/`if` 나열 금지.
- 기본값은 프론트 `DEFAULT_NOTIFICATIONS`와 일치: `push/oneToOne/meeting/team/message = true`, `marketing = false`.

### command 서비스

- `SaveNotificationPreferenceService` (`@Transactional`, `SaveNotificationPreferenceUseCase` 구현)
  - `SaveNotificationPreferenceCommand(userId, push, oneToOne, meeting, team, message, marketing)` 6개 전체를 받는다(**full replace**, idempotent).
  - `GetNotificationPreferencePort.findByUserId(userId)`로 기존 행을 읽어 있으면 갱신, 없으면 신규 생성 → `SaveNotificationPreferencePort.save(pref)`. (upsert)
- `SendAlarmTalkService` (`@Transactional` 또는 readOnly 검토 — 외부 전송 부수효과가 있으므로 `@Transactional`, `SendAlarmTalkUseCase` 구현)
  - `attempt(SendAlarmTalkCommand(userId, type, title, body))`:
    ```
    val pref = getNotificationPreferencePort.findByUserId(userId) ?: NotificationPreference.default(userId)
    if (pref.allows(type.category())) alarmTalkSenderPort.send(userId, title, body)
    ```
  - `MESSAGE`·`MARKETING`은 `AlarmType.category()`가 반환하지 않으므로 이 경로로 들어오는 일이 없다(현재 모든 `AlarmType`은 위 3 카테고리).

### query 서비스

- `GetNotificationPreferenceService` (`@Transactional(readOnly = true)`, `GetNotificationPreferenceUseCase` 구현)
  - `getByUserId(userId): NotificationPreferenceView` — `GetNotificationPreferenceDao`로 조회, 없으면 **기본값 View** 반환.
- `NotificationPreferenceView(push, oneToOne, meeting, team, message, marketing)` — GET API 응답용 read model.
- query는 자기 dao에만 의존하고 command 도메인·포트를 참조하지 않는다(CLAUDE.md CQRS 규칙). 단건 조회가 command(`GetNotificationPreferencePort`)·query(`GetNotificationPreferenceDao`) 양쪽에 있어도 **공유하지 않고 각자 구현**한다.

## infra

```
infra/notification/
├─ command/
│  ├─ entity/NotificationPreferenceEntity.kt
│  ├─ mapper/NotificationPreferenceMapper.kt
│  ├─ repository/NotificationPreferenceJpaRepository.kt
│  └─ adapter/
│     ├─ NotificationPreferenceAdapter.kt   // Get·Save 포트 함께 구현(엔티티당 어댑터 하나)
│     └─ AlarmTalkSenderAdapter.kt          // AlarmTalkSenderPort stub 구현(로그만)
└─ query/
   └─ GetNotificationPreferenceDaoImpl.kt   // Spring Data 파생 쿼리 findByUserId
```

### 테이블 `notification_preferences`

| 컬럼 | 타입 | 비고 |
|---|---|---|
| `id` | bigint PK auto | |
| `user_id` | bigint **UNIQUE NOT NULL** | 유저당 1행 |
| `push` | boolean NOT NULL | |
| `one_to_one` | boolean NOT NULL | |
| `meeting` | boolean NOT NULL | |
| `team` | boolean NOT NULL | |
| `message` | boolean NOT NULL | |
| `marketing` | boolean NOT NULL | |
| `created_at`/`updated_at` | datetime | `BaseEntity` |

- `user_id` unique 인덱스로 단건 조회/upsert가 인덱스 seek.
- 행이 없는 유저는 조회·게이트 모두 `NotificationPreference.default()`로 간주(미리 행 생성 안 함).

### `AlarmTalkSenderAdapter` (stub)

```kotlin
@Component
class AlarmTalkSenderAdapter : AlarmTalkSenderPort {
    private val log = LoggerFactory.getLogger(javaClass)
    override fun send(userId: Long, title: String, body: String) {
        log.info("[알림톡 stub] userId={} title={} body={}", userId, title, body)
    }
}
```

나중에 이 구현만 카카오 알림톡 API 호출로 교체한다(포트·게이트는 그대로).

## alarm 도메인 연동 (게이트 진입점)

`SaveAlarmService`(alarm command)는 preference 로직을 모른 채 notification in-port에만 위임한다.

```kotlin
// SaveAlarmService.save(command) 내, 기존 alarm 저장 직후
val saved = saveAlarmPort.save(alarm)
sendAlarmTalkUseCase.attempt(
    SendAlarmTalkCommand(
        userId = saved.userId,
        type = saved.type,
        title = saved.title,
        body = saved.description,
    ),
)
return saved
```

- alarm → notification은 **다른 도메인 in-port(`SendAlarmTalkUseCase`) 주입**(CLAUDE.md 도메인 간 참조 규칙). out-port·구현체 직접 주입 금지.
- 게이트 판단은 notification 도메인 안에 캡슐화되어 alarm은 "이 알림에 대해 알림톡을 시도하라"만 호출한다.
- `SaveAlarmService`가 배치(다수 유저)에서 호출되더라도 호출 단위가 알림 1건이므로 게이트도 1건씩 평가된다.

## API (oneulsogae-api)

```
api/notification/
├─ NotificationPreferenceController.kt
├─ request/UpdateNotificationPreferenceRequest.kt
└─ response/NotificationPreferenceResponse.kt
```

| 메서드 | 경로 | 설명 |
|---|---|---|
| `GET` | `/notification-preferences/v1` | `@LoginUser`의 설정 6개 조회. 없으면 기본값. |
| `PUT` | `/notification-preferences/v1` | 6개 전체 교체(full replace, idempotent upsert). |

- `UpdateNotificationPreferenceRequest(push, oneToOne, meeting, team, message, marketing: Boolean)` — 6개 모두 필수.
- `NotificationPreferenceResponse(push, oneToOne, meeting, team, message, marketing: Boolean)`.
- 응답은 `ApiResponse<NotificationPreferenceResponse>` / `ApiResponse<Unit>`(기존 컨벤션).
- Controller는 `GetNotificationPreferenceUseCase`(GET)·`SaveNotificationPreferenceUseCase`(PUT) in-port를 주입한다.

## 모듈 의존

- `NotificationCategory`(common) ← `AlarmType.category()`(common)에서 사용.
- core notification 도메인은 common에만 의존.
- alarm command(`SaveAlarmService`)가 notification in-port(`SendAlarmTalkUseCase`)에 의존 — 둘 다 core 내부라 모듈 경계 위반 없음.
- infra notification 어댑터가 out-port 구현(`oneulsogae-infra ──> core` 기존 방향 유지).

## 테스트 전략

- **도메인 유닛(Kotest)**: `NotificationPreference.allows()` — push off → 전부 false, 카테고리별 on/off 조합, 기본값.
- **E2E(oneulsogae-api)**: 
  - `PUT` 후 `GET`이 같은 값을 반환(upsert·idempotent).
  - 행 없는 유저 `GET` → 기본값.
  - 알림 발생 → preference에 따라 `AlarmTalkSenderPort`가 호출/미호출(stub을 검증용으로 관찰하거나 테스트 더블로 대체).

## 프론트엔드 대응 (백엔드에서 수정하지 않음 — 안내만)

리포지토리 경계 규칙상 백엔드만 수정한다. 프론트(`meeple-frontend`)는 별도로 다음을 반영해야 한다.

- `src/domains/settings/data/datasources/local/SettingsDataSource.ts`
  - 현재 localStorage 전용 → 백엔드 연동: 마이탭 진입 시 `GET /notification-preferences/v1`로 초기화, 토글 변경 후 6개 전체를 `PUT`으로 동기화.
  - 저장 키(`push/oneToOne/meeting/team/message/marketing`)가 백엔드 필드와 1:1 동일하므로 키↔AlarmType 그룹 매핑 변환은 불필요(기존 주석의 "선호 API 연동 시 변환"은 더 이상 필요 없음).
  - localStorage는 오프라인 캐시/낙관적 업데이트 용도로 유지하거나 제거 — 프론트 판단.
- 요청/응답 DTO: `{ push, oneToOne, meeting, team, message, marketing: boolean }` (6개 boolean, 평면 구조).
```
