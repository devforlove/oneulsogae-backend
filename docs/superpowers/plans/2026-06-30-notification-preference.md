# 알림 설정(Notification Preference) + 알림톡 전송 게이트 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 사용자가 마이탭에서 켜고 끈 알림 설정을 백엔드에 저장·조회하고, 알림 발생 시 그 설정을 읽어 카카오 알림톡 전송 여부를 결정하는 게이트(실제 전송은 로그 stub)를 만든다.

**Architecture:** core에 `notification` 도메인을 CQRS(command/query)로 신설한다. 게이트 판단(`push && 카테고리`)은 도메인 모델 `NotificationPreference.allows()`에 캡슐화하고, `AlarmType`→카테고리 매핑은 `oneulsogae-common`에 둔다. `SaveAlarmService`(alarm)는 알림 저장 후 notification in-port `SendAlarmTalkUseCase`에 위임만 한다. 실제 알림톡 전송 어댑터는 로그만 남기는 stub로 두고, 나중에 카카오 API 구현으로 교체한다.

**Tech Stack:** Kotlin 2.2.21 / JVM 21, Spring Boot 4, Spring Data JPA, QueryDSL(OpenFeign fork), Kotest(DescribeSpec) + RestAssured + Testcontainers.

## Global Constraints

- **응답·주석은 한국어.**
- **`meeple-backend`만 수정.** 프론트(`meeple-frontend`)는 건드리지 않는다(연동 방법은 spec의 "프론트엔드 대응" 절 참고).
- 모듈 의존 방향 준수: `common`(무의존) ← `core`(common만) ← `infra`(core·common). 도메인 간 참조는 **상대 도메인 in-port UseCase 주입**(out-port·구현체 직접 주입 금지).
- CQRS: command는 도메인 모델, query는 전용 read model(View). query는 core에서 command 도메인·포트를 참조하지 않는다(infra 내부 query→command 엔티티/리포지토리 참조는 허용).
- 타입 명시(변수·반환·람다 파라미터). `LocalDateTime.now()` 직접 호출 금지(엔티티 픽스처/`SystemTimeGenerator` 제외 — 이 작업엔 시각 직접 사용 없음).
- 도메인 유닛 테스트는 **`oneulsogae-api` 테스트 소스셋**(`oneulsogae-api/src/test/kotlin/com/org/oneulsogae/notification/`)에 둔다(core엔 testImplementation 없음). E2E도 `oneulsogae-api`에 `AbstractIntegrationSupport` 상속으로 둔다.
- **기본값(프론트 `DEFAULT_NOTIFICATIONS`와 일치):** `push/oneToOne/meeting/team/message = true`, `marketing = false`.
- **커밋 green 유지:** core `@Service` 빈은 자신의 out-port를 만족하는 infra 빈이 있어야 컨텍스트가 뜬다. 그래서 순서를 `common → core 도메인 → core 포트/DTO(인터페이스만) → infra 어댑터 → core @Service → api → 게이트 배선`으로 잡아 매 커밋이 빌드·기존 E2E를 깨지 않게 한다.

---

### Task 1: common — NotificationCategory enum + AlarmType.category()

**Files:**
- Create: `oneulsogae-common/src/main/kotlin/com/org/oneulsogae/common/notification/NotificationCategory.kt`
- Modify: `oneulsogae-common/src/main/kotlin/com/org/oneulsogae/common/alarm/AlarmType.kt`
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/notification/AlarmTypeCategoryTest.kt`

**Interfaces:**
- Produces: `enum class NotificationCategory { ONE_TO_ONE, MEETING, TEAM, MESSAGE, MARKETING }`; `fun AlarmType.category(): NotificationCategory` (멤버 함수, 13종 모두 위 3개 중 하나 반환 — MESSAGE·MARKETING은 반환하지 않음).

- [ ] **Step 1: 실패 테스트 작성**

`oneulsogae-api/src/test/kotlin/com/org/oneulsogae/notification/AlarmTypeCategoryTest.kt`:

```kotlin
package com.org.oneulsogae.notification

import com.org.oneulsogae.common.alarm.AlarmType
import com.org.oneulsogae.common.notification.NotificationCategory
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class AlarmTypeCategoryTest : DescribeSpec({

	describe("AlarmType.category") {

		context("ONE_TO_ONE_* 4종은") {
			it("ONE_TO_ONE 카테고리로 매핑된다 (NO_MATCH_TODAY 포함)") {
				AlarmType.ONE_TO_ONE_INTEREST_RECEIVED.category() shouldBe NotificationCategory.ONE_TO_ONE
				AlarmType.ONE_TO_ONE_MATCHED.category() shouldBe NotificationCategory.ONE_TO_ONE
				AlarmType.ONE_TO_ONE_MATCH_ENDED.category() shouldBe NotificationCategory.ONE_TO_ONE
				AlarmType.ONE_TO_ONE_NO_MATCH_TODAY.category() shouldBe NotificationCategory.ONE_TO_ONE
			}
		}

		context("MANY_TO_MANY_* 4종은") {
			it("MEETING 카테고리로 매핑된다 (NO_MATCH_TODAY 포함)") {
				AlarmType.MANY_TO_MANY_INTEREST_RECEIVED.category() shouldBe NotificationCategory.MEETING
				AlarmType.MANY_TO_MANY_MATCHED.category() shouldBe NotificationCategory.MEETING
				AlarmType.MANY_TO_MANY_MATCH_ENDED.category() shouldBe NotificationCategory.MEETING
				AlarmType.MANY_TO_MANY_NO_MATCH_TODAY.category() shouldBe NotificationCategory.MEETING
			}
		}

		context("TEAM_* 5종은") {
			it("TEAM 카테고리로 매핑된다") {
				AlarmType.TEAM_INVITATION_RECEIVED.category() shouldBe NotificationCategory.TEAM
				AlarmType.TEAM_INVITATION_DECLINED.category() shouldBe NotificationCategory.TEAM
				AlarmType.TEAM_INVITATION_CANCELED.category() shouldBe NotificationCategory.TEAM
				AlarmType.TEAM_INVITATION_ACCEPTED.category() shouldBe NotificationCategory.TEAM
				AlarmType.TEAM_DISBANDED.category() shouldBe NotificationCategory.TEAM
			}
		}
	}
})
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.notification.AlarmTypeCategoryTest"`
Expected: 컴파일 실패(`NotificationCategory` / `category()` 미존재).

- [ ] **Step 3: NotificationCategory 생성**

`oneulsogae-common/src/main/kotlin/com/org/oneulsogae/common/notification/NotificationCategory.kt`:

```kotlin
package com.org.oneulsogae.common.notification

/**
 * 알림 설정 카테고리. 마이탭 토글(push 마스터 제외 5종)과 1:1 대응한다.
 * AlarmType은 [com.org.oneulsogae.common.alarm.AlarmType.category]로 이 카테고리에 묶인다.
 * MESSAGE·MARKETING은 현재 대응하는 AlarmType이 없는 예약 슬롯이다(채팅·마케팅 알림톡 추가 시 사용).
 */
enum class NotificationCategory {
	ONE_TO_ONE,
	MEETING,
	TEAM,
	MESSAGE,
	MARKETING,
}
```

- [ ] **Step 4: AlarmType에 category() 추가**

`AlarmType.kt`의 마지막 enum 상수(`TEAM_DISBANDED("팀 해체됨"),`) 뒤를 다음으로 바꾼다(상수 뒤 `;` 추가 후 멤버 함수). import도 추가한다:

```kotlin
package com.org.oneulsogae.common.alarm

import com.org.oneulsogae.common.notification.NotificationCategory
```

상수 블록 끝부분:

```kotlin
	/** [팀 매칭] 팀이 해체됨(해체를 실행한 구성원을 제외한 같은 팀의 남은 구성원에게). */
	TEAM_DISBANDED("팀 해체됨"),
	;

	/** 이 알람 유형이 속한 알림 설정 카테고리. (알림톡 전송 게이트가 이 값으로 사용자 설정을 평가) */
	fun category(): NotificationCategory =
		when (this) {
			ONE_TO_ONE_INTEREST_RECEIVED, ONE_TO_ONE_MATCHED, ONE_TO_ONE_MATCH_ENDED, ONE_TO_ONE_NO_MATCH_TODAY ->
				NotificationCategory.ONE_TO_ONE
			MANY_TO_MANY_INTEREST_RECEIVED, MANY_TO_MANY_MATCHED, MANY_TO_MANY_MATCH_ENDED, MANY_TO_MANY_NO_MATCH_TODAY ->
				NotificationCategory.MEETING
			TEAM_INVITATION_RECEIVED, TEAM_INVITATION_DECLINED, TEAM_INVITATION_CANCELED, TEAM_INVITATION_ACCEPTED, TEAM_DISBANDED ->
				NotificationCategory.TEAM
		}
```

(enum 전체에 대한 `when`이므로 `else` 불필요 — 새 AlarmType 추가 시 컴파일러가 분기 누락을 잡아준다.)

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.notification.AlarmTypeCategoryTest"`
Expected: PASS.

- [ ] **Step 6: 커밋**

```bash
git add oneulsogae-common/src/main/kotlin/com/org/oneulsogae/common/notification/NotificationCategory.kt \
        oneulsogae-common/src/main/kotlin/com/org/oneulsogae/common/alarm/AlarmType.kt \
        oneulsogae-api/src/test/kotlin/com/org/oneulsogae/notification/AlarmTypeCategoryTest.kt
git commit -m "feat(notification): NotificationCategory + AlarmType.category() 매핑 추가"
```

---

### Task 2: core — NotificationPreference 도메인 모델

**Files:**
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/notification/command/domain/NotificationPreference.kt`
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/notification/NotificationPreferenceTest.kt`

**Interfaces:**
- Consumes: `NotificationCategory`(Task 1).
- Produces: `data class NotificationPreference(id: Long = 0, userId: Long, push: Boolean = true, oneToOne: Boolean = true, meeting: Boolean = true, team: Boolean = true, message: Boolean = true, marketing: Boolean = false)`; `fun allows(category: NotificationCategory): Boolean`; `companion fun default(userId: Long): NotificationPreference`.

- [ ] **Step 1: 실패 테스트 작성**

`oneulsogae-api/src/test/kotlin/com/org/oneulsogae/notification/NotificationPreferenceTest.kt`:

```kotlin
package com.org.oneulsogae.notification

import com.org.oneulsogae.common.notification.NotificationCategory
import com.org.oneulsogae.core.notification.command.domain.NotificationPreference
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class NotificationPreferenceTest : DescribeSpec({

	describe("NotificationPreference.allows") {

		context("push 마스터가 꺼져 있으면") {
			it("개별 카테고리가 켜져 있어도 모두 false") {
				val pref = NotificationPreference(
					userId = 1L, push = false,
					oneToOne = true, meeting = true, team = true, message = true, marketing = true,
				)

				NotificationCategory.entries.forEach { category ->
					pref.allows(category) shouldBe false
				}
			}
		}

		context("push가 켜져 있으면") {
			it("해당 카테고리 플래그를 그대로 따른다") {
				val pref = NotificationPreference(
					userId = 1L, push = true,
					oneToOne = true, meeting = false, team = true, message = false, marketing = true,
				)

				pref.allows(NotificationCategory.ONE_TO_ONE) shouldBe true
				pref.allows(NotificationCategory.MEETING) shouldBe false
				pref.allows(NotificationCategory.TEAM) shouldBe true
				pref.allows(NotificationCategory.MESSAGE) shouldBe false
				pref.allows(NotificationCategory.MARKETING) shouldBe true
			}
		}
	}

	describe("NotificationPreference.default") {

		it("프론트 기본값과 일치한다 (marketing만 false)") {
			val pref = NotificationPreference.default(userId = 7L)

			pref.userId shouldBe 7L
			pref.push shouldBe true
			pref.oneToOne shouldBe true
			pref.meeting shouldBe true
			pref.team shouldBe true
			pref.message shouldBe true
			pref.marketing shouldBe false
		}
	}
})
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.notification.NotificationPreferenceTest"`
Expected: 컴파일 실패(`NotificationPreference` 미존재).

- [ ] **Step 3: 도메인 모델 작성**

`oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/notification/command/domain/NotificationPreference.kt`:

```kotlin
package com.org.oneulsogae.core.notification.command.domain

import com.org.oneulsogae.common.notification.NotificationCategory

/**
 * 사용자별 알림 설정. push 마스터 스위치 + 카테고리별 on/off를 보관한다.
 * 알림톡 전송 게이트는 [allows]로 'push && 해당 카테고리'를 판정한다(서비스에 if 나열 금지).
 * 행이 없는 사용자는 [default]로 간주한다(프론트 DEFAULT_NOTIFICATIONS와 일치).
 */
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

	/** push 마스터가 켜져 있고 [category] 플래그도 켜져 있을 때만 true. */
	fun allows(category: NotificationCategory): Boolean =
		push && when (category) {
			NotificationCategory.ONE_TO_ONE -> oneToOne
			NotificationCategory.MEETING -> meeting
			NotificationCategory.TEAM -> team
			NotificationCategory.MESSAGE -> message
			NotificationCategory.MARKETING -> marketing
		}

	companion object {

		/** 설정 행이 없는 사용자의 기본값. (프론트 DEFAULT_NOTIFICATIONS와 동일) */
		fun default(userId: Long): NotificationPreference = NotificationPreference(userId = userId)
	}
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.notification.NotificationPreferenceTest"`
Expected: PASS.

- [ ] **Step 5: 커밋**

```bash
git add oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/notification/command/domain/NotificationPreference.kt \
        oneulsogae-api/src/test/kotlin/com/org/oneulsogae/notification/NotificationPreferenceTest.kt
git commit -m "feat(notification): NotificationPreference 도메인 모델 + allows 게이트"
```

---

### Task 3: core — 포트·커맨드·query DTO/DAO 인터페이스 (구현체 없음)

`@Service` 빈을 만들지 않는 순수 인터페이스/데이터 클래스만 추가한다(컨텍스트에 영향 없음 → 커밋 green). 컴파일 검증만 한다.

**Files:**
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/notification/command/application/port/in/SaveNotificationPreferenceUseCase.kt`
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/notification/command/application/port/in/SendAlarmTalkUseCase.kt`
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/notification/command/application/port/in/command/SaveNotificationPreferenceCommand.kt`
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/notification/command/application/port/in/command/SendAlarmTalkCommand.kt`
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/notification/command/application/port/out/GetNotificationPreferencePort.kt`
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/notification/command/application/port/out/SaveNotificationPreferencePort.kt`
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/notification/command/application/port/out/AlarmTalkSenderPort.kt`
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/notification/query/dto/NotificationPreferenceView.kt`
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/notification/query/dao/GetNotificationPreferenceDao.kt`
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/notification/query/service/port/in/GetNotificationPreferenceUseCase.kt`

**Interfaces:**
- Produces (이후 태스크가 의존하는 정확한 시그니처):
  - `SaveNotificationPreferenceUseCase.save(command: SaveNotificationPreferenceCommand)` → `Unit`
  - `SendAlarmTalkUseCase.attempt(command: SendAlarmTalkCommand)` → `Unit`
  - `SaveNotificationPreferenceCommand(userId: Long, push: Boolean, oneToOne: Boolean, meeting: Boolean, team: Boolean, message: Boolean, marketing: Boolean)`
  - `SendAlarmTalkCommand(userId: Long, type: AlarmType, title: String, body: String)`
  - `GetNotificationPreferencePort.findByUserId(userId: Long): NotificationPreference?`
  - `SaveNotificationPreferencePort.save(preference: NotificationPreference): NotificationPreference`
  - `AlarmTalkSenderPort.send(userId: Long, title: String, body: String)`
  - `NotificationPreferenceView(push: Boolean, oneToOne: Boolean, meeting: Boolean, team: Boolean, message: Boolean, marketing: Boolean)` + `companion fun default(): NotificationPreferenceView`
  - `GetNotificationPreferenceDao.findByUserId(userId: Long): NotificationPreferenceView?`
  - `GetNotificationPreferenceUseCase.getByUserId(userId: Long): NotificationPreferenceView`

- [ ] **Step 1: in-port UseCase 2개 작성**

`.../port/in/SaveNotificationPreferenceUseCase.kt`:

```kotlin
package com.org.oneulsogae.core.notification.command.application.port.`in`

import com.org.oneulsogae.core.notification.command.application.port.`in`.command.SaveNotificationPreferenceCommand

/** 사용자 알림 설정 6개 전체를 교체(upsert)한다. */
interface SaveNotificationPreferenceUseCase {
	fun save(command: SaveNotificationPreferenceCommand)
}
```

`.../port/in/SendAlarmTalkUseCase.kt`:

```kotlin
package com.org.oneulsogae.core.notification.command.application.port.`in`

import com.org.oneulsogae.core.notification.command.application.port.`in`.command.SendAlarmTalkCommand

/**
 * 사용자 설정을 확인해 알림톡 전송을 시도한다.
 * 설정이 허용하지 않으면(혹은 push가 꺼져 있으면) 아무것도 보내지 않는다. (게이트)
 */
interface SendAlarmTalkUseCase {
	fun attempt(command: SendAlarmTalkCommand)
}
```

- [ ] **Step 2: 커맨드 2개 작성**

`.../port/in/command/SaveNotificationPreferenceCommand.kt`:

```kotlin
package com.org.oneulsogae.core.notification.command.application.port.`in`.command

/** 알림 설정 6개 전체 교체 입력. (full replace) */
data class SaveNotificationPreferenceCommand(
	val userId: Long,
	val push: Boolean,
	val oneToOne: Boolean,
	val meeting: Boolean,
	val team: Boolean,
	val message: Boolean,
	val marketing: Boolean,
)
```

`.../port/in/command/SendAlarmTalkCommand.kt`:

```kotlin
package com.org.oneulsogae.core.notification.command.application.port.`in`.command

import com.org.oneulsogae.common.alarm.AlarmType

/** 알림톡 전송 시도 입력. type으로 카테고리를 정해 설정을 평가한다. */
data class SendAlarmTalkCommand(
	val userId: Long,
	val type: AlarmType,
	val title: String,
	val body: String,
)
```

- [ ] **Step 3: out-port 3개 작성**

`.../port/out/GetNotificationPreferencePort.kt`:

```kotlin
package com.org.oneulsogae.core.notification.command.application.port.out

import com.org.oneulsogae.core.notification.command.domain.NotificationPreference

/** 사용자 알림 설정 단건 조회. (upsert·게이트용. 없으면 null) */
interface GetNotificationPreferencePort {
	fun findByUserId(userId: Long): NotificationPreference?
}
```

`.../port/out/SaveNotificationPreferencePort.kt`:

```kotlin
package com.org.oneulsogae.core.notification.command.application.port.out

import com.org.oneulsogae.core.notification.command.domain.NotificationPreference

/** 사용자 알림 설정 저장(신규 INSERT / 기존 UPDATE). */
interface SaveNotificationPreferencePort {
	fun save(preference: NotificationPreference): NotificationPreference
}
```

`.../port/out/AlarmTalkSenderPort.kt`:

```kotlin
package com.org.oneulsogae.core.notification.command.application.port.out

/**
 * 카카오 알림톡 전송 아웃포트. 현재 구현은 로그만 남기는 stub이며,
 * 나중에 이 구현만 실제 카카오 API 호출로 교체한다(포트·게이트는 그대로).
 */
interface AlarmTalkSenderPort {
	fun send(userId: Long, title: String, body: String)
}
```

- [ ] **Step 4: query read model + dao + in-port 작성**

`.../query/dto/NotificationPreferenceView.kt`:

```kotlin
package com.org.oneulsogae.core.notification.query.dto

/**
 * 알림 설정 조회 read model. (GET 응답 출처)
 * query는 command 도메인을 참조하지 않으므로 기본값도 여기서 자체 보유한다(프론트 기본값과 동일).
 */
data class NotificationPreferenceView(
	val push: Boolean,
	val oneToOne: Boolean,
	val meeting: Boolean,
	val team: Boolean,
	val message: Boolean,
	val marketing: Boolean,
) {

	companion object {

		/** 설정 행이 없는 사용자의 기본값. */
		fun default(): NotificationPreferenceView =
			NotificationPreferenceView(
				push = true,
				oneToOne = true,
				meeting = true,
				team = true,
				message = true,
				marketing = false,
			)
	}
}
```

`.../query/dao/GetNotificationPreferenceDao.kt`:

```kotlin
package com.org.oneulsogae.core.notification.query.dao

import com.org.oneulsogae.core.notification.query.dto.NotificationPreferenceView

/** 알림 설정 조회 dao(query out-port). 없으면 null. */
interface GetNotificationPreferenceDao {
	fun findByUserId(userId: Long): NotificationPreferenceView?
}
```

`.../query/service/port/in/GetNotificationPreferenceUseCase.kt`:

```kotlin
package com.org.oneulsogae.core.notification.query.service.port.`in`

import com.org.oneulsogae.core.notification.query.dto.NotificationPreferenceView

/** 사용자 알림 설정 조회. 행이 없으면 기본값 View를 반환한다. */
interface GetNotificationPreferenceUseCase {
	fun getByUserId(userId: Long): NotificationPreferenceView
}
```

- [ ] **Step 5: 컴파일 확인**

Run: `./gradlew :oneulsogae-core:compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: 커밋**

```bash
git add oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/notification
git commit -m "feat(notification): 포트·커맨드·query DTO/DAO 인터페이스 추가"
```

---

### Task 4: infra — 엔티티·매퍼·리포지토리·어댑터·DAO 구현 + 알림톡 stub

Task 3 포트를 구현하는 infra `@Component`들을 추가한다. 아직 이 빈들을 주입하는 곳이 없으므로 컨텍스트는 그대로 뜬다(green).

**Files:**
- Create: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/notification/command/entity/NotificationPreferenceEntity.kt`
- Create: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/notification/command/mapper/NotificationPreferenceMapper.kt`
- Create: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/notification/command/repository/NotificationPreferenceJpaRepository.kt`
- Create: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/notification/command/adapter/NotificationPreferenceAdapter.kt`
- Create: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/notification/command/adapter/AlarmTalkSenderAdapter.kt`
- Create: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/notification/query/GetNotificationPreferenceDaoImpl.kt`

**Interfaces:**
- Consumes: Task 3 포트들 + `NotificationPreference`(Task 2).
- Produces: `NotificationPreferenceEntity`(테이블 `notification_preferences`, `user_id` UNIQUE), `NotificationPreferenceJpaRepository.findByUserId(userId: Long): NotificationPreferenceEntity?`, `QNotificationPreferenceEntity`(kapt 생성 — E2E 정리에 사용).

- [ ] **Step 1: 엔티티 작성**

`.../command/entity/NotificationPreferenceEntity.kt`:

```kotlin
package com.org.oneulsogae.infra.notification.command.entity

import com.org.oneulsogae.infra.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table

/**
 * notification_preferences 테이블. 사용자당 1행(user_id UNIQUE)으로 알림 설정을 보관한다.
 * 도메인 로직을 두지 않고 상태만 보관한다. 소프트 삭제는 쓰지 않는다.
 */
@Entity
@Table(
	name = "notification_preferences",
	// 단건 조회/upsert가 user_id 등치로 인덱스 seek. 유저당 1행을 강제한다.
	indexes = [Index(name = "ux_user_id", columnList = "user_id", unique = true)],
)
class NotificationPreferenceEntity(
	@Column(name = "user_id", nullable = false)
	val userId: Long,

	/** 푸시 알림 마스터 스위치. 끄면 모든 알림톡 차단. */
	@Column(name = "push", nullable = false)
	var push: Boolean,

	/** 소개팅(1:1) 관심·매칭 알림. */
	@Column(name = "one_to_one", nullable = false)
	var oneToOne: Boolean,

	/** 미팅(다대다) 관심·매칭 알림. */
	@Column(name = "meeting", nullable = false)
	var meeting: Boolean,

	/** 팀 초대·수락·해체 알림. */
	@Column(name = "team", nullable = false)
	var team: Boolean,

	/** 새 메시지 알림. (현재 대응 AlarmType 없음 — 예약) */
	@Column(name = "message", nullable = false)
	var message: Boolean,

	/** 마케팅·이벤트 알림. (현재 대응 AlarmType 없음 — 예약) */
	@Column(name = "marketing", nullable = false)
	var marketing: Boolean,
) : BaseEntity()
```

- [ ] **Step 2: 매퍼 작성**

`.../command/mapper/NotificationPreferenceMapper.kt`:

```kotlin
package com.org.oneulsogae.infra.notification.command.mapper

import com.org.oneulsogae.core.notification.command.domain.NotificationPreference
import com.org.oneulsogae.infra.notification.command.entity.NotificationPreferenceEntity

/** 영속성 엔티티 -> 도메인 모델 */
fun NotificationPreferenceEntity.toDomain(): NotificationPreference =
	NotificationPreference(
		id = id ?: 0,
		userId = userId,
		push = push,
		oneToOne = oneToOne,
		meeting = meeting,
		team = team,
		message = message,
		marketing = marketing,
	)

/**
 * 도메인 모델 -> 영속성 엔티티.
 * id가 0이면 신규 INSERT, 0이 아니면 기존 행으로 식별돼 save 시 갱신(merge)된다.
 */
fun NotificationPreference.toEntity(): NotificationPreferenceEntity =
	NotificationPreferenceEntity(
		userId = userId,
		push = push,
		oneToOne = oneToOne,
		meeting = meeting,
		team = team,
		message = message,
		marketing = marketing,
	).also { if (id != 0L) it.id = id }
```

- [ ] **Step 3: 리포지토리 작성**

`.../command/repository/NotificationPreferenceJpaRepository.kt`:

```kotlin
package com.org.oneulsogae.infra.notification.command.repository

import com.org.oneulsogae.infra.notification.command.entity.NotificationPreferenceEntity
import org.springframework.data.jpa.repository.JpaRepository

/**
 * 알림 설정 영속성 리포지토리. user_id UNIQUE라 단건 조회는 1행을 돌려준다.
 * 명령(Get·Save out-port)은 [com.org.oneulsogae.infra.notification.command.adapter.NotificationPreferenceAdapter]가,
 * 조회 dao는 [com.org.oneulsogae.infra.notification.query.GetNotificationPreferenceDaoImpl]가 각각 사용한다.
 */
interface NotificationPreferenceJpaRepository : JpaRepository<NotificationPreferenceEntity, Long> {
	fun findByUserId(userId: Long): NotificationPreferenceEntity?
}
```

- [ ] **Step 4: command 어댑터 작성**

`.../command/adapter/NotificationPreferenceAdapter.kt`:

```kotlin
package com.org.oneulsogae.infra.notification.command.adapter

import com.org.oneulsogae.core.notification.command.application.port.out.GetNotificationPreferencePort
import com.org.oneulsogae.core.notification.command.application.port.out.SaveNotificationPreferencePort
import com.org.oneulsogae.core.notification.command.domain.NotificationPreference
import com.org.oneulsogae.infra.notification.command.mapper.toDomain
import com.org.oneulsogae.infra.notification.command.mapper.toEntity
import com.org.oneulsogae.infra.notification.command.repository.NotificationPreferenceJpaRepository
import org.springframework.stereotype.Component

/**
 * 알림 설정 명령 아웃포트([GetNotificationPreferencePort]·[SaveNotificationPreferencePort])의 JPA 구현. (한 엔티티 - 한 어댑터)
 * 엔티티/도메인 변환을 책임지며 외부에는 도메인 모델만 노출한다.
 */
@Component
class NotificationPreferenceAdapter(
	private val repository: NotificationPreferenceJpaRepository,
) : GetNotificationPreferencePort, SaveNotificationPreferencePort {

	override fun findByUserId(userId: Long): NotificationPreference? =
		repository.findByUserId(userId)?.toDomain()

	// id가 0이면 INSERT, 0이 아니면 기존 행 UPDATE(merge).
	override fun save(preference: NotificationPreference): NotificationPreference =
		repository.save(preference.toEntity()).toDomain()
}
```

- [ ] **Step 5: 알림톡 stub 어댑터 작성**

`.../command/adapter/AlarmTalkSenderAdapter.kt`:

```kotlin
package com.org.oneulsogae.infra.notification.command.adapter

import com.org.oneulsogae.core.notification.command.application.port.out.AlarmTalkSenderPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * [AlarmTalkSenderPort] stub 구현. 지금은 전송 의도만 로그로 남긴다.
 * 카카오 알림톡 연동 시 이 클래스만 실제 API 호출로 교체한다(게이트·포트 불변).
 */
@Component
class AlarmTalkSenderAdapter : AlarmTalkSenderPort {

	private val log = LoggerFactory.getLogger(javaClass)

	override fun send(userId: Long, title: String, body: String) {
		log.info("[알림톡 stub] userId={} title={} body={}", userId, title, body)
	}
}
```

- [ ] **Step 6: query dao 구현 작성**

`.../query/GetNotificationPreferenceDaoImpl.kt` (infra 내부 query→command 리포지토리/엔티티 참조 허용):

```kotlin
package com.org.oneulsogae.infra.notification.query

import com.org.oneulsogae.core.notification.query.dao.GetNotificationPreferenceDao
import com.org.oneulsogae.core.notification.query.dto.NotificationPreferenceView
import com.org.oneulsogae.infra.notification.command.repository.NotificationPreferenceJpaRepository
import org.springframework.stereotype.Component

/**
 * 알림 설정 조회 dao([GetNotificationPreferenceDao]) 구현. (조회 전용 read model 투영)
 * user_id UNIQUE 인덱스로 단건 seek. 명령 도메인(NotificationPreference) 대신 View로 직접 투영한다.
 */
@Component
class GetNotificationPreferenceDaoImpl(
	private val repository: NotificationPreferenceJpaRepository,
) : GetNotificationPreferenceDao {

	override fun findByUserId(userId: Long): NotificationPreferenceView? =
		repository.findByUserId(userId)?.let { entity ->
			NotificationPreferenceView(
				push = entity.push,
				oneToOne = entity.oneToOne,
				meeting = entity.meeting,
				team = entity.team,
				message = entity.message,
				marketing = entity.marketing,
			)
		}
}
```

- [ ] **Step 7: 컴파일 + 컨텍스트 안정성 확인**

Run: `./gradlew :oneulsogae-infra:compileKotlin`
Expected: BUILD SUCCESSFUL (kapt가 `QNotificationPreferenceEntity` 생성).

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.alarm.*"`
Expected: 기존 알람 E2E PASS(컨텍스트 정상 기동 — 새 빈이 기존 동작을 깨지 않음).

- [ ] **Step 8: 커밋**

```bash
git add oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/notification
git commit -m "feat(notification): 영속성 엔티티·어댑터·DAO + 알림톡 stub 구현"
```

---

### Task 5: core — command/query 서비스 3종 + 게이트 단위 테스트

infra 빈(Task 4)이 out-port를 만족하므로 이제 `@Service`를 추가해도 컨텍스트가 뜬다.

**Files:**
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/notification/command/application/SaveNotificationPreferenceService.kt`
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/notification/command/application/SendAlarmTalkService.kt`
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/notification/query/service/GetNotificationPreferenceService.kt`
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/notification/SendAlarmTalkServiceTest.kt`

**Interfaces:**
- Consumes: Task 3 포트/커맨드/DTO, Task 2 도메인, Task 1 `category()`.
- Produces: `SaveNotificationPreferenceService`(SaveNotificationPreferenceUseCase 구현), `SendAlarmTalkService`(SendAlarmTalkUseCase 구현), `GetNotificationPreferenceService`(GetNotificationPreferenceUseCase 구현).

- [ ] **Step 1: 게이트 실패 테스트 작성 (fake 사용, Spring 없이)**

`oneulsogae-api/src/test/kotlin/com/org/oneulsogae/notification/SendAlarmTalkServiceTest.kt`:

```kotlin
package com.org.oneulsogae.notification

import com.org.oneulsogae.common.alarm.AlarmType
import com.org.oneulsogae.core.notification.command.application.SendAlarmTalkService
import com.org.oneulsogae.core.notification.command.application.port.`in`.command.SendAlarmTalkCommand
import com.org.oneulsogae.core.notification.command.application.port.out.AlarmTalkSenderPort
import com.org.oneulsogae.core.notification.command.application.port.out.GetNotificationPreferencePort
import com.org.oneulsogae.core.notification.command.domain.NotificationPreference
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class SendAlarmTalkServiceTest : DescribeSpec({

	// 전송 호출을 기록하는 fake sender
	class RecordingSender : AlarmTalkSenderPort {
		val sent: MutableList<Triple<Long, String, String>> = mutableListOf()
		override fun send(userId: Long, title: String, body: String) {
			sent.add(Triple(userId, title, body))
		}
	}

	// 지정한 설정만 돌려주는 fake (null이면 미존재)
	class FixedPrefPort(private val preference: NotificationPreference?) : GetNotificationPreferencePort {
		override fun findByUserId(userId: Long): NotificationPreference? = preference
	}

	fun command(type: AlarmType): SendAlarmTalkCommand =
		SendAlarmTalkCommand(userId = 1L, type = type, title = "제목", body = "본문")

	describe("SendAlarmTalkService.attempt") {

		context("해당 카테고리가 켜져 있으면") {
			it("알림톡을 보낸다") {
				val sender = RecordingSender()
				val service = SendAlarmTalkService(
					FixedPrefPort(NotificationPreference(userId = 1L, push = true, team = true)),
					sender,
				)

				service.attempt(command(AlarmType.TEAM_INVITATION_RECEIVED))

				sender.sent.size shouldBe 1
				sender.sent.first() shouldBe Triple(1L, "제목", "본문")
			}
		}

		context("해당 카테고리가 꺼져 있으면") {
			it("보내지 않는다") {
				val sender = RecordingSender()
				val service = SendAlarmTalkService(
					FixedPrefPort(NotificationPreference(userId = 1L, push = true, team = false)),
					sender,
				)

				service.attempt(command(AlarmType.TEAM_INVITATION_RECEIVED))

				sender.sent.size shouldBe 0
			}
		}

		context("push 마스터가 꺼져 있으면") {
			it("카테고리가 켜져 있어도 보내지 않는다") {
				val sender = RecordingSender()
				val service = SendAlarmTalkService(
					FixedPrefPort(NotificationPreference(userId = 1L, push = false, oneToOne = true)),
					sender,
				)

				service.attempt(command(AlarmType.ONE_TO_ONE_MATCHED))

				sender.sent.size shouldBe 0
			}
		}

		context("설정 행이 없으면") {
			it("기본값(켜짐)으로 간주해 보낸다") {
				val sender = RecordingSender()
				val service = SendAlarmTalkService(FixedPrefPort(null), sender)

				service.attempt(command(AlarmType.ONE_TO_ONE_MATCHED))

				sender.sent.size shouldBe 1
			}
		}
	}
})
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.notification.SendAlarmTalkServiceTest"`
Expected: 컴파일 실패(`SendAlarmTalkService` 미존재).

- [ ] **Step 3: SendAlarmTalkService 작성**

`.../command/application/SendAlarmTalkService.kt`:

```kotlin
package com.org.oneulsogae.core.notification.command.application

import com.org.oneulsogae.core.notification.command.application.port.`in`.SendAlarmTalkUseCase
import com.org.oneulsogae.core.notification.command.application.port.`in`.command.SendAlarmTalkCommand
import com.org.oneulsogae.core.notification.command.application.port.out.AlarmTalkSenderPort
import com.org.oneulsogae.core.notification.command.application.port.out.GetNotificationPreferencePort
import com.org.oneulsogae.core.notification.command.domain.NotificationPreference
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [SendAlarmTalkUseCase] 구현. 사용자 설정을 조회해 알림톡 전송 여부를 결정한다(게이트).
 * 설정 행이 없으면 [NotificationPreference.default]로 간주한다. DB 쓰기는 없어 readOnly 트랜잭션.
 * (향후 실제 카카오 전송은 외부 호출이므로, 호출자 트랜잭션 커밋 이후로 분리하는 것을 검토)
 */
@Service
@Transactional(readOnly = true)
class SendAlarmTalkService(
	private val getNotificationPreferencePort: GetNotificationPreferencePort,
	private val alarmTalkSenderPort: AlarmTalkSenderPort,
) : SendAlarmTalkUseCase {

	override fun attempt(command: SendAlarmTalkCommand) {
		val preference: NotificationPreference =
			getNotificationPreferencePort.findByUserId(command.userId)
				?: NotificationPreference.default(command.userId)

		if (preference.allows(command.type.category())) {
			alarmTalkSenderPort.send(command.userId, command.title, command.body)
		}
	}
}
```

- [ ] **Step 4: SaveNotificationPreferenceService 작성**

`.../command/application/SaveNotificationPreferenceService.kt`:

```kotlin
package com.org.oneulsogae.core.notification.command.application

import com.org.oneulsogae.core.notification.command.application.port.`in`.SaveNotificationPreferenceUseCase
import com.org.oneulsogae.core.notification.command.application.port.`in`.command.SaveNotificationPreferenceCommand
import com.org.oneulsogae.core.notification.command.application.port.out.GetNotificationPreferencePort
import com.org.oneulsogae.core.notification.command.application.port.out.SaveNotificationPreferencePort
import com.org.oneulsogae.core.notification.command.domain.NotificationPreference
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [SaveNotificationPreferenceUseCase] 구현. 6개 설정 전체를 교체(upsert)한다.
 * 기존 행이 있으면 그 id를 이어받아 UPDATE, 없으면 INSERT 한다(user_id UNIQUE 위반 방지).
 */
@Service
@Transactional
class SaveNotificationPreferenceService(
	private val getNotificationPreferencePort: GetNotificationPreferencePort,
	private val saveNotificationPreferencePort: SaveNotificationPreferencePort,
) : SaveNotificationPreferenceUseCase {

	override fun save(command: SaveNotificationPreferenceCommand) {
		val existingId: Long = getNotificationPreferencePort.findByUserId(command.userId)?.id ?: 0
		saveNotificationPreferencePort.save(
			NotificationPreference(
				id = existingId,
				userId = command.userId,
				push = command.push,
				oneToOne = command.oneToOne,
				meeting = command.meeting,
				team = command.team,
				message = command.message,
				marketing = command.marketing,
			),
		)
	}
}
```

- [ ] **Step 5: GetNotificationPreferenceService 작성**

`.../query/service/GetNotificationPreferenceService.kt`:

```kotlin
package com.org.oneulsogae.core.notification.query.service

import com.org.oneulsogae.core.notification.query.dao.GetNotificationPreferenceDao
import com.org.oneulsogae.core.notification.query.dto.NotificationPreferenceView
import com.org.oneulsogae.core.notification.query.service.port.`in`.GetNotificationPreferenceUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [GetNotificationPreferenceUseCase] 구현. 설정 행이 없으면 기본값 View를 반환한다.
 */
@Service
@Transactional(readOnly = true)
class GetNotificationPreferenceService(
	private val getNotificationPreferenceDao: GetNotificationPreferenceDao,
) : GetNotificationPreferenceUseCase {

	override fun getByUserId(userId: Long): NotificationPreferenceView =
		getNotificationPreferenceDao.findByUserId(userId) ?: NotificationPreferenceView.default()
}
```

- [ ] **Step 6: 게이트 테스트 통과 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.notification.SendAlarmTalkServiceTest"`
Expected: PASS.

- [ ] **Step 7: 컨텍스트 안정성 확인 (새 @Service 빈 주입 만족)**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.alarm.*"`
Expected: PASS(컨텍스트 정상 기동).

- [ ] **Step 8: 커밋**

```bash
git add oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/notification \
        oneulsogae-api/src/test/kotlin/com/org/oneulsogae/notification/SendAlarmTalkServiceTest.kt
git commit -m "feat(notification): 설정 저장·조회·알림톡 게이트 서비스 추가"
```

---

### Task 6: api — 컨트롤러 + 요청/응답 DTO + E2E

**Files:**
- Create: `oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/notification/NotificationPreferenceController.kt`
- Create: `oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/notification/request/UpdateNotificationPreferenceRequest.kt`
- Create: `oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/notification/response/NotificationPreferenceResponse.kt`
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/notification/NotificationPreferenceE2ETest.kt`

**Interfaces:**
- Consumes: `GetNotificationPreferenceUseCase`, `SaveNotificationPreferenceUseCase`, `SaveNotificationPreferenceCommand`, `NotificationPreferenceView`, `AuthUser`/`@LoginUser`, `ApiResponse`, `QNotificationPreferenceEntity`(Task 4).
- Produces: `GET /notification-preferences/v1`, `PUT /notification-preferences/v1`.

- [ ] **Step 1: E2E 실패 테스트 작성**

`oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/notification/NotificationPreferenceE2ETest.kt`:

```kotlin
package com.org.oneulsogae.api.notification

import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.notification.command.entity.QNotificationPreferenceEntity
import io.restassured.RestAssured
import io.restassured.http.ContentType

class NotificationPreferenceE2ETest : AbstractIntegrationSupport() {

	init {

		describe("GET /notification-preferences/v1") {

			context("설정한 적 없는 사용자가 조회하면") {
				it("기본값을 반환한다 (marketing만 false)") {
					val userId = 71001L

					RestAssured.given()
						.header("Authorization", "Bearer ${accessTokenFor(userId)}")
						.`when`()
						.get("/notification-preferences/v1")
						.then()
						.statusCode(200)
						.body("data.push", org.hamcrest.Matchers.equalTo(true))
						.body("data.oneToOne", org.hamcrest.Matchers.equalTo(true))
						.body("data.meeting", org.hamcrest.Matchers.equalTo(true))
						.body("data.team", org.hamcrest.Matchers.equalTo(true))
						.body("data.message", org.hamcrest.Matchers.equalTo(true))
						.body("data.marketing", org.hamcrest.Matchers.equalTo(false))
				}
			}
		}

		describe("PUT /notification-preferences/v1") {

			context("설정을 저장한 뒤 다시 조회하면") {
				it("저장한 값이 그대로 반환된다 (upsert, idempotent)") {
					val userId = 71002L
					val token = accessTokenFor(userId)
					val body = mapOf(
						"push" to true,
						"oneToOne" to false,
						"meeting" to true,
						"team" to false,
						"message" to true,
						"marketing" to true,
					)

					// 1차 저장
					RestAssured.given()
						.header("Authorization", "Bearer $token")
						.contentType(ContentType.JSON)
						.body(body)
						.`when`()
						.put("/notification-preferences/v1")
						.then()
						.statusCode(200)

					// 동일 본문 재저장(멱등)
					RestAssured.given()
						.header("Authorization", "Bearer $token")
						.contentType(ContentType.JSON)
						.body(body)
						.`when`()
						.put("/notification-preferences/v1")
						.then()
						.statusCode(200)

					// 조회로 검증
					RestAssured.given()
						.header("Authorization", "Bearer $token")
						.`when`()
						.get("/notification-preferences/v1")
						.then()
						.statusCode(200)
						.body("data.oneToOne", org.hamcrest.Matchers.equalTo(false))
						.body("data.team", org.hamcrest.Matchers.equalTo(false))
						.body("data.marketing", org.hamcrest.Matchers.equalTo(true))
				}
			}
		}

		afterTest {
			IntegrationUtil.deleteAll(QNotificationPreferenceEntity.notificationPreferenceEntity)
		}
	}
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.notification.NotificationPreferenceE2ETest"`
Expected: 컴파일 실패(컨트롤러/엔드포인트 미존재).

- [ ] **Step 3: 요청 DTO 작성**

`.../notification/request/UpdateNotificationPreferenceRequest.kt`:

```kotlin
package com.org.oneulsogae.api.notification.request

import com.org.oneulsogae.core.notification.command.application.port.`in`.command.SaveNotificationPreferenceCommand

/** 알림 설정 6개 전체 교체 요청. (모두 필수) */
data class UpdateNotificationPreferenceRequest(
	val push: Boolean,
	val oneToOne: Boolean,
	val meeting: Boolean,
	val team: Boolean,
	val message: Boolean,
	val marketing: Boolean,
) {

	fun toCommand(userId: Long): SaveNotificationPreferenceCommand =
		SaveNotificationPreferenceCommand(
			userId = userId,
			push = push,
			oneToOne = oneToOne,
			meeting = meeting,
			team = team,
			message = message,
			marketing = marketing,
		)
}
```

- [ ] **Step 4: 응답 DTO 작성**

`.../notification/response/NotificationPreferenceResponse.kt`:

```kotlin
package com.org.oneulsogae.api.notification.response

import com.org.oneulsogae.core.notification.query.dto.NotificationPreferenceView

/** 알림 설정 조회 응답. */
data class NotificationPreferenceResponse(
	val push: Boolean,
	val oneToOne: Boolean,
	val meeting: Boolean,
	val team: Boolean,
	val message: Boolean,
	val marketing: Boolean,
) {

	companion object {

		fun from(view: NotificationPreferenceView): NotificationPreferenceResponse =
			NotificationPreferenceResponse(
				push = view.push,
				oneToOne = view.oneToOne,
				meeting = view.meeting,
				team = view.team,
				message = view.message,
				marketing = view.marketing,
			)
	}
}
```

- [ ] **Step 5: 컨트롤러 작성**

`.../notification/NotificationPreferenceController.kt`:

```kotlin
package com.org.oneulsogae.api.notification

import com.org.oneulsogae.api.notification.request.UpdateNotificationPreferenceRequest
import com.org.oneulsogae.api.notification.response.NotificationPreferenceResponse
import com.org.oneulsogae.auth.AuthUser
import com.org.oneulsogae.auth.LoginUser
import com.org.oneulsogae.core.common.response.ApiResponse
import com.org.oneulsogae.core.notification.command.application.port.`in`.SaveNotificationPreferenceUseCase
import com.org.oneulsogae.core.notification.query.service.port.`in`.GetNotificationPreferenceUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 알림 설정 엔드포인트. (모두 인증 필요)
 * - GET /: 현재 로그인 사용자의 알림 설정 6개를 조회한다(없으면 기본값).
 * - PUT /: 6개 전체를 교체한다(full replace, 멱등 upsert).
 */
@Tag(name = "알림 설정", description = "마이탭 알림 설정 조회·저장. 향후 카카오 알림톡 전송 여부 판단에 사용된다.")
@RestController
@RequestMapping("/notification-preferences/v1")
class NotificationPreferenceController(
	private val getNotificationPreferenceUseCase: GetNotificationPreferenceUseCase,
	private val saveNotificationPreferenceUseCase: SaveNotificationPreferenceUseCase,
) {

	@Operation(summary = "내 알림 설정 조회", description = "현재 로그인 사용자의 알림 설정 6개를 조회한다. 설정한 적 없으면 기본값을 반환한다.")
	@GetMapping
	fun myPreference(
		@LoginUser user: AuthUser,
	): ApiResponse<NotificationPreferenceResponse> =
		ApiResponse.success(NotificationPreferenceResponse.from(getNotificationPreferenceUseCase.getByUserId(user.id)))

	@Operation(summary = "내 알림 설정 저장", description = "현재 로그인 사용자의 알림 설정 6개를 전체 교체한다. (멱등 upsert)")
	@PutMapping
	fun updatePreference(
		@LoginUser user: AuthUser,
		@RequestBody request: UpdateNotificationPreferenceRequest,
	): ApiResponse<Unit> {
		saveNotificationPreferenceUseCase.save(request.toCommand(user.id))
		return ApiResponse.success()
	}
}
```

- [ ] **Step 6: E2E 통과 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.notification.NotificationPreferenceE2ETest"`
Expected: PASS.

- [ ] **Step 7: 커밋**

```bash
git add oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/notification \
        oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/notification/NotificationPreferenceE2ETest.kt
git commit -m "feat(notification): 알림 설정 조회·저장 API + E2E"
```

---

### Task 7: alarm — SaveAlarmService에 알림톡 게이트 배선

알림 저장 직후 notification in-port에 위임해 알림톡 전송을 시도한다.

**Files:**
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/alarm/command/application/SaveAlarmService.kt`
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/notification/SaveAlarmGateTest.kt`

**Interfaces:**
- Consumes: `SendAlarmTalkUseCase`, `SendAlarmTalkCommand`(Task 3), 기존 `SaveAlarmPort`/`SaveAlarmCommand`/`Alarm`.

- [ ] **Step 1: 배선 실패 테스트 작성 (fake 사용, Spring 없이)**

`oneulsogae-api/src/test/kotlin/com/org/oneulsogae/notification/SaveAlarmGateTest.kt`:

```kotlin
package com.org.oneulsogae.notification

import com.org.oneulsogae.common.alarm.AlarmType
import com.org.oneulsogae.core.alarm.command.application.SaveAlarmService
import com.org.oneulsogae.core.alarm.command.application.port.`in`.command.SaveAlarmCommand
import com.org.oneulsogae.core.alarm.command.application.port.out.SaveAlarmPort
import com.org.oneulsogae.core.alarm.command.domain.Alarm
import com.org.oneulsogae.core.notification.command.application.port.`in`.SendAlarmTalkUseCase
import com.org.oneulsogae.core.notification.command.application.port.`in`.command.SendAlarmTalkCommand
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class SaveAlarmGateTest : DescribeSpec({

	// 저장 시 id를 부여해 도메인으로 돌려주는 fake
	class EchoSavePort : SaveAlarmPort {
		override fun save(alarm: Alarm): Alarm = alarm.copy(id = 100L)
	}

	// attempt 호출을 기록하는 fake
	class RecordingSendAlarmTalk : SendAlarmTalkUseCase {
		val attempts: MutableList<SendAlarmTalkCommand> = mutableListOf()
		override fun attempt(command: SendAlarmTalkCommand) {
			attempts.add(command)
		}
	}

	describe("SaveAlarmService.save") {

		it("알림을 저장하고, 저장된 알림 정보로 알림톡 전송을 시도한다") {
			val sendAlarmTalk = RecordingSendAlarmTalk()
			val service = SaveAlarmService(EchoSavePort(), sendAlarmTalk)

			val saved = service.save(
				SaveAlarmCommand(
					userId = 42L,
					type = AlarmType.TEAM_INVITATION_RECEIVED,
					title = "팀 초대 받음",
					description = "초대가 도착했어요",
					link = "/teams/1",
				),
			)

			saved.id shouldBe 100L
			sendAlarmTalk.attempts.size shouldBe 1
			sendAlarmTalk.attempts.first() shouldBe SendAlarmTalkCommand(
				userId = 42L,
				type = AlarmType.TEAM_INVITATION_RECEIVED,
				title = "팀 초대 받음",
				body = "초대가 도착했어요",
			)
		}
	}
})
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.notification.SaveAlarmGateTest"`
Expected: 컴파일 실패(`SaveAlarmService` 생성자에 두 번째 인자 없음).

- [ ] **Step 3: SaveAlarmService 수정**

`SaveAlarmService.kt`를 다음으로 바꾼다(주입 추가 + 저장 후 게이트 위임):

```kotlin
package com.org.oneulsogae.core.alarm.command.application

import com.org.oneulsogae.core.alarm.command.application.port.`in`.SaveAlarmUseCase
import com.org.oneulsogae.core.alarm.command.application.port.`in`.command.SaveAlarmCommand
import com.org.oneulsogae.core.alarm.command.application.port.out.SaveAlarmPort
import com.org.oneulsogae.core.alarm.command.domain.Alarm
import com.org.oneulsogae.core.notification.command.application.port.`in`.SendAlarmTalkUseCase
import com.org.oneulsogae.core.notification.command.application.port.`in`.command.SendAlarmTalkCommand
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [SaveAlarmUseCase] 구현.
 * 입력을 도메인 모델([Alarm.create])로 만들어 저장하고, 저장된 알림으로 알림톡 전송을 시도한다.
 * 전송 여부 판단(사용자 설정 게이트)은 notification 도메인([SendAlarmTalkUseCase])에 위임한다.
 */
@Service
@Transactional
class SaveAlarmService(
	private val saveAlarmPort: SaveAlarmPort,
	private val sendAlarmTalkUseCase: SendAlarmTalkUseCase,
) : SaveAlarmUseCase {

	override fun save(command: SaveAlarmCommand): Alarm {
		val saved: Alarm = saveAlarmPort.save(
			Alarm.create(
				userId = command.userId,
				type = command.type,
				title = command.title,
				description = command.description,
				link = command.link,
				fromUserId = command.fromUserId,
				fromTeamId = command.fromTeamId,
			),
		)
		sendAlarmTalkUseCase.attempt(
			SendAlarmTalkCommand(
				userId = saved.userId,
				type = saved.type,
				title = saved.title,
				body = saved.description,
			),
		)
		return saved
	}
}
```

- [ ] **Step 4: 배선 테스트 통과 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.notification.SaveAlarmGateTest"`
Expected: PASS.

- [ ] **Step 5: 기존 알람 E2E 회귀 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.alarm.*"`
Expected: PASS(컨텍스트 기동 + 기존 알람 저장 흐름이 게이트로 깨지지 않음).

- [ ] **Step 6: 커밋**

```bash
git add oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/alarm/command/application/SaveAlarmService.kt \
        oneulsogae-api/src/test/kotlin/com/org/oneulsogae/notification/SaveAlarmGateTest.kt
git commit -m "feat(alarm): 알림 저장 시 알림톡 전송 게이트 위임"
```

---

### Task 8: 전체 검증

- [ ] **Step 1: 전체 빌드·테스트**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL. 신규 테스트(`AlarmTypeCategoryTest`, `NotificationPreferenceTest`, `SendAlarmTalkServiceTest`, `SaveAlarmGateTest`, `NotificationPreferenceE2ETest`) 모두 PASS, 기존 테스트 회귀 없음.

- [ ] **Step 2: (커밋 없음 — 변경 사항이 없으면 종료)**

빌드가 깨지면 systematic-debugging으로 원인 파악 후 해당 Task로 돌아가 수정·재커밋한다.

---

## 프론트엔드 후속 (별도 — 백엔드 범위 밖)

이 플랜은 백엔드만 다룬다. 연동 시 프론트(`meeple-frontend`)는 `src/domains/settings/data/datasources/local/SettingsDataSource.ts`에서 마이탭 진입 시 `GET /notification-preferences/v1`로 초기화, 토글 변경 시 6개 전체를 `PUT`으로 동기화하면 된다. 키(`push/oneToOne/meeting/team/message/marketing`)가 백엔드 필드와 1:1 동일해 변환은 불필요하다. (상세: `docs/superpowers/specs/2026-06-30-notification-preference-design.md`)
