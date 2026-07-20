# oneulsogae-admin 모듈 분리 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 어드민 전용 core-레이어 코드(대시보드·신고 조회 UseCase/Service/Port/View)를 운영 `oneulsogae-core`에서 떼어내 새 자립 모듈 `oneulsogae-admin`으로 옮긴다.

**Architecture:** `oneulsogae-admin`은 `oneulsogae-core`에 의존하지 않고 `oneulsogae-common`만 쓰는 자립 모듈이다(`oneulsogae-chatting`·`oneulsogae-scheduler`와 동일 컨벤션). core에 결합돼 있던 `TimeGenerator`·`BusinessException`/`ErrorCode`는 admin 자체 포트/예외로 대체하고, 예외→HTTP 변환 핸들러만 `oneulsogae-api`에 둔다. 어드민 컨트롤러(api)·어댑터(infra)는 기존 위치에 그대로 두고 import만 `admin.*`로 재지정한다.

**Tech Stack:** Kotlin 2.2.21 / JVM 21, Spring Boot 4.0.6 (Gradle 멀티모듈 + `oneulsogae.kotlin-conventions`), QueryDSL(OpenFeign 포크), Kotest 5.9.1 + RestAssured E2E(Testcontainers).

## Global Constraints

- 언어/응답: 모든 커밋 메시지·주석은 한국어. 커밋 형식 `<type>(<domain>): <설명>` (본 작업 도메인은 `admin`).
- **`oneulsogae-admin`은 `oneulsogae-core`를 의존하지 않는다.** admin 소스에 `com.org.oneulsogae.core.*` import 0건, build.gradle에 `project(":oneulsogae-core")` 없음.
- **운영 core 파일 무변경**: `oneulsogae-core`에서 삭제하는 것은 이동 대상 admin 파일뿐. 그 외 core 파일은 한 줄도 바꾸지 않는다.
- 타입 명시(변수·반환·람다 파라미터), `LocalDateTime.now()` 직접 호출은 `System*TimeGenerator` 구현체에만 허용.
- 클래스명은 유지(패키지만 이동). `AdminErrorCode.REPORT_NOT_FOUND`의 코드 문자열은 **"REPORT-001"** 그대로(응답 계약 보존).
- 검증 명령: 모듈 컴파일은 `./gradlew :oneulsogae-admin:compileKotlin`, 전체는 `./gradlew compileKotlin compileTestKotlin`, 어드민 E2E는 아래 Task 8의 명령.

---

## File Structure

**새 모듈 `oneulsogae-admin`** (base package `com.org.oneulsogae.admin`):
- `oneulsogae-admin/build.gradle.kts` — common + spring-context/tx/web 의존
- `admin/common/time/TimeGenerator.kt` — 시간 아웃포트(admin 자체)
- `admin/common/time/SystemAdminTimeGenerator.kt` — `@Component` 시스템 시계 구현
- `admin/common/error/AdminErrorCode.kt` — admin 에러 코드 enum
- `admin/common/error/AdminException.kt` — admin 커스텀 예외
- `admin/dashboard/query/service/port/in/GetAdminDashboardUseCase.kt`
- `admin/dashboard/query/service/GetAdminDashboardService.kt`
- `admin/dashboard/query/dao/GetAdminDashboardDao.kt`
- `admin/dashboard/query/dto/AdminDashboardView.kt`
- `admin/report/query/service/port/in/GetAdminReportsUseCase.kt`
- `admin/report/query/service/GetAdminReportsService.kt`
- `admin/report/query/dao/GetAdminReportDao.kt`
- `admin/report/query/dto/AdminReportDetailView.kt` / `AdminReportSummaryView.kt` / `AdminReportSummaryViews.kt` / `AdminReportPage.kt`

**수정(파일 이동 없음, import/의존만 변경):**
- `settings.gradle.kts` — `include("oneulsogae-admin")`
- `oneulsogae-api/build.gradle.kts`, `oneulsogae-infra/build.gradle.kts` — `implementation(project(":oneulsogae-admin"))`
- `oneulsogae-api/.../api/admin/AdminDashboardController.kt`, `AdminReportController.kt`
- `oneulsogae-api/.../api/admin/response/AdminDashboardResponse.kt`, `AdminReportDetailResponse.kt`, `AdminReportPageResponse.kt`, `AdminReportSummaryResponse.kt`
- `oneulsogae-api/.../api/admin/AdminExceptionHandler.kt` — **신규**
- `oneulsogae-infra/.../infra/admin/query/GetAdminDashboardDaoImpl.kt`, `infra/report/query/GetAdminReportDaoImpl.kt`

**삭제(core에서 제거):** 위 이동 대상 core 파일 8종 + 비게 된 `core/admin`, `core/report/query` 디렉터리.

---

### Task 1: oneulsogae-admin Gradle 모듈 뼈대 생성

새 모듈을 등록하고 빈 컴파일이 되도록 만든다. (아직 소스 없음)

**Files:**
- Modify: `settings.gradle.kts`
- Create: `oneulsogae-admin/build.gradle.kts`

**Interfaces:**
- Consumes: 없음
- Produces: Gradle 프로젝트 `:oneulsogae-admin` (common + spring-context/tx/web on compile classpath, Boot BOM 4.0.6 관리)

- [ ] **Step 1: `settings.gradle.kts`에 모듈 등록**

`include("oneulsogae-scheduler")` 아래 줄에 추가:

```kotlin
include("oneulsogae-admin")
```

수정 후 전체 모습:

```kotlin
rootProject.name = "oneulsogae-backend"

include("oneulsogae-api")
include("oneulsogae-core")
include("oneulsogae-infra")
include("oneulsogae-chatting")
include("oneulsogae-common")
include("oneulsogae-scheduler")
include("oneulsogae-auth")
include("oneulsogae-admin")
```

- [ ] **Step 2: `oneulsogae-admin/build.gradle.kts` 생성**

```kotlin
plugins {
	id("oneulsogae.kotlin-conventions")
}

dependencies {
	// admin은 core에 의존하지 않는다. 어드민의 도메인/유스케이스/서비스/포트를 자체 보유하고, 공용 enum만 common에서 가져온다.
	// 영속성은 자기 out-port(Dao)를 infra 어댑터가 구현해 채운다. (chatting·scheduler와 동일한 자립 구조)
	implementation(project(":oneulsogae-common"))

	// @Service(스테레오타입) · @Transactional 경계용. 실제 트랜잭션 매니저는 구동 앱(infra/JPA)이 제공한다.
	implementation("org.springframework:spring-context")
	implementation("org.springframework:spring-tx")
	// AdminErrorCode가 HttpStatus를 담는다. (core가 ErrorCode에 HttpStatus를 쓰는 것과 동일 — core 의존이 아니라 spring-web 라이브러리 의존)
	implementation("org.springframework:spring-web")
}
```

- [ ] **Step 3: 모듈이 인식·컴파일되는지 확인**

Run: `./gradlew :oneulsogae-admin:compileKotlin`
Expected: BUILD SUCCESSFUL (소스가 없어도 성공)

- [ ] **Step 4: 커밋**

```bash
git add settings.gradle.kts oneulsogae-admin/build.gradle.kts
git commit -m "build(admin): oneulsogae-admin 자립 모듈 뼈대 추가"
```

---

### Task 2: admin 공통 primitive (TimeGenerator · 예외) 작성

core 결합을 대체할 admin 자체 시간 포트/구현과 예외 타입을 만든다.

**Files:**
- Create: `oneulsogae-admin/src/main/kotlin/com/org/oneulsogae/admin/common/time/TimeGenerator.kt`
- Create: `oneulsogae-admin/src/main/kotlin/com/org/oneulsogae/admin/common/time/SystemAdminTimeGenerator.kt`
- Create: `oneulsogae-admin/src/main/kotlin/com/org/oneulsogae/admin/common/error/AdminErrorCode.kt`
- Create: `oneulsogae-admin/src/main/kotlin/com/org/oneulsogae/admin/common/error/AdminException.kt`

**Interfaces:**
- Consumes: 없음
- Produces:
  - `com.org.oneulsogae.admin.common.time.TimeGenerator` — `fun now(): LocalDateTime`, `fun today(): LocalDate`
  - `com.org.oneulsogae.admin.common.time.SystemAdminTimeGenerator` — `@Component`, 위 인터페이스 구현
  - `com.org.oneulsogae.admin.common.error.AdminErrorCode` — enum, 프로퍼티 `code: String`, `message: String`, `status: HttpStatus`; 항목 `REPORT_NOT_FOUND`
  - `com.org.oneulsogae.admin.common.error.AdminException(errorCode: AdminErrorCode, message: String)` — `RuntimeException`

- [ ] **Step 1: `TimeGenerator.kt` 작성** (scheduler 자립 포트와 동일 시그니처)

```kotlin
package com.org.oneulsogae.admin.common.time

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 현재 시각 제공 아웃포트.
 * 어드민 조회 로직이 [java.time.LocalDateTime.now]를 직접 호출하지 않고 이 인터페이스에 의존하게 해, 테스트에서 시각을 고정할 수 있게 한다.
 * (core의 동일 추상화에 의존하지 않도록 admin이 자체 포트로 둔다. 구현은 admin 모듈이 직접 제공한다)
 */
interface TimeGenerator {

	fun now(): LocalDateTime

	/** 오늘 날짜. "금일 경계" 같은 일자 기준 판단에 사용한다. */
	fun today(): LocalDate = now().toLocalDate()
}
```

- [ ] **Step 2: `SystemAdminTimeGenerator.kt` 작성** (scheduler `SystemBatchTimeGenerator`와 동일 패턴)

```kotlin
package com.org.oneulsogae.admin.common.time

import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * 시스템 시계를 사용하는 [TimeGenerator] 기본 구현. (인프라 의존이 없어 admin 모듈에서 직접 제공한다)
 * core·scheduler·chatting의 동명 System*TimeGenerator와 빈 이름이 겹치지 않도록 클래스명을 구분한다.
 */
@Component
class SystemAdminTimeGenerator : TimeGenerator {

	override fun now(): LocalDateTime = LocalDateTime.now()
}
```

- [ ] **Step 3: `AdminErrorCode.kt` 작성** (core `ErrorCode` 계약을 admin 자체로 복제)

```kotlin
package com.org.oneulsogae.admin.common.error

import org.springframework.http.HttpStatus

/**
 * 어드민 도메인 에러 코드. [AdminException]에 넘겨 사용한다.
 * (core의 ErrorCode/도메인 에러코드에 의존하지 않도록 admin이 자체 정의한다)
 */
enum class AdminErrorCode(
	val code: String,
	val message: String,
	val status: HttpStatus,
) {

	// 코드 문자열은 기존 신고 에러(REPORT-001)와 동일하게 유지해 응답 계약을 보존한다.
	REPORT_NOT_FOUND("REPORT-001", "신고를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
}
```

- [ ] **Step 4: `AdminException.kt` 작성** (core `BusinessException` 축소 복제)

```kotlin
package com.org.oneulsogae.admin.common.error

/**
 * 어드민 도메인 커스텀 예외. [AdminErrorCode]를 담아 던지면
 * api의 AdminExceptionHandler가 코드에 맞는 에러 응답(상태 코드 + 본문)으로 변환한다.
 */
class AdminException(
	val errorCode: AdminErrorCode,
	override val message: String = errorCode.message,
) : RuntimeException(message)
```

- [ ] **Step 5: 컴파일 확인**

Run: `./gradlew :oneulsogae-admin:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: 커밋**

```bash
git add oneulsogae-admin/src/main/kotlin/com/org/oneulsogae/admin/common
git commit -m "feat(admin): admin 자체 TimeGenerator·에러 primitive 추가"
```

---

### Task 3: 대시보드 조회 코드 이동 (core → admin)

`core.admin.query.*` 4파일을 admin 모듈로 옮기고 패키지를 `admin.dashboard.query.*`로 바꾼다. 서비스의 `TimeGenerator`는 admin 것을 쓴다.

**Files:**
- Create: `oneulsogae-admin/.../admin/dashboard/query/dto/AdminDashboardView.kt`
- Create: `oneulsogae-admin/.../admin/dashboard/query/dao/GetAdminDashboardDao.kt`
- Create: `oneulsogae-admin/.../admin/dashboard/query/service/port/in/GetAdminDashboardUseCase.kt`
- Create: `oneulsogae-admin/.../admin/dashboard/query/service/GetAdminDashboardService.kt`
- Delete: `oneulsogae-core/.../core/admin/query/dto/AdminDashboardView.kt`, `dao/GetAdminDashboardDao.kt`, `service/port/in/GetAdminDashboardUseCase.kt`, `service/GetAdminDashboardService.kt` (그리고 빈 `core/admin` 디렉터리)

**Interfaces:**
- Consumes: `com.org.oneulsogae.admin.common.time.TimeGenerator` (Task 2)
- Produces:
  - `com.org.oneulsogae.admin.dashboard.query.dto.AdminDashboardView` — 7개 Long 프로퍼티 data class
  - `com.org.oneulsogae.admin.dashboard.query.dao.GetAdminDashboardDao` — `fun load(todayStart: LocalDateTime): AdminDashboardView`
  - `com.org.oneulsogae.admin.dashboard.query.service.port.in.GetAdminDashboardUseCase` — `fun get(): AdminDashboardView`

- [ ] **Step 1: `AdminDashboardView.kt` 생성** (내용 동일, 패키지만 변경)

```kotlin
package com.org.oneulsogae.admin.dashboard.query.dto

/**
 * 어드민 대시보드 read model.
 * - [totalUsers]: 전체 사용자 수. (탈퇴 처리(soft delete)된 계정 제외)
 * - [todaySignups]: 금일 생성된 계정 수. (온보딩 완료 여부 무관, created_at 기준)
 * - [todayActiveUsers]: 금일 로그인한 사용자 수(DAU). (last_login_at 기준)
 * - [todayCoinPurchaseAmount]: 금일 결제(PURCHASE)로 적립된 코인 수량 합.
 * - [ongoingSoloMatches]: 진행중(PROPOSED·PARTIALLY_ACCEPTED) 1:1 매칭 수.
 * - [ongoingTeamMatches]: 진행중(PROPOSED·PARTIALLY_ACCEPTED) 팀 매칭 수.
 * - [pendingReports]: 미처리(PENDING) 신고 수.
 */
data class AdminDashboardView(
	val totalUsers: Long,
	val todaySignups: Long,
	val todayActiveUsers: Long,
	val todayCoinPurchaseAmount: Long,
	val ongoingSoloMatches: Long,
	val ongoingTeamMatches: Long,
	val pendingReports: Long,
)
```

- [ ] **Step 2: `GetAdminDashboardDao.kt` 생성**

```kotlin
package com.org.oneulsogae.admin.dashboard.query.dao

import com.org.oneulsogae.admin.dashboard.query.dto.AdminDashboardView
import java.time.LocalDateTime

/** 어드민 대시보드 지표 조회 dao. 구현은 infra가 담당한다. */
interface GetAdminDashboardDao {

	/** [todayStart](금일 00시)를 기준으로 전체/금일 지표를 집계해 돌려준다. */
	fun load(todayStart: LocalDateTime): AdminDashboardView
}
```

- [ ] **Step 3: `GetAdminDashboardUseCase.kt` 생성**

```kotlin
package com.org.oneulsogae.admin.dashboard.query.service.port.`in`

import com.org.oneulsogae.admin.dashboard.query.dto.AdminDashboardView

/** 어드민 대시보드 지표(전체 사용자·금일 가입자·금일 DAU·금일 코인 결제액) 조회 유스케이스. */
interface GetAdminDashboardUseCase {

	fun get(): AdminDashboardView
}
```

- [ ] **Step 4: `GetAdminDashboardService.kt` 생성** (import를 admin `TimeGenerator`로)

```kotlin
package com.org.oneulsogae.admin.dashboard.query.service

import com.org.oneulsogae.admin.common.time.TimeGenerator
import com.org.oneulsogae.admin.dashboard.query.dao.GetAdminDashboardDao
import com.org.oneulsogae.admin.dashboard.query.dto.AdminDashboardView
import com.org.oneulsogae.admin.dashboard.query.service.port.`in`.GetAdminDashboardUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** [GetAdminDashboardUseCase] 구현. 금일 경계(00시)를 계산해 조회 dao([GetAdminDashboardDao])에만 의존한다. */
@Service
class GetAdminDashboardService(
	private val getAdminDashboardDao: GetAdminDashboardDao,
	private val timeGenerator: TimeGenerator,
) : GetAdminDashboardUseCase {

	@Transactional(readOnly = true)
	override fun get(): AdminDashboardView =
		getAdminDashboardDao.load(timeGenerator.today().atStartOfDay())
}
```

- [ ] **Step 5: core의 대시보드 원본 4파일 삭제**

```bash
git rm oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/admin/query/dto/AdminDashboardView.kt \
       oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/admin/query/dao/GetAdminDashboardDao.kt \
       "oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/admin/query/service/port/in/GetAdminDashboardUseCase.kt" \
       oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/admin/query/service/GetAdminDashboardService.kt
```

빈 디렉터리 정리(`git rm`이 파일만 지우므로 남은 빈 폴더 제거):

```bash
find oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/admin -type d -empty -delete
```

- [ ] **Step 6: admin 모듈 컴파일 확인** (core는 아직 컨트롤러/impl이 옛 경로를 참조하므로 전체 빌드는 Task 6·7 후에 성공)

Run: `./gradlew :oneulsogae-admin:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: 커밋**

```bash
git add oneulsogae-admin oneulsogae-core
git commit -m "refactor(admin): 대시보드 조회 코드를 oneulsogae-admin으로 이동"
```

---

### Task 4: 신고 조회 View/DTO 이동 (core → admin)

`core.report.query.dto.*` 4파일(Admin* View/Page)을 admin으로 옮긴다. common enum import는 그대로.

**Files:**
- Create: `oneulsogae-admin/.../admin/report/query/dto/AdminReportDetailView.kt`, `AdminReportSummaryView.kt`, `AdminReportSummaryViews.kt`, `AdminReportPage.kt`
- Delete: 같은 4파일의 core 원본 (`oneulsogae-core/.../core/report/query/dto/*`)

**Interfaces:**
- Consumes: `com.org.oneulsogae.common.report.ReportStatus`, `com.org.oneulsogae.common.report.ReportType` (common, 변경 없음)
- Produces:
  - `com.org.oneulsogae.admin.report.query.dto.AdminReportDetailView` — data class(12필드)
  - `com.org.oneulsogae.admin.report.query.dto.AdminReportSummaryView` — data class(10필드)
  - `com.org.oneulsogae.admin.report.query.dto.AdminReportSummaryViews` — `val values: List<AdminReportSummaryView>` + `empty()`
  - `com.org.oneulsogae.admin.report.query.dto.AdminReportPage` — `reports/page/size/totalElements` + 파생 `totalPages`/`hasNext` + `empty(page,size)`

- [ ] **Step 1: `AdminReportSummaryView.kt` 생성**

```kotlin
package com.org.oneulsogae.admin.report.query.dto

import com.org.oneulsogae.common.report.ReportStatus
import com.org.oneulsogae.common.report.ReportType
import java.time.LocalDateTime

/**
 * 어드민 신고 목록 한 건(read model). 유저 신고(toUserId 존재)만 대상으로 하며,
 * 신고자·대상의 표시 정보(닉네임·이메일)를 users·user_details 조인으로 채운다. (없으면 null)
 */
data class AdminReportSummaryView(
	val id: Long,
	val type: ReportType,
	val status: ReportStatus,
	val createdAt: LocalDateTime?,
	val reporterId: Long,
	val reporterNickname: String?,
	val reporterEmail: String?,
	val targetUserId: Long,
	val targetNickname: String?,
	val targetEmail: String?,
)
```

- [ ] **Step 2: `AdminReportDetailView.kt` 생성**

```kotlin
package com.org.oneulsogae.admin.report.query.dto

import com.org.oneulsogae.common.report.ReportStatus
import com.org.oneulsogae.common.report.ReportType
import java.time.LocalDateTime

/** 어드민 신고 상세 read model. 목록 필드 + 신고 사유(description)·채팅방(chatRoomId). */
data class AdminReportDetailView(
	val id: Long,
	val type: ReportType,
	val status: ReportStatus,
	val createdAt: LocalDateTime?,
	val reporterId: Long,
	val reporterNickname: String?,
	val reporterEmail: String?,
	val targetUserId: Long,
	val targetNickname: String?,
	val targetEmail: String?,
	val description: String?,
	val chatRoomId: Long?,
)
```

- [ ] **Step 3: `AdminReportSummaryViews.kt` 생성**

```kotlin
package com.org.oneulsogae.admin.report.query.dto

/** 어드민 신고 목록 read model 일급 컬렉션. */
data class AdminReportSummaryViews(
	val values: List<AdminReportSummaryView>,
) {
	companion object {
		fun empty(): AdminReportSummaryViews = AdminReportSummaryViews(emptyList())
	}
}
```

- [ ] **Step 4: `AdminReportPage.kt` 생성**

```kotlin
package com.org.oneulsogae.admin.report.query.dto

/**
 * 어드민 신고 목록 한 페이지(read model). offset(page·size) 페이징 결과.
 * [totalElements]는 (유저 신고 한정) 전체 개수, [totalPages]/[hasNext]는 파생값.
 */
data class AdminReportPage(
	val reports: AdminReportSummaryViews,
	val page: Int,
	val size: Int,
	val totalElements: Long,
) {
	val totalPages: Int
		get() = if (size <= 0) 0 else ((totalElements + size - 1) / size).toInt()

	val hasNext: Boolean
		get() = (page + 1).toLong() * size < totalElements

	companion object {
		fun empty(page: Int, size: Int): AdminReportPage =
			AdminReportPage(AdminReportSummaryViews.empty(), page, size, 0)
	}
}
```

- [ ] **Step 5: core 원본 4파일 삭제**

```bash
git rm oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/report/query/dto/AdminReportDetailView.kt \
       oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/report/query/dto/AdminReportSummaryView.kt \
       oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/report/query/dto/AdminReportSummaryViews.kt \
       oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/report/query/dto/AdminReportPage.kt
```

- [ ] **Step 6: admin 모듈 컴파일 확인**

Run: `./gradlew :oneulsogae-admin:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: 커밋**

```bash
git add oneulsogae-admin oneulsogae-core
git commit -m "refactor(admin): 신고 조회 read model을 oneulsogae-admin으로 이동"
```

---

### Task 5: 신고 조회 UseCase/Dao/Service 이동 (core → admin)

`core.report.query`의 Dao·UseCase·Service를 admin으로 옮긴다. 서비스의 예외를 admin `AdminException`으로 교체한다.

**Files:**
- Create: `oneulsogae-admin/.../admin/report/query/dao/GetAdminReportDao.kt`
- Create: `oneulsogae-admin/.../admin/report/query/service/port/in/GetAdminReportsUseCase.kt`
- Create: `oneulsogae-admin/.../admin/report/query/service/GetAdminReportsService.kt`
- Delete: core 원본 `core/report/query/dao/GetAdminReportDao.kt`, `service/port/in/GetAdminReportsUseCase.kt`, `service/GetAdminReportsService.kt` (그리고 비게 된 `core/report/query` 디렉터리)

**Interfaces:**
- Consumes: Task 4의 `AdminReportDetailView`/`AdminReportPage`/`AdminReportSummaryViews`; Task 2의 `AdminErrorCode`/`AdminException`
- Produces:
  - `com.org.oneulsogae.admin.report.query.dao.GetAdminReportDao` — `fun findPage(offset: Long, limit: Int): AdminReportSummaryViews`, `fun count(): Long`, `fun findDetailById(id: Long): AdminReportDetailView?`
  - `com.org.oneulsogae.admin.report.query.service.port.in.GetAdminReportsUseCase` — `fun getReports(page: Int, size: Int): AdminReportPage`, `fun getReport(id: Long): AdminReportDetailView`

- [ ] **Step 1: `GetAdminReportDao.kt` 생성**

```kotlin
package com.org.oneulsogae.admin.report.query.dao

import com.org.oneulsogae.admin.report.query.dto.AdminReportDetailView
import com.org.oneulsogae.admin.report.query.dto.AdminReportSummaryViews

/** 어드민 신고 조회 dao(query out-port). 유저 신고(toUserId 존재)만 다룬다. */
interface GetAdminReportDao {

	/** 유저 신고를 최신순(created_at desc, id desc)으로 [offset]부터 [limit]건 조회한다. */
	fun findPage(offset: Long, limit: Int): AdminReportSummaryViews

	/** (soft delete 제외) 유저 신고 전체 개수. (페이징 메타데이터 계산용) */
	fun count(): Long

	/** 유저 신고 상세를 id로 조회한다. 없거나 팀 신고면 null. */
	fun findDetailById(id: Long): AdminReportDetailView?
}
```

- [ ] **Step 2: `GetAdminReportsUseCase.kt` 생성**

```kotlin
package com.org.oneulsogae.admin.report.query.service.port.`in`

import com.org.oneulsogae.admin.report.query.dto.AdminReportDetailView
import com.org.oneulsogae.admin.report.query.dto.AdminReportPage

/** 어드민 신고 목록 조회 유스케이스. */
interface GetAdminReportsUseCase {

	/** 유저 신고를 최신순으로 [page](0부터)·[size] 단위 페이징 조회한다. */
	fun getReports(page: Int, size: Int): AdminReportPage

	/** 유저 신고 상세를 [id]로 조회한다. 없거나 팀 신고면 예외를 던진다. */
	fun getReport(id: Long): AdminReportDetailView
}
```

- [ ] **Step 3: `GetAdminReportsService.kt` 생성** (예외를 `AdminException`으로 교체)

```kotlin
package com.org.oneulsogae.admin.report.query.service

import com.org.oneulsogae.admin.common.error.AdminErrorCode
import com.org.oneulsogae.admin.common.error.AdminException
import com.org.oneulsogae.admin.report.query.dao.GetAdminReportDao
import com.org.oneulsogae.admin.report.query.dto.AdminReportDetailView
import com.org.oneulsogae.admin.report.query.dto.AdminReportPage
import com.org.oneulsogae.admin.report.query.service.port.`in`.GetAdminReportsUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** [GetAdminReportsUseCase] 구현. (조회 전용) 유저 신고를 최신순 페이징 조회한다. */
@Service
@Transactional(readOnly = true)
class GetAdminReportsService(
	private val getAdminReportDao: GetAdminReportDao,
) : GetAdminReportsUseCase {

	override fun getReports(page: Int, size: Int): AdminReportPage {
		val pageNumber: Int = page.coerceAtLeast(0)
		val pageSize: Int = size.coerceIn(1, MAX_PAGE_SIZE)
		val offset: Long = pageNumber.toLong() * pageSize
		return AdminReportPage(
			reports = getAdminReportDao.findPage(offset, pageSize),
			page = pageNumber,
			size = pageSize,
			totalElements = getAdminReportDao.count(),
		)
	}

	override fun getReport(id: Long): AdminReportDetailView =
		getAdminReportDao.findDetailById(id)
			?: throw AdminException(AdminErrorCode.REPORT_NOT_FOUND, "신고를 찾을 수 없습니다: $id")

	companion object {
		private const val MAX_PAGE_SIZE: Int = 100
	}
}
```

- [ ] **Step 4: core 원본 3파일 삭제 + 빈 디렉터리 정리**

```bash
git rm oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/report/query/dao/GetAdminReportDao.kt \
       "oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/report/query/service/port/in/GetAdminReportsUseCase.kt" \
       oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/report/query/service/GetAdminReportsService.kt
find oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/report/query -type d -empty -delete
```

- [ ] **Step 5: admin 모듈 컴파일 확인**

Run: `./gradlew :oneulsogae-admin:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: 커밋**

```bash
git add oneulsogae-admin oneulsogae-core
git commit -m "refactor(admin): 신고 조회 UseCase/Service를 oneulsogae-admin으로 이동"
```

---

### Task 6: oneulsogae-infra를 admin 포트에 재배선

infra가 `oneulsogae-admin`을 의존하게 하고, 두 DAO 구현체의 import를 `core.*` → `admin.*`로 바꾼다. (엔티티 참조는 그대로)

**Files:**
- Modify: `oneulsogae-infra/build.gradle.kts`
- Modify: `oneulsogae-infra/.../infra/admin/query/GetAdminDashboardDaoImpl.kt` (import 3줄)
- Modify: `oneulsogae-infra/.../infra/report/query/GetAdminReportDaoImpl.kt` (import 4줄)

**Interfaces:**
- Consumes: Task 3·4·5의 admin dao/dto 타입
- Produces: `GetAdminDashboardDao`/`GetAdminReportDao`(admin)의 `@Component` 구현 — 시그니처 불변

- [ ] **Step 1: `oneulsogae-infra/build.gradle.kts`에 admin 의존 추가**

`implementation(project(":oneulsogae-chatting"))` 아래에 추가:

```kotlin
	// 어드민 전용 포트(admin 소유)를 infra 어댑터가 구현하므로 의존한다. (infra -> admin)
	implementation(project(":oneulsogae-admin"))
```

- [ ] **Step 2: `GetAdminDashboardDaoImpl.kt` import 교체**

다음 2줄을

```kotlin
import com.org.oneulsogae.core.admin.query.dao.GetAdminDashboardDao
import com.org.oneulsogae.core.admin.query.dto.AdminDashboardView
```

으로 교체:

```kotlin
import com.org.oneulsogae.admin.dashboard.query.dao.GetAdminDashboardDao
import com.org.oneulsogae.admin.dashboard.query.dto.AdminDashboardView
```

(클래스 본문·`: GetAdminDashboardDao`·`AdminDashboardView(...)` 등은 그대로)

- [ ] **Step 3: `GetAdminReportDaoImpl.kt` import 교체**

다음 4줄을

```kotlin
import com.org.oneulsogae.core.report.query.dao.GetAdminReportDao
import com.org.oneulsogae.core.report.query.dto.AdminReportDetailView
import com.org.oneulsogae.core.report.query.dto.AdminReportSummaryView
import com.org.oneulsogae.core.report.query.dto.AdminReportSummaryViews
```

으로 교체:

```kotlin
import com.org.oneulsogae.admin.report.query.dao.GetAdminReportDao
import com.org.oneulsogae.admin.report.query.dto.AdminReportDetailView
import com.org.oneulsogae.admin.report.query.dto.AdminReportSummaryView
import com.org.oneulsogae.admin.report.query.dto.AdminReportSummaryViews
```

- [ ] **Step 4: infra 컴파일 확인**

Run: `./gradlew :oneulsogae-infra:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 커밋**

```bash
git add oneulsogae-infra
git commit -m "refactor(admin): infra 어드민 어댑터를 oneulsogae-admin 포트에 재배선"
```

---

### Task 7: oneulsogae-api를 admin UseCase에 재배선 + 예외 핸들러 추가

api가 `oneulsogae-admin`을 의존하게 하고, 어드민 컨트롤러·응답 DTO의 import를 `admin.*`로 바꾼다. `AdminException`을 HTTP 응답으로 변환하는 핸들러를 추가한다.

**Files:**
- Modify: `oneulsogae-api/build.gradle.kts`
- Modify: `oneulsogae-api/.../api/admin/AdminDashboardController.kt` (import 1줄)
- Modify: `oneulsogae-api/.../api/admin/AdminReportController.kt` (import 1줄)
- Modify: `oneulsogae-api/.../api/admin/response/AdminDashboardResponse.kt` (import 1줄)
- Modify: `oneulsogae-api/.../api/admin/response/AdminReportDetailResponse.kt` (import 1줄)
- Modify: `oneulsogae-api/.../api/admin/response/AdminReportPageResponse.kt` (import 1줄)
- Modify: `oneulsogae-api/.../api/admin/response/AdminReportSummaryResponse.kt` (import 1줄)
- Create: `oneulsogae-api/.../api/admin/AdminExceptionHandler.kt`

**Interfaces:**
- Consumes: Task 2의 `AdminException`/`AdminErrorCode`; Task 3·5의 UseCase; Task 3·4의 View/Page; core의 `ApiResponse`/`ErrorResponse`(기존)
- Produces: 없음(경계)

- [ ] **Step 1: `oneulsogae-api/build.gradle.kts`에 admin 의존 추가**

`implementation(project(":oneulsogae-scheduler"))` 아래에 추가:

```kotlin
	implementation(project(":oneulsogae-admin"))
```

- [ ] **Step 2: 컨트롤러 import 교체**

`AdminDashboardController.kt`:

```kotlin
import com.org.oneulsogae.core.admin.query.service.port.`in`.GetAdminDashboardUseCase
```
→
```kotlin
import com.org.oneulsogae.admin.dashboard.query.service.port.`in`.GetAdminDashboardUseCase
```

`AdminReportController.kt`:

```kotlin
import com.org.oneulsogae.core.report.query.service.port.`in`.GetAdminReportsUseCase
```
→
```kotlin
import com.org.oneulsogae.admin.report.query.service.port.`in`.GetAdminReportsUseCase
```

(두 파일 모두 `import com.org.oneulsogae.core.common.response.ApiResponse`는 **그대로 둔다** — api는 core 의존)

- [ ] **Step 3: 응답 DTO import 교체**

`AdminDashboardResponse.kt`:
```kotlin
import com.org.oneulsogae.core.admin.query.dto.AdminDashboardView
```
→
```kotlin
import com.org.oneulsogae.admin.dashboard.query.dto.AdminDashboardView
```

`AdminReportDetailResponse.kt`:
```kotlin
import com.org.oneulsogae.core.report.query.dto.AdminReportDetailView
```
→
```kotlin
import com.org.oneulsogae.admin.report.query.dto.AdminReportDetailView
```

`AdminReportPageResponse.kt`:
```kotlin
import com.org.oneulsogae.core.report.query.dto.AdminReportPage
```
→
```kotlin
import com.org.oneulsogae.admin.report.query.dto.AdminReportPage
```

`AdminReportSummaryResponse.kt`:
```kotlin
import com.org.oneulsogae.core.report.query.dto.AdminReportSummaryView
```
→
```kotlin
import com.org.oneulsogae.admin.report.query.dto.AdminReportSummaryView
```

- [ ] **Step 4: `AdminExceptionHandler.kt` 생성** (core `GlobalExceptionHandler`의 BusinessException 처리와 동일 포맷)

```kotlin
package com.org.oneulsogae.api.admin

import com.org.oneulsogae.admin.common.error.AdminException
import com.org.oneulsogae.core.common.error.ErrorResponse
import com.org.oneulsogae.core.common.response.ApiResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * 어드민 예외 핸들러. [AdminException]을 공통 응답 봉투([ApiResponse])로 변환한다.
 * (admin 모듈은 core에 의존하지 않으므로 core GlobalExceptionHandler가 잡지 못한다. 표현 계층인 api에서 매핑한다)
 * 응답 본문 구조는 core의 BusinessException 처리와 동일하게 유지한다. (success=false, error에 코드/메시지)
 */
@RestControllerAdvice
class AdminExceptionHandler {

	private val log: Logger = LoggerFactory.getLogger(javaClass)

	@ExceptionHandler(AdminException::class)
	fun handleAdminException(e: AdminException): ResponseEntity<ApiResponse<Nothing>> {
		log.info("AdminException: code={}, message={}", e.errorCode.code, e.message)
		return ResponseEntity
			.status(e.errorCode.status)
			.body(ApiResponse.error(ErrorResponse(e.errorCode.code, e.message)))
	}
}
```

- [ ] **Step 5: api 컴파일 확인**

Run: `./gradlew :oneulsogae-api:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: 커밋**

```bash
git add oneulsogae-api
git commit -m "refactor(admin): api 어드민 컨트롤러를 oneulsogae-admin에 재배선하고 예외 핸들러 추가"
```

---

### Task 8: 전체 빌드·어드민 E2E 검증

전 모듈 컴파일과 어드민 E2E 통과로 이동이 동작 불변임을 확인한다.

**Files:** 없음(검증만)

**Interfaces:** 없음

- [ ] **Step 1: 전체 컴파일 (main + test)**

Run: `./gradlew compileKotlin compileTestKotlin`
Expected: BUILD SUCCESSFUL — 남아 있는 `com.org.oneulsogae.core.admin`/`core.report.query` 참조가 없어야 한다.

- [ ] **Step 2: admin이 core를 참조하지 않는지 검증**

Run:
```bash
grep -rn "com.org.oneulsogae.core" oneulsogae-admin/src 2>/dev/null; echo "exit=$?"
```
Expected: 매치 없음(출력 없고 `exit=1`).

Run:
```bash
grep -n "oneulsogae-core" oneulsogae-admin/build.gradle.kts; echo "exit=$?"
```
Expected: 매치 없음(`exit=1`).

- [ ] **Step 3: 어드민 E2E 실행**

Run:
```bash
./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.admin.*" --tests "com.org.oneulsogae.api.auth.AdminAccessE2ETest"
```
Expected: BUILD SUCCESSFUL — `AdminDashboardE2ETest`, `AdminReportListE2ETest`, `AdminReportDetailE2ETest`(404 2건 포함), `AdminAccessE2ETest` 모두 PASS.

- [ ] **Step 4: (안전망) 전체 테스트 스모크 — 시간이 허용되면**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 최종 상태 커밋 (변경이 남아 있을 때만)**

앞선 태스크에서 모두 커밋됐다면 이 단계는 생략한다. 정리(빈 디렉터리 등)로 변경이 생겼다면:

```bash
git add -A
git commit -m "chore(admin): oneulsogae-admin 분리 후 빌드·E2E 검증"
```

---

## Self-Review

**Spec coverage:**
- 새 모듈 생성/의존(common+spring) → Task 1. admin primitive(TimeGenerator·예외) → Task 2. 대시보드 이동 → Task 3. 신고 View 이동 → Task 4. 신고 UseCase/Service 이동 → Task 5. infra 재배선 → Task 6. api 재배선+핸들러 → Task 7. 검증(빌드·core미의존·E2E) → Task 8. **스펙의 모든 이동/제외/성공기준 항목이 태스크에 매핑됨.**
- 제외 항목(SecurityConfig·OAuth2·common enum·core 잔여 primitive)은 어떤 태스크에서도 건드리지 않음 — 계획에 수정 대상으로 등장하지 않음(의도적).

**Placeholder scan:** TBD/TODO/"적절히 처리" 없음. 모든 코드 스텝에 완전한 코드 포함.

**Type consistency:** admin 패키지 경로가 Task 3(`admin.dashboard.query.*`)·Task 4·5(`admin.report.query.*`)에서 일관. infra(Task 6)·api(Task 7)의 교체 대상 import가 이 경로와 정확히 일치. `AdminErrorCode.status: HttpStatus` → 핸들러(Task 7)에서 `ResponseEntity.status(e.errorCode.status)`로 사용, `code`는 `ErrorResponse(e.errorCode.code, e.message)`로 사용 — 시그니처 일치. `TimeGenerator.today()/now()`는 Task 2 정의와 Task 3 사용 일치.
