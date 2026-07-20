# 어드민 공지(Notice) API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 어드민이 공지를 목록 페이징 조회·상세 조회·추가할 수 있는 API 3종(`/admin/v1/notices`)을 추가한다.

**Architecture:** `oneulsogae-admin`의 self-contained 헥사고날 패턴(`companyverification` 미러링)으로 어드민 전용 query/command 컴포넌트를 신설하고, 기존 `notices` 테이블/`NoticeEntity`/`QNoticeEntity`를 재사용한다. 영속성은 `oneulsogae-infra`의 DaoImpl(조회)·Adapter(저장)가 어드민 out-port를 구현하고, HTTP 경계(Controller/DTO)는 `oneulsogae-api`에 둔다.

**Tech Stack:** Kotlin 2.2.21, Spring Boot 4, Spring Data JPA, QueryDSL, Kotest(유닛), RestAssured + Testcontainers(E2E).

## Global Constraints

- 응답은 항상 한국어. 코드 주석/에러 메시지 한국어.
- `meeple-backend`만 수정. 프론트엔드 변경 금지.
- 어드민 모듈(`oneulsogae-admin`)은 core에 의존하지 않는다(self-contained). core `Notice`/`NoticeView` 등을 import하지 않는다.
- 타입 명시: 변수·반환 타입·람다 파라미터 타입 생략 금지.
- `LocalDateTime.now()` 직접 호출 금지(이 기능은 created_at을 JPA Auditing이 채우므로 시각 직접 생성 없음).
- 컨트롤러는 core `com.org.oneulsogae.core.common.response.ApiResponse`를 반환(기존 어드민 컨트롤러 규약).
- 어드민 경로 `/admin/**`는 `SecurityConfig`의 `hasRole("ADMIN")`로 자동 보호(별도 처리 불필요).
- 들여쓰기는 탭(기존 파일 스타일).
- 페이징: `page` default 0(`coerceAtLeast(0)`), `size` default 20(`coerceIn(1, 100)`), 정렬 `created_at desc, id desc`.
- read model 필드 — 목록 행: `id, title, createdAt` / 상세: `id, title, description, createdAt`.

**패키지 루트:** `com.org.oneulsogae.admin.notice` (admin), `com.org.oneulsogae.infra.notice` (infra), `com.org.oneulsogae.api.admin` (api).

---

### Task 1: 어드민 에러코드 + 조회 read model DTO

**Files:**
- Modify: `oneulsogae-admin/src/main/kotlin/com/org/oneulsogae/admin/common/error/AdminErrorCode.kt`
- Create: `oneulsogae-admin/src/main/kotlin/com/org/oneulsogae/admin/notice/query/dto/AdminNoticeView.kt`
- Create: `oneulsogae-admin/src/main/kotlin/com/org/oneulsogae/admin/notice/query/dto/AdminNoticeViews.kt`
- Create: `oneulsogae-admin/src/main/kotlin/com/org/oneulsogae/admin/notice/query/dto/AdminNoticeDetailView.kt`
- Create: `oneulsogae-admin/src/main/kotlin/com/org/oneulsogae/admin/notice/query/dto/AdminNoticePage.kt`

**Interfaces:**
- Produces:
  - `AdminNoticeView(id: Long, title: String, createdAt: LocalDateTime?)`
  - `AdminNoticeViews(values: List<AdminNoticeView>)` + `companion object { fun empty(): AdminNoticeViews }`
  - `AdminNoticeDetailView(id: Long, title: String, description: String, createdAt: LocalDateTime?)`
  - `AdminNoticePage(content: AdminNoticeViews, page: Int, size: Int, totalElements: Long)` with `val totalPages: Int`, `val hasNext: Boolean`, `companion object { fun empty(page: Int, size: Int): AdminNoticePage }`
  - `AdminErrorCode.NOTICE_NOT_FOUND`

- [ ] **Step 1: `AdminErrorCode`에 `NOTICE_NOT_FOUND` 추가**

`AdminErrorCode.kt`의 마지막 enum 상수(`COMPANY_IMAGE_VERIFICATION_NOT_FOUND(...)`) 다음 줄에 추가:

```kotlin
	COMPANY_IMAGE_VERIFICATION_NOT_FOUND("COMPANY-IMAGE-001", "직장 인증을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
	NOTICE_NOT_FOUND("NOTICE-001", "공지를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
```

- [ ] **Step 2: `AdminNoticeView` 생성**

```kotlin
package com.org.oneulsogae.admin.notice.query.dto

import java.time.LocalDateTime

/** 어드민 공지 목록 한 건(read model). 본문(description)은 상세에서만 노출한다. */
data class AdminNoticeView(
	val id: Long,
	val title: String,
	val createdAt: LocalDateTime?,
)
```

- [ ] **Step 3: `AdminNoticeViews` 생성 (일급 컬렉션)**

```kotlin
package com.org.oneulsogae.admin.notice.query.dto

/** 어드민 공지 목록 read model 일급 컬렉션. */
data class AdminNoticeViews(
	val values: List<AdminNoticeView>,
) {
	companion object {
		fun empty(): AdminNoticeViews = AdminNoticeViews(emptyList())
	}
}
```

- [ ] **Step 4: `AdminNoticeDetailView` 생성**

```kotlin
package com.org.oneulsogae.admin.notice.query.dto

import java.time.LocalDateTime

/** 어드민 공지 상세 read model. 목록 필드 + 본문(description). */
data class AdminNoticeDetailView(
	val id: Long,
	val title: String,
	val description: String,
	val createdAt: LocalDateTime?,
)
```

- [ ] **Step 5: `AdminNoticePage` 생성 (페이징 read model)**

```kotlin
package com.org.oneulsogae.admin.notice.query.dto

/**
 * 어드민 공지 목록 한 페이지(read model). offset(page·size) 페이징 결과.
 * [totalElements]는 (soft delete 제외) 전체 개수, [totalPages]/[hasNext]는 파생값.
 */
data class AdminNoticePage(
	val content: AdminNoticeViews,
	val page: Int,
	val size: Int,
	val totalElements: Long,
) {
	val totalPages: Int
		get() = if (size <= 0) 0 else ((totalElements + size - 1) / size).toInt()

	val hasNext: Boolean
		get() = (page + 1).toLong() * size < totalElements

	companion object {
		fun empty(page: Int, size: Int): AdminNoticePage =
			AdminNoticePage(AdminNoticeViews.empty(), page, size, 0)
	}
}
```

- [ ] **Step 6: 컴파일 검증**

Run: `./gradlew :oneulsogae-admin:compileKotlin -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add oneulsogae-admin/src/main/kotlin/com/org/oneulsogae/admin/common/error/AdminErrorCode.kt \
  oneulsogae-admin/src/main/kotlin/com/org/oneulsogae/admin/notice/query/dto/
git commit -m "feat(admin): 어드민 공지 조회 read model·에러코드 추가"
```

---

### Task 2: 어드민 공지 조회 포트·서비스 (query)

**Files:**
- Create: `oneulsogae-admin/src/main/kotlin/com/org/oneulsogae/admin/notice/query/dao/GetAdminNoticeDao.kt`
- Create: `oneulsogae-admin/src/main/kotlin/com/org/oneulsogae/admin/notice/query/service/port/in/GetAdminNoticesUseCase.kt`
- Create: `oneulsogae-admin/src/main/kotlin/com/org/oneulsogae/admin/notice/query/service/GetAdminNoticesService.kt`

**Interfaces:**
- Consumes: `AdminNoticeViews`, `AdminNoticeDetailView`, `AdminNoticePage` (Task 1), `AdminErrorCode.NOTICE_NOT_FOUND` (Task 1), 기존 `AdminException`.
- Produces:
  - `GetAdminNoticeDao`: `fun findPage(offset: Long, limit: Int): AdminNoticeViews`, `fun count(): Long`, `fun findDetailById(id: Long): AdminNoticeDetailView?`
  - `GetAdminNoticesUseCase`: `fun getNotices(page: Int, size: Int): AdminNoticePage`, `fun getNotice(id: Long): AdminNoticeDetailView`

- [ ] **Step 1: `GetAdminNoticeDao` (query out-port) 생성**

```kotlin
package com.org.oneulsogae.admin.notice.query.dao

import com.org.oneulsogae.admin.notice.query.dto.AdminNoticeDetailView
import com.org.oneulsogae.admin.notice.query.dto.AdminNoticeViews

/** 어드민 공지 조회 dao(query out-port). */
interface GetAdminNoticeDao {

	/** 공지를 최신순(created_at desc, id desc)으로 [offset]부터 [limit]건 조회한다. */
	fun findPage(offset: Long, limit: Int): AdminNoticeViews

	/** (soft delete 제외) 전체 공지 개수. (페이징 메타데이터 계산용) */
	fun count(): Long

	/** 공지 상세를 [id]로 조회한다. 없거나 soft-delete면 null. */
	fun findDetailById(id: Long): AdminNoticeDetailView?
}
```

- [ ] **Step 2: `GetAdminNoticesUseCase` (in-port) 생성**

```kotlin
package com.org.oneulsogae.admin.notice.query.service.port.`in`

import com.org.oneulsogae.admin.notice.query.dto.AdminNoticeDetailView
import com.org.oneulsogae.admin.notice.query.dto.AdminNoticePage

/** 어드민 공지 조회 유스케이스. (조회 전용) */
interface GetAdminNoticesUseCase {

	/** 공지를 최신순으로 page(0부터)·size 페이징 조회한다. */
	fun getNotices(page: Int, size: Int): AdminNoticePage

	/** 공지 상세를 id로 조회한다. 없으면 NOTICE_NOT_FOUND. */
	fun getNotice(id: Long): AdminNoticeDetailView
}
```

- [ ] **Step 3: `GetAdminNoticesService` 구현**

```kotlin
package com.org.oneulsogae.admin.notice.query.service

import com.org.oneulsogae.admin.common.error.AdminErrorCode
import com.org.oneulsogae.admin.common.error.AdminException
import com.org.oneulsogae.admin.notice.query.dao.GetAdminNoticeDao
import com.org.oneulsogae.admin.notice.query.dto.AdminNoticeDetailView
import com.org.oneulsogae.admin.notice.query.dto.AdminNoticePage
import com.org.oneulsogae.admin.notice.query.dto.AdminNoticeViews
import com.org.oneulsogae.admin.notice.query.service.port.`in`.GetAdminNoticesUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [GetAdminNoticesUseCase] 구현. (조회 전용 - 쓰기 부수효과 없음)
 * 공지를 저장 날짜(생성 시각) 최신순으로 limit/offset(page·size) 페이징 조회하고,
 * 전체 개수를 함께 조회해 페이지 메타데이터([AdminNoticePage])를 구성한다.
 */
@Service
@Transactional(readOnly = true)
class GetAdminNoticesService(
	private val getAdminNoticeDao: GetAdminNoticeDao,
) : GetAdminNoticesUseCase {

	override fun getNotices(page: Int, size: Int): AdminNoticePage {
		val pageNumber: Int = page.coerceAtLeast(0)
		val pageSize: Int = size.coerceIn(1, MAX_PAGE_SIZE)
		val offset: Long = pageNumber.toLong() * pageSize
		val notices: AdminNoticeViews = getAdminNoticeDao.findPage(offset, pageSize)
		return AdminNoticePage(
			content = notices,
			page = pageNumber,
			size = pageSize,
			totalElements = getAdminNoticeDao.count(),
		)
	}

	override fun getNotice(id: Long): AdminNoticeDetailView =
		getAdminNoticeDao.findDetailById(id)
			?: throw AdminException(AdminErrorCode.NOTICE_NOT_FOUND, "공지를 찾을 수 없습니다: $id")

	companion object {
		private const val MAX_PAGE_SIZE: Int = 100
	}
}
```

- [ ] **Step 4: 컴파일 검증**

Run: `./gradlew :oneulsogae-admin:compileKotlin -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add oneulsogae-admin/src/main/kotlin/com/org/oneulsogae/admin/notice/query/dao/ \
  oneulsogae-admin/src/main/kotlin/com/org/oneulsogae/admin/notice/query/service/
git commit -m "feat(admin): 어드민 공지 조회 유스케이스·서비스·포트 추가"
```

---

### Task 3: 어드민 공지 조회 QueryDSL 어댑터 (infra)

**Files:**
- Create: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/notice/query/GetAdminNoticeDaoImpl.kt`

**Interfaces:**
- Consumes: `GetAdminNoticeDao` (Task 2), `AdminNoticeView`/`AdminNoticeViews`/`AdminNoticeDetailView` (Task 1), 기존 `QNoticeEntity`, `JPAQueryFactory`.
- Produces: `GetAdminNoticeDaoImpl` (`@Component`, `GetAdminNoticeDao` 구현).

> 참고: `oneulsogae-infra`는 이미 `oneulsogae-admin`에 의존한다(`GetAdminCompanyVerificationDaoImpl`이 어드민 포트를 구현 중). 별도 의존성 추가 불필요. `QNoticeEntity`는 기존 `NoticeEntity`에서 이미 생성된다.

- [ ] **Step 1: `GetAdminNoticeDaoImpl` 생성**

```kotlin
package com.org.oneulsogae.infra.notice.query

import com.org.oneulsogae.admin.notice.query.dao.GetAdminNoticeDao
import com.org.oneulsogae.admin.notice.query.dto.AdminNoticeDetailView
import com.org.oneulsogae.admin.notice.query.dto.AdminNoticeView
import com.org.oneulsogae.admin.notice.query.dto.AdminNoticeViews
import com.org.oneulsogae.infra.notice.command.entity.QNoticeEntity
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * [GetAdminNoticeDao]의 QueryDSL 구현. (조회 전용)
 * 공지를 저장 날짜(created_at) 내림차순(동률이면 id 내림차순)으로 offset/limit 페이징해 read model에 직접 투영한다.
 * (soft delete 행은 @SQLRestriction으로 양쪽 쿼리에서 제외)
 * 저장 out-port는 [com.org.oneulsogae.infra.notice.command.adapter.NoticeAdapter]가 따로 구현한다.
 */
@Component
class GetAdminNoticeDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetAdminNoticeDao {

	override fun findPage(offset: Long, limit: Int): AdminNoticeViews {
		val notice: QNoticeEntity = QNoticeEntity.noticeEntity
		val views: List<AdminNoticeView> = queryFactory
			.select(
				Projections.constructor(
					AdminNoticeView::class.java,
					notice.id,
					notice.title,
					notice.createdAt,
				),
			)
			.from(notice)
			.orderBy(notice.createdAt.desc(), notice.id.desc())
			.offset(offset)
			.limit(limit.toLong())
			.fetch()
		return AdminNoticeViews(views)
	}

	override fun count(): Long {
		val notice: QNoticeEntity = QNoticeEntity.noticeEntity
		return queryFactory
			.select(notice.count())
			.from(notice)
			.fetchOne() ?: 0L
	}

	override fun findDetailById(id: Long): AdminNoticeDetailView? {
		val notice: QNoticeEntity = QNoticeEntity.noticeEntity
		return queryFactory
			.select(
				Projections.constructor(
					AdminNoticeDetailView::class.java,
					notice.id,
					notice.title,
					notice.description,
					notice.createdAt,
				),
			)
			.from(notice)
			.where(notice.id.eq(id))
			.fetchOne()
	}
}
```

- [ ] **Step 2: 컴파일 검증**

Run: `./gradlew :oneulsogae-infra:compileKotlin -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/notice/query/GetAdminNoticeDaoImpl.kt
git commit -m "feat(admin): 어드민 공지 조회 QueryDSL 어댑터 추가"
```

---

### Task 4: 어드민 공지 추가 도메인·포트·서비스 (command)

**Files:**
- Create: `oneulsogae-admin/src/main/kotlin/com/org/oneulsogae/admin/notice/command/domain/AdminNotice.kt`
- Create: `oneulsogae-admin/src/main/kotlin/com/org/oneulsogae/admin/notice/command/application/port/in/command/CreateAdminNoticeCommand.kt`
- Create: `oneulsogae-admin/src/main/kotlin/com/org/oneulsogae/admin/notice/command/application/port/in/CreateAdminNoticeUseCase.kt`
- Create: `oneulsogae-admin/src/main/kotlin/com/org/oneulsogae/admin/notice/command/application/port/out/SaveAdminNoticePort.kt`
- Create: `oneulsogae-admin/src/main/kotlin/com/org/oneulsogae/admin/notice/command/application/CreateAdminNoticeService.kt`

**Interfaces:**
- Produces:
  - `AdminNotice(id: Long = 0, title: String, description: String)` + `companion object { fun create(title: String, description: String): AdminNotice }`
  - `CreateAdminNoticeCommand(title: String, description: String)`
  - `CreateAdminNoticeUseCase`: `fun create(command: CreateAdminNoticeCommand)`
  - `SaveAdminNoticePort`: `fun save(notice: AdminNotice)`
  - `CreateAdminNoticeService` (`@Service @Transactional`)

- [ ] **Step 1: `AdminNotice` 도메인 생성 (최소, 검증 없음 — core Notice 관례)**

```kotlin
package com.org.oneulsogae.admin.notice.command.domain

/**
 * 어드민이 등록하는 공지 도메인 모델(명령 측). 제목·설명만 담는다.
 * 저장 날짜는 별도 필드 없이 영속성의 created_at(JPA Auditing)으로 갈음한다.
 * (admin은 core에 의존하지 않으므로 core Notice를 쓰지 않고 자체 최소 모델을 둔다)
 * 입력 검증은 요청 DTO(CreateAdminNoticeRequest)에서 처리한다.
 */
data class AdminNotice(
	val id: Long = 0,
	val title: String,
	val description: String,
) {
	companion object {
		fun create(title: String, description: String): AdminNotice =
			AdminNotice(title = title, description = description)
	}
}
```

- [ ] **Step 2: `CreateAdminNoticeCommand` 생성**

```kotlin
package com.org.oneulsogae.admin.notice.command.application.port.`in`.command

/** 어드민 공지 생성 입력. (저장 날짜는 created_at으로 자동 기록) */
data class CreateAdminNoticeCommand(
	val title: String,
	val description: String,
)
```

- [ ] **Step 3: `CreateAdminNoticeUseCase` (in-port) 생성**

```kotlin
package com.org.oneulsogae.admin.notice.command.application.port.`in`

import com.org.oneulsogae.admin.notice.command.application.port.`in`.command.CreateAdminNoticeCommand

/** 어드민 공지 생성 유스케이스. */
interface CreateAdminNoticeUseCase {
	/** [command] 내용으로 공지를 생성·저장한다. */
	fun create(command: CreateAdminNoticeCommand)
}
```

- [ ] **Step 4: `SaveAdminNoticePort` (out-port) 생성**

```kotlin
package com.org.oneulsogae.admin.notice.command.application.port.out

import com.org.oneulsogae.admin.notice.command.domain.AdminNotice

/** 어드민 공지 저장 out-port. infra 어댑터가 구현한다. */
fun interface SaveAdminNoticePort {
	fun save(notice: AdminNotice)
}
```

- [ ] **Step 5: `CreateAdminNoticeService` 구현**

```kotlin
package com.org.oneulsogae.admin.notice.command.application

import com.org.oneulsogae.admin.notice.command.application.port.`in`.CreateAdminNoticeUseCase
import com.org.oneulsogae.admin.notice.command.application.port.`in`.command.CreateAdminNoticeCommand
import com.org.oneulsogae.admin.notice.command.application.port.out.SaveAdminNoticePort
import com.org.oneulsogae.admin.notice.command.domain.AdminNotice
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [CreateAdminNoticeUseCase] 구현. [command]로 공지([AdminNotice])를 만들어 저장한다.
 * 저장 날짜는 영속성의 created_at(JPA Auditing)으로 자동 기록된다.
 */
@Service
@Transactional
class CreateAdminNoticeService(
	private val saveAdminNoticePort: SaveAdminNoticePort,
) : CreateAdminNoticeUseCase {

	override fun create(command: CreateAdminNoticeCommand) {
		saveAdminNoticePort.save(AdminNotice.create(title = command.title, description = command.description))
	}
}
```

- [ ] **Step 6: 컴파일 검증**

Run: `./gradlew :oneulsogae-admin:compileKotlin -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add oneulsogae-admin/src/main/kotlin/com/org/oneulsogae/admin/notice/command/
git commit -m "feat(admin): 어드민 공지 추가 도메인·유스케이스·포트 추가"
```

---

### Task 5: 어드민 공지 저장 어댑터 (infra)

**Files:**
- Create: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/notice/command/mapper/AdminNoticeMapper.kt`
- Modify: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/notice/command/adapter/NoticeAdapter.kt`

**Interfaces:**
- Consumes: `SaveAdminNoticePort`, `AdminNotice` (Task 4), 기존 `NoticeEntity`, `NoticeJpaRepository`.
- Produces: `AdminNotice.toEntity(): NoticeEntity` 확장함수; `NoticeAdapter`가 `SaveAdminNoticePort`도 구현.

> 엔티티당 어댑터 하나 규칙: `NoticeEntity`의 어댑터인 `NoticeAdapter`가 core `SaveNoticePort`와 admin `SaveAdminNoticePort`를 함께 구현한다.

- [ ] **Step 1: `AdminNoticeMapper` (AdminNotice → NoticeEntity) 생성**

```kotlin
package com.org.oneulsogae.infra.notice.command.mapper

import com.org.oneulsogae.admin.notice.command.domain.AdminNotice
import com.org.oneulsogae.infra.notice.command.entity.NoticeEntity

fun AdminNotice.toEntity(): NoticeEntity =
	NoticeEntity(
		title = title,
		description = description,
	).also { if (id != 0L) it.id = id }
```

- [ ] **Step 2: `NoticeAdapter`가 `SaveAdminNoticePort`도 구현하도록 수정**

기존 파일 전체를 아래로 교체:

```kotlin
package com.org.oneulsogae.infra.notice.command.adapter

import com.org.oneulsogae.admin.notice.command.application.port.out.SaveAdminNoticePort
import com.org.oneulsogae.admin.notice.command.domain.AdminNotice
import com.org.oneulsogae.core.notice.command.application.port.out.SaveNoticePort
import com.org.oneulsogae.core.notice.command.domain.Notice
import com.org.oneulsogae.infra.notice.command.mapper.toDomain
import com.org.oneulsogae.infra.notice.command.mapper.toEntity
import com.org.oneulsogae.infra.notice.command.repository.NoticeJpaRepository
import org.springframework.stereotype.Component

/**
 * [NoticeEntity]의 command 영속성 어댑터. (엔티티당 어댑터 하나)
 * 유저용 저장 out-port([SaveNoticePort])와 어드민 저장 out-port([SaveAdminNoticePort])를 함께 구현한다.
 * 공지 조회는 [com.org.oneulsogae.infra.notice.query.GetNoticeDaoImpl]·[com.org.oneulsogae.infra.notice.query.GetAdminNoticeDaoImpl]가 따로 담당한다.
 */
@Component
class NoticeAdapter(
	private val noticeJpaRepository: NoticeJpaRepository,
) : SaveNoticePort, SaveAdminNoticePort {

	override fun save(notice: Notice): Notice =
		noticeJpaRepository.save(notice.toEntity()).toDomain()

	override fun save(notice: AdminNotice) {
		noticeJpaRepository.save(notice.toEntity())
	}
}
```

> 참고: `Notice.toEntity()`와 `AdminNotice.toEntity()`는 파라미터 타입이 달라 오버로드 충돌이 없다. `save(Notice)`/`save(AdminNotice)`도 시그니처가 달라 각 인터페이스를 정상 구현한다.

- [ ] **Step 3: 컴파일 검증**

Run: `./gradlew :oneulsogae-infra:compileKotlin -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/notice/command/mapper/AdminNoticeMapper.kt \
  oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/notice/command/adapter/NoticeAdapter.kt
git commit -m "feat(admin): NoticeAdapter에 어드민 공지 저장 포트 구현 추가"
```

---

### Task 6: API 컨트롤러·DTO + E2E 테스트

**Files:**
- Create: `oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/admin/request/CreateAdminNoticeRequest.kt`
- Create: `oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/admin/response/AdminNoticeResponse.kt`
- Create: `oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/admin/response/AdminNoticePageResponse.kt`
- Create: `oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/admin/response/AdminNoticeDetailResponse.kt`
- Create: `oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/admin/AdminNoticeController.kt`
- Create: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/admin/AdminNoticeE2ETest.kt`
- Create: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/notice/AdminNoticePageTest.kt`

**Interfaces:**
- Consumes: `GetAdminNoticesUseCase`, `CreateAdminNoticeUseCase`, `CreateAdminNoticeCommand`, `AdminNoticePage`, `AdminNoticeView`, `AdminNoticeViews`, `AdminNoticeDetailView` (Tasks 1·2·4), core `ApiResponse`.
- 기존 테스트 유틸: `AbstractIntegrationSupport`, `IntegrationUtil.persist/deleteAll`, `NoticeEntityFixture`, `QNoticeEntity`, `adminAccessTokenFor`, `accessTokenFor`, `get`/`post`/`expect`/`bearer`/`jsonBody`.

- [ ] **Step 1: E2E 테스트 먼저 작성 (실패 확인용)**

`oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/admin/AdminNoticeE2ETest.kt`:

```kotlin
package com.org.oneulsogae.api.admin

import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.expect
import com.org.oneulsogae.common.integration.get
import com.org.oneulsogae.common.integration.post
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.fixture.NoticeEntityFixture
import com.org.oneulsogae.infra.notice.command.entity.QNoticeEntity
import org.hamcrest.Matchers.hasSize

/**
 * 어드민 공지 API E2E 테스트.
 * - GET /admin/v1/notices: 최신순(created_at desc, id desc) 페이징. 목록 행은 id/title/createdAt만.
 * - GET /admin/v1/notices/{id}: 상세(본문 포함), 없는 id 404(NOTICE-001).
 * - POST /admin/v1/notices: 공지 추가 후 재조회로 확인, 잘못된 입력 400.
 */
class AdminNoticeE2ETest : AbstractIntegrationSupport({

	describe("GET /admin/v1/notices") {

		it("최신순으로 페이징 조회하고 목록 행은 id/title/createdAt만 노출한다") {
			IntegrationUtil.persist(NoticeEntityFixture.create(title = "첫 공지", description = "본문1"))
			val lastId: Long = IntegrationUtil.persist(
				NoticeEntityFixture.create(title = "둘째 공지", description = "본문2"),
			).id!!

			get("/admin/v1/notices") {
				bearer(adminAccessTokenFor(9901L))
			} expect {
				status(200)
				body("success", true)
				body("data.totalElements", 2)
				body("data.content", hasSize<Any>(2))
				// 최신순: 마지막 저장분이 먼저.
				body("data.content[0].id", lastId.toInt())
				body("data.content[0].title", "둘째 공지")
				// 목록 행에 본문(description)은 없다.
				body("data.content[0].description", null)
			}
		}

		it("size로 페이지 크기를 제한한다") {
			(1..3).forEach { index: Int ->
				IntegrationUtil.persist(NoticeEntityFixture.create(title = "공지-$index"))
			}

			get("/admin/v1/notices?page=0&size=2") {
				bearer(adminAccessTokenFor(9901L))
			} expect {
				status(200)
				body("data.content", hasSize<Any>(2))
				body("data.size", 2)
				body("data.totalElements", 3)
				body("data.totalPages", 2)
				body("data.hasNext", true)
			}
		}
	}

	describe("GET /admin/v1/notices/{id}") {

		it("공지 상세를 본문과 함께 반환한다 (200)") {
			val id: Long = IntegrationUtil.persist(
				NoticeEntityFixture.create(title = "상세 공지", description = "상세 본문"),
			).id!!

			get("/admin/v1/notices/$id") {
				bearer(adminAccessTokenFor(9901L))
			} expect {
				status(200)
				body("data.id", id.toInt())
				body("data.title", "상세 공지")
				body("data.description", "상세 본문")
			}
		}

		it("없는 id면 404다 (NOTICE-001)") {
			get("/admin/v1/notices/999999") {
				bearer(adminAccessTokenFor(9901L))
			} expect {
				status(404)
				body("success", false)
				body("error.code", "NOTICE-001")
			}
		}
	}

	describe("POST /admin/v1/notices") {

		it("공지를 추가하면 목록에서 조회된다 (200)") {
			post("/admin/v1/notices") {
				bearer(adminAccessTokenFor(9901L))
				jsonBody("""{"title":"신규 공지","description":"신규 본문"}""")
			} expect {
				status(200)
				body("success", true)
			}

			get("/admin/v1/notices") {
				bearer(adminAccessTokenFor(9901L))
			} expect {
				status(200)
				body("data.totalElements", 1)
				body("data.content[0].title", "신규 공지")
			}
		}

		it("제목이 비면 400이다") {
			post("/admin/v1/notices") {
				bearer(adminAccessTokenFor(9901L))
				jsonBody("""{"title":"","description":"본문"}""")
			} expect {
				status(400)
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QNoticeEntity.noticeEntity)
	}
})
```

- [ ] **Step 2: E2E 실행해 실패 확인 (컨트롤러/DTO 미존재)**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.admin.AdminNoticeE2ETest" -q`
Expected: 컴파일 실패 또는 테스트 실패 (컨트롤러·응답 DTO 없음)

- [ ] **Step 3: `CreateAdminNoticeRequest` 생성**

```kotlin
package com.org.oneulsogae.api.admin.request

import com.org.oneulsogae.admin.notice.command.application.port.`in`.command.CreateAdminNoticeCommand
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CreateAdminNoticeRequest(
	@field:NotBlank(message = "공지 제목은 필수입니다.")
	@field:Size(max = 200, message = "공지 제목은 200자 이하여야 합니다.")
	val title: String? = null,

	@field:NotBlank(message = "공지 설명은 필수입니다.")
	@field:Size(max = 2000, message = "공지 설명은 2000자 이하여야 합니다.")
	val description: String? = null,
) {
	// @Valid 통과 후 호출 → 필수 필드 non-null/non-blank 보장 → command로 변환
	fun toCommand(): CreateAdminNoticeCommand =
		CreateAdminNoticeCommand(
			title = title!!,
			description = description!!,
		)
}
```

- [ ] **Step 4: `AdminNoticeResponse` (목록 행) 생성**

```kotlin
package com.org.oneulsogae.api.admin.response

import com.org.oneulsogae.admin.notice.query.dto.AdminNoticeView
import com.org.oneulsogae.admin.notice.query.dto.AdminNoticeViews
import java.time.LocalDateTime

/** 어드민 공지 목록 항목 응답. 본문(description)은 상세에서만 노출한다. */
data class AdminNoticeResponse(
	val id: Long,
	val title: String,
	val createdAt: LocalDateTime?,
) {
	companion object {
		private fun of(view: AdminNoticeView): AdminNoticeResponse =
			AdminNoticeResponse(
				id = view.id,
				title = view.title,
				createdAt = view.createdAt,
			)

		fun listOf(views: AdminNoticeViews): List<AdminNoticeResponse> =
			views.values.map { view: AdminNoticeView -> of(view) }
	}
}
```

- [ ] **Step 5: `AdminNoticePageResponse` 생성**

```kotlin
package com.org.oneulsogae.api.admin.response

import com.org.oneulsogae.admin.notice.query.dto.AdminNoticePage

/** 어드민 공지 목록 페이지 응답. (offset 페이징) */
data class AdminNoticePageResponse(
	val content: List<AdminNoticeResponse>,
	val page: Int,
	val size: Int,
	val totalElements: Long,
	val totalPages: Int,
	val hasNext: Boolean,
) {
	companion object {
		fun of(page: AdminNoticePage): AdminNoticePageResponse =
			AdminNoticePageResponse(
				content = AdminNoticeResponse.listOf(page.content),
				page = page.page,
				size = page.size,
				totalElements = page.totalElements,
				totalPages = page.totalPages,
				hasNext = page.hasNext,
			)
	}
}
```

- [ ] **Step 6: `AdminNoticeDetailResponse` 생성**

```kotlin
package com.org.oneulsogae.api.admin.response

import com.org.oneulsogae.admin.notice.query.dto.AdminNoticeDetailView
import java.time.LocalDateTime

/** 어드민 공지 상세 응답. 목록 필드 + 본문(description). */
data class AdminNoticeDetailResponse(
	val id: Long,
	val title: String,
	val description: String,
	val createdAt: LocalDateTime?,
) {
	companion object {
		fun of(view: AdminNoticeDetailView): AdminNoticeDetailResponse =
			AdminNoticeDetailResponse(
				id = view.id,
				title = view.title,
				description = view.description,
				createdAt = view.createdAt,
			)
	}
}
```

- [ ] **Step 7: `AdminNoticeController` 생성**

```kotlin
package com.org.oneulsogae.api.admin

import com.org.oneulsogae.admin.notice.command.application.port.`in`.CreateAdminNoticeUseCase
import com.org.oneulsogae.admin.notice.query.service.port.`in`.GetAdminNoticesUseCase
import com.org.oneulsogae.api.admin.request.CreateAdminNoticeRequest
import com.org.oneulsogae.api.admin.response.AdminNoticeDetailResponse
import com.org.oneulsogae.api.admin.response.AdminNoticePageResponse
import com.org.oneulsogae.core.common.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 어드민 공지 엔드포인트. `/admin` 하위는 SecurityConfig의 hasRole(ADMIN)으로 보호된다.
 * - GET /: 최신순 page·size 페이징 목록 (본문 제외).
 * - GET /{id}: 상세(본문 포함). 없으면 404(NOTICE-001).
 * - POST /: 제목·설명으로 공지 추가.
 */
@Tag(name = "어드민 공지", description = "어드민 백오피스 공지 조회·등록. ROLE_ADMIN 토큰만 접근할 수 있다.")
@RestController
@RequestMapping("/admin/v1/notices")
class AdminNoticeController(
	private val getAdminNoticesUseCase: GetAdminNoticesUseCase,
	private val createAdminNoticeUseCase: CreateAdminNoticeUseCase,
) {

	@Operation(
		summary = "공지 목록 조회",
		description = "공지를 저장 날짜(생성 시각) 최신순으로 page(0부터)·size 페이징 조회한다. 목록 항목은 본문(description)을 포함하지 않는다.",
	)
	@GetMapping
	fun notices(
		@RequestParam(defaultValue = "0") page: Int,
		@RequestParam(defaultValue = "20") size: Int,
	): ApiResponse<AdminNoticePageResponse> =
		ApiResponse.success(AdminNoticePageResponse.of(getAdminNoticesUseCase.getNotices(page, size)))

	@Operation(
		summary = "공지 상세 조회",
		description = "공지 한 건을 id로 조회한다(본문 포함). 없으면 404(NOTICE-001).",
	)
	@GetMapping("/{id}")
	fun notice(
		@PathVariable id: Long,
	): ApiResponse<AdminNoticeDetailResponse> =
		ApiResponse.success(AdminNoticeDetailResponse.of(getAdminNoticesUseCase.getNotice(id)))

	@Operation(
		summary = "공지 추가",
		description = "제목과 설명으로 공지를 등록한다. 저장 날짜는 생성 시각으로 자동 기록된다. 제목·설명이 비면 400.",
	)
	@PostMapping
	fun create(
		@RequestBody @Valid request: CreateAdminNoticeRequest,
	): ApiResponse<Unit> {
		createAdminNoticeUseCase.create(request.toCommand())
		return ApiResponse.success()
	}
}
```

- [ ] **Step 8: E2E 실행해 통과 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.admin.AdminNoticeE2ETest" -q`
Expected: PASS (모든 it 통과)

- [ ] **Step 9: `AdminNoticePage` 유닛 테스트 작성 (NoticePageTest 미러)**

`oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/notice/AdminNoticePageTest.kt`:

```kotlin
package com.org.oneulsogae.domain.notice

import com.org.oneulsogae.admin.notice.query.dto.AdminNoticePage
import com.org.oneulsogae.admin.notice.query.dto.AdminNoticeView
import com.org.oneulsogae.admin.notice.query.dto.AdminNoticeViews
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

/**
 * [AdminNoticePage] read model 유닛 테스트.
 * offset 페이징 메타데이터([AdminNoticePage.totalPages]/[AdminNoticePage.hasNext]) 계산을 검증한다.
 */
class AdminNoticePageTest : DescribeSpec({

	fun view(id: Long): AdminNoticeView =
		AdminNoticeView(id = id, title = "title-$id", createdAt = null)

	fun views(count: Int): AdminNoticeViews =
		AdminNoticeViews((1..count).map { view(it.toLong()) })

	describe("totalPages") {
		it("전체 개수를 size로 나눈 올림 값이다") {
			AdminNoticePage(content = views(2), page = 0, size = 2, totalElements = 5).totalPages shouldBe 3
		}

		it("전체 개수가 size로 나누어떨어지면 그 몫이다") {
			AdminNoticePage(content = views(2), page = 0, size = 2, totalElements = 4).totalPages shouldBe 2
		}

		it("전체가 0이면 0이다") {
			AdminNoticePage.empty(page = 0, size = 20).totalPages shouldBe 0
		}
	}

	describe("hasNext") {
		it("뒤에 페이지가 더 있으면 true다") {
			AdminNoticePage(content = views(2), page = 0, size = 2, totalElements = 5).hasNext shouldBe true
		}

		it("마지막(부분) 페이지면 false다") {
			AdminNoticePage(content = views(1), page = 2, size = 2, totalElements = 5).hasNext shouldBe false
		}

		it("정확히 꽉 찬 마지막 페이지면 false다") {
			AdminNoticePage(content = views(2), page = 1, size = 2, totalElements = 4).hasNext shouldBe false
		}
	}
})
```

- [ ] **Step 10: 유닛 테스트 실행해 통과 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.domain.notice.AdminNoticePageTest" -q`
Expected: PASS

- [ ] **Step 11: 전체 관련 테스트 재확인 + Commit**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.admin.AdminNoticeE2ETest" --tests "com.org.oneulsogae.domain.notice.AdminNoticePageTest" -q`
Expected: PASS

```bash
git add oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/admin/ \
  oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/admin/AdminNoticeE2ETest.kt \
  oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/notice/AdminNoticePageTest.kt
git commit -m "feat(admin): 어드민 공지 목록·상세·추가 API 및 E2E 추가"
```

---

## Self-Review

**Spec coverage:**
- 목록 페이징 API → Task 2(Service)·Task 3(DaoImpl)·Task 6(Controller/E2E). ✅
- 상세 조회 API(404 포함) → Task 1(NOTICE_NOT_FOUND)·Task 2(getNotice)·Task 3(findDetailById)·Task 6. ✅
- 추가 API(입력 검증 400) → Task 4(command/service)·Task 5(adapter)·Task 6(Request DTO @Valid/E2E). ✅
- 기존 `notices` 테이블/엔티티 재사용 → Task 3·5(QNoticeEntity/NoticeEntity 재사용, 신규 엔티티 없음). ✅
- read model 필드(목록 id/title/createdAt, 상세 +description) → Task 1 DTO, Task 6 Response. ✅
- self-contained(admin이 core 미의존) → 모든 admin 파일이 core import 없음. ✅
- 권한(비어드민 차단) → 기존 `AdminAccessE2ETest`가 `/admin/**` 인가를 이미 커버(401/403). 본 기능 컨트롤러는 경로만 `/admin/**`에 두면 자동 보호되므로 중복 테스트는 생략. ✅
- 유닛 테스트(Page 계산) → Task 6 Step 9. ✅
- 인덱스: 필터 없는 최신순 페이징이라 신규 인덱스 없음(spec 명시). ✅

**Placeholder scan:** TBD/TODO/"적절히 처리" 없음. 모든 코드 스텝에 완전한 코드 포함. ✅

**Type consistency:**
- `AdminNoticeViews.values: List<AdminNoticeView>` — Task 1 정의, Task 3(`AdminNoticeViews(views)`)·Task 6(`views.values.map`)에서 동일 사용. ✅
- `AdminNoticePage(content=..., page, size, totalElements)` + 파생 `totalPages`/`hasNext` — Task 1 정의, Task 2·6·유닛에서 동일 시그니처. ✅
- `SaveAdminNoticePort.save(notice: AdminNotice)` 반환 Unit — Task 4 정의, Task 5 구현, Task 4 Service 호출 일치. ✅
- `GetAdminNoticesUseCase.getNotices/getNotice` — Task 2 정의, Task 6 컨트롤러 호출 일치. ✅
- `AdminNoticeView` 3-arg(id, title, createdAt) — Task 1 정의, Task 3 Projections 3-arg 투영, Task 6 Response·유닛 생성 일치. ✅
- `AdminNoticeDetailView` 4-arg(id, title, description, createdAt) — Task 1 정의, Task 3 Projections 4-arg 투영 일치. ✅

이슈 없음.
