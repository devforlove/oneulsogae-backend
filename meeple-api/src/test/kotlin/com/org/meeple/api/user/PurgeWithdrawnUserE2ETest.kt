package com.org.meeple.api.user

import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.user.UserStatus
import com.org.meeple.core.user.command.application.port.`in`.PurgeWithdrawnUserUseCase
import com.org.meeple.core.user.command.application.port.`in`.RegisterUserUseCase
import com.org.meeple.core.user.command.domain.User
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.UserDetailEntityFixture
import com.org.meeple.infra.fixture.UserEntityFixture
import com.org.meeple.infra.user.command.entity.QUserDetailEntity
import com.org.meeple.infra.user.command.entity.QUserEntity
import com.org.meeple.scheduler.user.command.adapter.PurgeWithdrawnUserBatchJob
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime

/**
 * 유예(10일) 경과 탈퇴 계정 파기 배치 검증.
 * 11일 전 소프트삭제된 사용자를 배치가 익명화하면, 같은 provider/providerId로 재로그인 시
 * 복구되지 않고 새 계정이 생성된다(provider_id가 치환돼 복구 대상이 아님).
 */
class PurgeWithdrawnUserE2ETest : AbstractIntegrationSupport() {

    @Autowired private lateinit var purgeWithdrawnUserBatchJob: PurgeWithdrawnUserBatchJob
    @Autowired private lateinit var purgeWithdrawnUserUseCase: PurgeWithdrawnUserUseCase
    @Autowired private lateinit var registerUserUseCase: RegisterUserUseCase

    init {
        describe("탈퇴 계정 파기 배치") {

            it("유예 경과분을 익명화해 복구 불가·새 계정 가입이 된다") {
                // 11일 전에 탈퇴(소프트삭제)된 사용자. softDelete(at)으로 deleted_at을 과거로 박아 insert.
                val withdrawnAt: LocalDateTime = LocalDateTime.now().minusDays(11)
                val oldId: Long = IntegrationUtil.persist(
                    UserEntityFixture.create(
                        provider = "kakao",
                        providerId = "kakao-777",
                        email = "old@test.com",
                        status = UserStatus.ACTIVE,
                    ).also { it.softDelete(withdrawnAt) },
                ).id!!
                IntegrationUtil.persist(UserDetailEntityFixture.create(userId = oldId))

                purgeWithdrawnUserBatchJob.run()

                // 파기 직접 검증: email=null, provider_id 치환, status=WITHDRAWN
                val userRow: Array<Any?> = IntegrationUtil.nativeQuerySingleOrNull(
                    "select email, provider_id, status from users where id = $oldId"
                )!!
                userRow[0] shouldBe null                         // email 익명화
                userRow[1] shouldBe "withdrawn_$oldId"           // provider_id 치환
                userRow[2] shouldBe "WITHDRAWN"                  // status 파기 마커

                // user_details PII 익명화 검증 (닉네임 null)
                val detailRow: Array<Any?> = IntegrationUtil.nativeQuerySingleOrNull(
                    "select nickname, deleted_at from user_details where user_id = $oldId"
                )!!
                detailRow[0] shouldBe null      // nickname PII 제거됨
                detailRow[1] shouldNotBe null   // user_details도 소프트삭제됨

                // 파기 후: provider_id가 치환돼 복구 대상이 아니므로 같은 카카오로 신규 가입된다.
                val newUser: User = registerUserUseCase.registerIfAbsent("kakao", "kakao-777", "new@test.com", null)
                newUser.id shouldNotBe oldId
                newUser.status shouldBe UserStatus.IDENTITY_VERIFICATION_PENDING   // 복구가 아닌 신규 → 본인확인부터
            }

            it("활성(deleted_at=null) 계정은 파기 직접 호출에도 변경되지 않는다 — anonymizeById 가드 검증") {
                // 소프트삭제 없이 처음부터 활성 상태인 사용자.
                // 배치 SELECT(findUserIdsWithdrawnBefore)를 우회해 purge()를 직접 호출함으로써
                // anonymizeById의 WHERE 가드(deleted_at IS NOT NULL)를 직접 타격한다.
                val email: String = "active-guard@test.com"
                val providerId: String = "kakao-active-guard"
                val userId: Long = IntegrationUtil.persist(
                    UserEntityFixture.create(
                        provider = "kakao",
                        providerId = providerId,
                        email = email,
                        status = UserStatus.ACTIVE,
                    ),
                ).id!!
                IntegrationUtil.persist(UserDetailEntityFixture.create(userId = userId))

                // 배치 SELECT 없이 직접 purge() 호출 → anonymizeById 가드가 0행 반환해야 한다.
                purgeWithdrawnUserUseCase.purge(userId)

                // 가드가 UPDATE를 막아 users 행이 그대로여야 한다.
                val userRow: Array<Any?> = IntegrationUtil.nativeQuerySingleOrNull(
                    "select email, provider_id, status from users where id = $userId"
                )!!
                userRow[0] shouldBe email         // email 보존(익명화 안 됨)
                userRow[1] shouldBe providerId    // provider_id 원본(치환 안 됨)
                userRow[2] shouldBe "ACTIVE"      // status 원본

                // user 익명화 0행이면 user_details도 손대지 않는다(원자성 검증).
                val detailRow: Array<Any?> = IntegrationUtil.nativeQuerySingleOrNull(
                    "select nickname, deleted_at from user_details where user_id = $userId"
                )!!
                detailRow[0] shouldNotBe null   // nickname PII 보존
                detailRow[1] shouldBe null      // user_details 소프트삭제 안 됨
            }

            it("배치 적재 후 복구된(deleted_at=null) 사용자는 파기하지 않는다 (C1 회귀)") {
                // 11일 전 소프트삭제 상태로 적재한다.
                val withdrawnAt: LocalDateTime = LocalDateTime.now().minusDays(11)
                val userId: Long = IntegrationUtil.persist(
                    UserEntityFixture.create(
                        provider = "kakao",
                        providerId = "kakao-999",
                        email = "active@test.com",
                        status = UserStatus.ACTIVE,
                    ).also { it.softDelete(withdrawnAt) },
                ).id!!
                IntegrationUtil.persist(UserDetailEntityFixture.create(userId = userId))

                // 배치 적재 시점과 개별 파기 사이에 복구가 일어난 상황을 직접 모사:
                // @SQLRestriction 우회를 위해 네이티브 UPDATE로 deleted_at을 null로 되돌린다.
                IntegrationUtil.nativeUpdate("update users set deleted_at = null where id = $userId")

                // 배치 실행 — 복구된 행은 WHERE deleted_at IS NOT NULL 가드로 건드리지 않아야 한다.
                purgeWithdrawnUserBatchJob.run()

                // 검증: users 행이 활성(email 보존, status 원본, provider_id 원본) 상태여야 한다.
                val row: Array<Any?> = IntegrationUtil.nativeQuerySingleOrNull(
                    "select email, provider_id, status, deleted_at from users where id = $userId"
                )!!
                row[0] shouldBe "active@test.com"   // email 보존 (파기되지 않음)
                row[1] shouldBe "kakao-999"          // provider_id 원본
                row[2] shouldBe "ACTIVE"             // status 원본
                row[3] shouldBe null                 // deleted_at=null(활성)

                // user_details도 PII 보존 확인
                val detailRow: Array<Any?> = IntegrationUtil.nativeQuerySingleOrNull(
                    "select nickname, deleted_at from user_details where user_id = $userId"
                )!!
                detailRow[0] shouldNotBe null   // nickname 보존
                detailRow[1] shouldBe null      // 소프트삭제 안 됨
            }
        }

        afterTest {
            // deleteAll은 @Table name 기반 네이티브 DELETE라 익명화·소프트삭제 행도 모두 지워진다.
            IntegrationUtil.deleteAll(QUserDetailEntity.userDetailEntity)
            IntegrationUtil.deleteAll(QUserEntity.userEntity)
        }
    }
}
