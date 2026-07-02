package com.org.meeple.api.scheduler

import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.user.Gender
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.MatchUserEntityFixture
import com.org.meeple.infra.matchuser.command.entity.QMatchUserEntity
import com.org.meeple.infra.solomatch.command.entity.QSoloMatchEntity
import com.org.meeple.infra.solomatch.command.entity.QSoloMatchMemberEntity
import com.org.meeple.scheduler.solomatch.command.application.port.`in`.RunSoloMatchBatchUseCase
import io.kotest.matchers.shouldBe

/**
 * 일일 매칭 배치의 "유저당 하루 1회 소개" 재실행 멱등성 통합 테스트.
 * 같은 날 배치가 여러 번 돌아도(로컬 1분 크론·수동 재트리거) 오늘 이미 소개된 유저는
 * `findUserIdsIntroducedOn`으로 제외되어, 다른 상대와도 추가 소개가 생기지 않아야 한다.
 */
class SoloMatchBatchDailyOnceIntegrationTest(
	private val runSoloMatchBatchUseCase: RunSoloMatchBatchUseCase,
) : AbstractIntegrationSupport({

	describe("일일 매칭 배치를 같은 날 두 번 실행하면") {

		it("첫 실행에서 소개된 유저는 두 번째 실행에서 다른 상대와도 재소개되지 않는다") {
			// 남 1명 + 여 2명: 첫 실행에서 남성이 한 명과 소개되고, 제외가 깨져 있으면 두 번째 실행에서 남은 여성과 또 소개된다.
			IntegrationUtil.persist(MatchUserEntityFixture.create(userId = 9501L, gender = Gender.MALE, regionId = 1L))
			IntegrationUtil.persist(MatchUserEntityFixture.create(userId = 9502L, gender = Gender.FEMALE, regionId = 1L))
			IntegrationUtil.persist(MatchUserEntityFixture.create(userId = 9503L, gender = Gender.FEMALE, regionId = 1L))

			val first = runSoloMatchBatchUseCase.run()
			first.recommended shouldBe 1

			val second = runSoloMatchBatchUseCase.run()
			second.recommended shouldBe 0

			val matchCount: Int = IntegrationUtil.getQuery()
				.selectFrom(QSoloMatchEntity.soloMatchEntity)
				.fetch()
				.size
			matchCount shouldBe 1
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QSoloMatchMemberEntity.soloMatchMemberEntity)
		IntegrationUtil.deleteAll(QSoloMatchEntity.soloMatchEntity)
		IntegrationUtil.deleteAll(QMatchUserEntity.matchUserEntity)
	}
})
