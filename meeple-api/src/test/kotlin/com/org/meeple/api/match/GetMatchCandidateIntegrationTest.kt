package com.org.meeple.api.match

import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.match.MatchStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.core.solomatch.command.application.port.out.GetMatchCandidatePort
import com.org.meeple.core.solomatch.command.domain.MatchMembers
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.MatchUserEntityFixture
import com.org.meeple.infra.fixture.RegionEntityFixture
import com.org.meeple.infra.fixture.SoloMatchEntityFixture
import com.org.meeple.infra.fixture.SoloMatchMemberEntityFixture
import com.org.meeple.infra.matchuser.command.entity.QMatchUserEntity
import com.org.meeple.infra.solomatch.command.entity.QSoloMatchEntity
import com.org.meeple.infra.solomatch.command.entity.QSoloMatchMemberEntity
import com.org.meeple.infra.solomatch.command.entity.SoloMatchEntity
import com.org.meeple.infra.region.entity.QRegionEntity
import com.org.meeple.infra.region.entity.RegionEntity
import com.org.meeple.scheduler.common.command.application.port.out.RegionProximityPort
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime

/**
 * [GetMatchCandidatePort] 통합 테스트. 실 컨텍스트 + Testcontainers(MySQL)에서 후보 조회를 직접 호출한다.
 * regions·match_user는 컨텍스트 기동 후 적재하므로, 각 시나리오에서 [RegionProximityPort.refresh]로 근접·유저분포 스냅샷을 갱신한 뒤 호출한다.
 */
class GetMatchCandidateIntegrationTest(
    private val getMatchCandidatePort: GetMatchCandidatePort,
    private val regionProximityPort: RegionProximityPort,
) : AbstractIntegrationSupport({

    val loginAfter: LocalDateTime = LocalDateTime.now().minusWeeks(2)

    describe("findOneCandidate") {

        context("가까운 지역과 먼 지역에 후보가 있으면") {
            it("가까운 지역 후보를 반환한다") {
                val nearRegionId: Long = persistRegion("서울특별시", "강남구", 37.50, 127.00)
                val farRegionId: Long = persistRegion("부산광역시", "해운대구", 35.16, 129.16)
                val nearFemaleId: Long = persistMatchUser(userId = 10L, gender = Gender.FEMALE, regionId = nearRegionId)
                persistMatchUser(userId = 20L, gender = Gender.FEMALE, regionId = farRegionId)
                regionProximityPort.refresh()

                val candidateId: Long? = getMatchCandidatePort.findOneCandidate(
                    requesterId = 1L, gender = Gender.FEMALE, regionId = nearRegionId, loginAfter = loginAfter,
                )

                candidateId shouldBe nearFemaleId
            }
        }

        context("가장 가까운 후보가 이미 소개된 이력이 있으면") {
            it("그 후보를 제외하고 같은 지역의 다음 후보를 반환한다") {
                val regionId: Long = persistRegion("서울특별시", "강남구", 37.50, 127.00)
                // introduced(10L)가 더 최근 로그인이지만 이력으로 제외되고, fresh(20L)가 선택돼야 한다
                val introducedFemaleId: Long = persistMatchUser(userId = 10L, gender = Gender.FEMALE, regionId = regionId, lastLoginAt = LocalDateTime.now())
                val freshFemaleId: Long = persistMatchUser(userId = 20L, gender = Gender.FEMALE, regionId = regionId, lastLoginAt = LocalDateTime.now().minusDays(1))
                persistProposedMatch(1L, introducedFemaleId)
                regionProximityPort.refresh()

                val candidateId: Long? = getMatchCandidatePort.findOneCandidate(
                    requesterId = 1L, gender = Gender.FEMALE, regionId = regionId, loginAfter = loginAfter,
                )

                candidateId shouldBe freshFemaleId
            }
        }

        context("요청자와 후보가 각자 '다른 사람'과는 매칭됐지만 서로는 매칭된 적 없으면") {
            it("그 후보를 반환한다 (서로의 매칭 이력만 제외해야 한다)") {
                val regionId: Long = persistRegion("서울특별시", "강남구", 37.50, 127.00)
                val candidateId: Long = persistMatchUser(userId = 20L, gender = Gender.FEMALE, regionId = regionId)
                // 요청자(1L)는 99L과, 후보(20L)는 98L과 각각 매칭 이력이 있다. 1L↔20L 사이 매칭은 없다.
                persistProposedMatch(1L, 99L)
                persistProposedMatch(98L, 20L)
                regionProximityPort.refresh()

                val result: Long? = getMatchCandidatePort.findOneCandidate(
                    requesterId = 1L, gender = Gender.FEMALE, regionId = regionId, loginAfter = loginAfter,
                )

                result shouldBe candidateId
            }
        }

        context("어디에도 신선 후보가 없으면") {
            it("null을 반환한다") {
                val regionId: Long = persistRegion("서울특별시", "강남구", 37.50, 127.00)
                // 같은 성별만 있어 반대 성별 후보가 없다 (region은 유저 있음으로 잡히지만 자격 후보는 0)
                persistMatchUser(userId = 10L, gender = Gender.MALE, regionId = regionId)
                regionProximityPort.refresh()

                getMatchCandidatePort.findOneCandidate(
                    requesterId = 1L, gender = Gender.FEMALE, regionId = regionId, loginAfter = loginAfter,
                ).shouldBeNull()
            }
        }

        context("가까운 지역들은 비어 있고 더 먼 '유저 있는' 지역에만 후보가 있으면") {
            it("빈 지역은 건너뛰고 그 먼 지역 후보를 반환한다") {
                // 요청자 지역(빈) + 빈 채움 지역 19개 + 유저 있는 먼 지역 1개 = 21개.
                // '유저 있는 region' 필터로 빈 지역들을 건너뛰고, 유저 있는 먼 지역을 지역 단위로 조회해 찾는다.
                val requesterRegionId: Long = persistRegion("테스트도", "req", latitude = 37.00, longitude = 127.00)
                for (i: Int in 1..19) {
                    persistRegion("테스트도", "fill$i", latitude = 37.00 + i * 0.01, longitude = 127.00)
                }
                val farRegionId: Long = persistRegion("테스트도", "far", latitude = 38.00, longitude = 127.00)
                val farFemaleId: Long = persistMatchUser(userId = 30L, gender = Gender.FEMALE, regionId = farRegionId)
                regionProximityPort.refresh()

                val candidateId: Long? = getMatchCandidatePort.findOneCandidate(
                    requesterId = 1L, gender = Gender.FEMALE, regionId = requesterRegionId, loginAfter = loginAfter,
                )

                candidateId shouldBe farFemaleId
            }
        }

        context("스냅샷 갱신 전 새로 생긴 지역의 후보는") {
            it("다음 갱신 전까지 후보로 잡히지 않아 null이다") {
                // refresh 시점엔 유저가 없어 '유저 있는 region' 스냅샷이 비고, 지역 순회가 그 지역을 건너뛴다.
                // (의도된 트레이드오프: 새 region 유저는 다음 refresh까지 추천 대상에서 빠진다. 정합성이 아니라 추천 지연일 뿐)
                val regionId: Long = persistRegion("서울특별시", "강남구", 37.50, 127.00)
                regionProximityPort.refresh()
                persistMatchUser(userId = 40L, gender = Gender.FEMALE, regionId = regionId)

                getMatchCandidatePort.findOneCandidate(
                    requesterId = 1L, gender = Gender.FEMALE, regionId = regionId, loginAfter = loginAfter,
                ).shouldBeNull()
            }
        }
    }

    afterTest {
        IntegrationUtil.deleteAll(QSoloMatchMemberEntity.soloMatchMemberEntity)
        IntegrationUtil.deleteAll(QSoloMatchEntity.soloMatchEntity)
        IntegrationUtil.deleteAll(QMatchUserEntity.matchUserEntity)
        IntegrationUtil.deleteAll(QRegionEntity.regionEntity)
    }
})

private fun persistRegion(sido: String, sigungu: String, latitude: Double, longitude: Double): Long {
    val region: RegionEntity = IntegrationUtil.persist(
        RegionEntityFixture.create(sido = sido, sigungu = sigungu, latitude = latitude, longitude = longitude),
    )
    return region.id!!
}

private fun persistMatchUser(
    userId: Long,
    gender: Gender,
    regionId: Long,
    lastLoginAt: LocalDateTime = LocalDateTime.now(),
): Long {
    IntegrationUtil.persist(
        MatchUserEntityFixture.create(userId = userId, gender = gender, regionId = regionId, lastLoginAt = lastLoginAt),
    )
    return userId
}

private fun persistProposedMatch(userIdA: Long, userIdB: Long) {
    val match: SoloMatchEntity = IntegrationUtil.persist(
        SoloMatchEntityFixture.create(
            memberKey = MatchMembers.memberKeyOf(listOf(userIdA, userIdB)),
            status = MatchStatus.PROPOSED,
        ),
    )
    IntegrationUtil.persist(SoloMatchMemberEntityFixture.create(matchId = match.id!!, userId = userIdA, gender = Gender.MALE))
    IntegrationUtil.persist(SoloMatchMemberEntityFixture.create(matchId = match.id!!, userId = userIdB, gender = Gender.FEMALE))
}
