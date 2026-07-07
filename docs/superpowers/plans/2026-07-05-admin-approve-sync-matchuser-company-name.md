# 어드민 승인 시 match_user.company_name 동기화 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 어드민이 회사 이미지 인증을 승인하며 회사명을 확정할 때 `user_details.company_name`뿐 아니라 `match_user.company_name`도 함께 갱신한다.

**Architecture:** admin이 자체 out-port `UpdateMatchUserCompanyNamePort`를 두고(core 비의존), infra `MatchUserAdapter`가 `MatchUserJpaRepository`의 `@Modifying` 부분갱신 쿼리로 구현한다(행 없으면 no-op). 서비스 `approve`가 이를 호출한다.

**Tech Stack:** Kotlin / Spring Data JPA(@Modifying) / QueryDSL / Kotest E2E.

## Global Constraints

- **`meeple-admin`은 `meeple-core`를 의존하지 않는다** (admin 소스 `com.org.meeple.core.*` 0건).
- match_user 갱신은 **단일 컬럼 부분갱신 + 행 없으면 no-op**(기존 `updateRefuseSameCompanyIntro` 관례). 예외 없음.
- 명령 서비스 `@Transactional`(user_details·match_user 원자적). 타입 명시. 커밋 형식 `<type>(admin): <설명>`.

---

## Task 1: admin out-port + infra 구현 + 서비스 배선

**Files:**
- Create: `meeple-admin/src/main/kotlin/com/org/meeple/admin/companyverification/command/application/port/out/UpdateMatchUserCompanyNamePort.kt`
- Modify: `meeple-admin/src/main/kotlin/com/org/meeple/admin/companyverification/command/application/ReviewCompanyImageVerificationService.kt`
- Modify: `meeple-infra/src/main/kotlin/com/org/meeple/infra/matchuser/command/repository/MatchUserJpaRepository.kt`
- Modify: `meeple-infra/src/main/kotlin/com/org/meeple/infra/matchuser/command/adapter/MatchUserAdapter.kt`

**Interfaces:**
- Produces: `UpdateMatchUserCompanyNamePort.updateCompanyName(userId: Long, companyName: String)`; `MatchUserJpaRepository.updateCompanyName(userId, companyName): Int`.

- [ ] **Step 1: admin out-port 작성**

`UpdateMatchUserCompanyNamePort.kt`:

```kotlin
package com.org.meeple.admin.companyverification.command.application.port.out

/**
 * 매칭 읽기 모델(match_user)의 회사명을 갱신하는 out-port.
 * (승인으로 유저 회사명이 바뀌면 같은-회사 소개 차단이 스테일해지지 않도록 match_user도 맞춘다. 행이 없으면 no-op)
 */
fun interface UpdateMatchUserCompanyNamePort {

	fun updateCompanyName(userId: Long, companyName: String)
}
```

- [ ] **Step 2: 리포지토리에 부분갱신 쿼리 추가**

`MatchUserJpaRepository.kt`의 `updateRefuseSameCompanyIntro(...)` 메서드 뒤에 추가:

```kotlin
	/** 회사명만 갱신한다. 영향 행 수를 반환한다(행이 없으면 0 = 미적재, 예외 없음). */
	@Modifying
	@Query(
		"""
		update MatchUserEntity m
		set m.companyName = :companyName
		where m.userId = :userId
		""",
	)
	fun updateCompanyName(@Param("userId") userId: Long, @Param("companyName") companyName: String): Int
```

- [ ] **Step 3: MatchUserAdapter가 admin 포트 구현**

`MatchUserAdapter.kt`의 import에 추가:

```kotlin
import com.org.meeple.admin.companyverification.command.application.port.out.UpdateMatchUserCompanyNamePort
```

클래스 선언의 구현 인터페이스 목록에 `UpdateMatchUserCompanyNamePort`를 추가한다:

```kotlin
) : GetMatchCandidatePort, SaveMatchUserPort, GetMatchUserPort, DeleteMatchUserPort, UpdateMatchUserCompanyNamePort {
```

클래스 본문(마지막 메서드 뒤, 클래스 닫는 `}` 앞)에 구현 추가:

```kotlin
	// admin 승인 동기화: 매칭 읽기 모델의 회사명을 맞춘다. (match_user 행이 없으면 0건 = no-op)
	override fun updateCompanyName(userId: Long, companyName: String) {
		matchUserJpaRepository.updateCompanyName(userId, companyName)
	}
```

- [ ] **Step 4: 서비스가 match_user도 갱신 + KDoc 정리**

`ReviewCompanyImageVerificationService.kt`의 import에 추가:

```kotlin
import com.org.meeple.admin.companyverification.command.application.port.out.UpdateMatchUserCompanyNamePort
```

클래스 KDoc에서 "[알려진 제약 — 보류] ..." 문단(6줄)을 제거하고 그 자리에 한 줄로 대체:

```kotlin
 * 승인 시 유저 프로필(user_details)과 매칭 읽기 모델(match_user)의 회사명을 함께 갱신해 같은-회사 소개 차단이 스테일해지지 않게 한다.
 * (match_user 행이 없는(온보딩) 유저는 no-op)
```

생성자에 포트를 주입한다:

```kotlin
class ReviewCompanyImageVerificationService(
	private val getCompanyImageVerificationPort: GetCompanyImageVerificationPort,
	private val saveCompanyImageVerificationPort: SaveCompanyImageVerificationPort,
	private val updateUserCompanyNamePort: UpdateUserCompanyNamePort,
	private val updateMatchUserCompanyNamePort: UpdateMatchUserCompanyNamePort,
) : ReviewCompanyImageVerificationUseCase {
```

`approve`의 `updateUserCompanyNamePort.updateCompanyName(...)` 뒤에 한 줄 추가:

```kotlin
		saveCompanyImageVerificationPort.save(verification.approve())
		updateUserCompanyNamePort.updateCompanyName(verification.userId, companyName)
		updateMatchUserCompanyNamePort.updateCompanyName(verification.userId, companyName)
	}
```

- [ ] **Step 5: 컴파일 확인 + core 비의존 확인**

Run: `./gradlew :meeple-api:compileKotlin -q`
Expected: 성공(exit 0).

Run: `grep -rn "com.org.meeple.core" meeple-admin/src --include="*.kt" | wc -l | tr -d ' '`
Expected: `0`

- [ ] **Step 6: 커밋**

```bash
git add meeple-admin/src/main/kotlin/com/org/meeple/admin/companyverification/command/application/port/out/UpdateMatchUserCompanyNamePort.kt \
        meeple-admin/src/main/kotlin/com/org/meeple/admin/companyverification/command/application/ReviewCompanyImageVerificationService.kt \
        meeple-infra/src/main/kotlin/com/org/meeple/infra/matchuser/command/repository/MatchUserJpaRepository.kt \
        meeple-infra/src/main/kotlin/com/org/meeple/infra/matchuser/command/adapter/MatchUserAdapter.kt
git commit -m "feat(admin): 회사 이미지 인증 승인 시 match_user.company_name 동기화

admin UpdateMatchUserCompanyNamePort(infra MatchUserAdapter 구현, @Modifying 부분갱신·행없으면 no-op)을
approve에 배선해 user_details와 match_user 회사명을 함께 갱신한다. (같은-회사 소개 차단 스테일 해소)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: E2E 검증

**Files:**
- Modify: `meeple-api/src/test/kotlin/com/org/meeple/api/admin/AdminCompanyVerificationReviewE2ETest.kt`

**Interfaces:**
- Consumes: `POST .../{id}/approve`, `MatchUserEntityFixture.create(userId, companyName, ...)`, `QMatchUserEntity`.

- [ ] **Step 1: match_user 동기화 E2E 케이스 추가**

`AdminCompanyVerificationReviewE2ETest.kt`의 import에 추가(없으면):

```kotlin
import com.org.meeple.infra.fixture.MatchUserEntityFixture
import com.org.meeple.infra.matchuser.command.entity.MatchUserEntity
import com.org.meeple.infra.matchuser.command.entity.QMatchUserEntity
```

파일 상단 헬퍼(`verificationById`/`detailByUserId` 근처)에 추가:

```kotlin
	fun matchUserByUserId(userId: Long): MatchUserEntity {
		val m: QMatchUserEntity = QMatchUserEntity.matchUserEntity
		return IntegrationUtil.getQuery().selectFrom(m).where(m.userId.eq(userId)).fetchOne()!!
	}
```

approve `describe` 블록에 케이스 추가:

```kotlin
		it("승인 시 match_user 회사명도 함께 갱신한다") {
			val userId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "civ-matchuser")).id!!
			IntegrationUtil.persist(UserDetailEntityFixture.create(userId = userId, nickname = "인증유저", companyName = null))
			IntegrationUtil.persist(MatchUserEntityFixture.create(userId = userId, companyName = "이전회사"))
			val id: Long = IntegrationUtil.persist(
				CompanyImageVerificationEntityFixture.create(
					userId = userId,
					imageKey = "matchuser-key",
					status = CompanyImageVerificationStatus.PENDING,
				),
			).id!!

			post("/admin/v1/company-image-verifications/$id/approve") {
				bearer(adminAccessTokenFor(9901L))
				jsonBody("""{"companyName":"미플"}""")
			} expect {
				status(200)
			}

			detailByUserId(userId).companyName shouldBe "미플"
			matchUserByUserId(userId).companyName shouldBe "미플"
		}
```

`afterTest` 블록에 match_user 정리를 추가한다(company_image_verifications 삭제 뒤, users 삭제 앞 등 적절한 위치):

```kotlin
		IntegrationUtil.deleteAll(QMatchUserEntity.matchUserEntity)
```

- [ ] **Step 2: E2E 실행**

Run: `./gradlew :meeple-api:test --tests "com.org.meeple.api.admin.AdminCompanyVerificationReviewE2ETest" -q`
Expected: PASS(신규 케이스 포함 7). 실패 시: match_user 갱신 안 되면 어댑터/쿼리·서비스 호출 점검; match_user 행 없이도 기존 승인 케이스가 통과하는지(no-op) 확인. 확신 안 서면 BLOCKED.

- [ ] **Step 3: 전체 빌드 확인**

Run: `./gradlew build -q`
Expected: 성공(exit 0).

- [ ] **Step 4: 커밋**

```bash
git add meeple-api/src/test/kotlin/com/org/meeple/api/admin/AdminCompanyVerificationReviewE2ETest.kt
git commit -m "test(admin): 승인 시 match_user 회사명 동기화 E2E 추가

match_user 행이 있는 유저를 승인하면 user_details와 match_user 회사명이 함께 바뀜을 검증한다.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## 참고: 메모리 정리 (컨트롤러(사람)가 별도 수행)

구현·검증 완료 후 보류 메모리를 제거한다(이슈 해소): `admin-company-image-approve-matchuser-stale-deferred.md` 삭제 + `MEMORY.md`에서 해당 줄 제거. (서브에이전트 태스크가 아니라 컨트롤러가 처리)

## Self-Review 결과

- **Spec coverage**: out-port(Task 1 Step 1)·infra 쿼리/어댑터(Step 2·3)·서비스 배선+KDoc(Step 4)·no-op(쿼리 반환 Int, 행 없으면 0)·E2E(Task 2)·admin core 비의존(Task 1 Step 5)·메모리 정리(참고) 모두 커버.
- **Placeholder scan**: 실제 코드. TODO 없음.
- **Type consistency**: `UpdateMatchUserCompanyNamePort.updateCompanyName(userId: Long, companyName: String)` / `MatchUserJpaRepository.updateCompanyName(userId, companyName): Int` / 서비스 주입 필드명 `updateMatchUserCompanyNamePort` 일치.
