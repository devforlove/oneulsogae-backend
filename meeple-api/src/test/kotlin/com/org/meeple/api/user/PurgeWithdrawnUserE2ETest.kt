package com.org.meeple.api.user

import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.user.UserStatus
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

                // 파기 후: provider_id가 치환돼 복구 대상이 아니므로 같은 카카오로 신규 가입된다.
                val newUser: User = registerUserUseCase.registerIfAbsent("kakao", "kakao-777", "new@test.com", null)
                newUser.id shouldNotBe oldId
                newUser.status shouldBe UserStatus.ONBOARDING   // 복구가 아닌 신규 → 온보딩부터
            }
        }

        afterTest {
            // deleteAll은 @Table name 기반 네이티브 DELETE라 익명화·소프트삭제 행도 모두 지워진다.
            IntegrationUtil.deleteAll(QUserDetailEntity.userDetailEntity)
            IntegrationUtil.deleteAll(QUserEntity.userEntity)
        }
    }
}
