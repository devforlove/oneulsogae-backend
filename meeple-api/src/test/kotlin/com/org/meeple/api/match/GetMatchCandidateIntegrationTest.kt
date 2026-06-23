package com.org.meeple.api.match

import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.match.MatchStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.core.match.command.application.port.out.GetMatchCandidatePort
import com.org.meeple.core.match.command.domain.MatchMembers
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.MatchUserEntityFixture
import com.org.meeple.infra.fixture.RegionEntityFixture
import com.org.meeple.infra.fixture.SoloMatchEntityFixture
import com.org.meeple.infra.fixture.SoloMatchMemberEntityFixture
import com.org.meeple.infra.match.command.entity.QMatchUserEntity
import com.org.meeple.infra.match.command.entity.QSoloMatchEntity
import com.org.meeple.infra.match.command.entity.QSoloMatchMemberEntity
import com.org.meeple.infra.match.command.entity.SoloMatchEntity
import com.org.meeple.infra.region.entity.QRegionEntity
import com.org.meeple.infra.region.entity.RegionEntity
import com.org.meeple.scheduler.match.command.application.port.out.RegionProximityPort
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
                // '유저 있는 region' 필터로 빈 20개를 건너뛰고, 유저 있는 먼 지역을 지역 단위로 조회해 찾는다.
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

        context("스냅샷 갱신 뒤 새로 생긴 지역의 후보는") {
            it("랜덤 폴백으로 찾는다") {
                // refresh 시점엔 유저가 없어 '유저 있는 region' 스냅샷이 비어 지역 순회가 건너뛰어지지만,
                // 랜덤 폴백이 실제 데이터를 보고 후보를 찾는다. (스냅샷 지연에도 정확성 보존)
                val regionId: Long = persistRegion("서울특별시", "강남구", 37.50, 127.00)
                regionProximityPort.refresh()
                val lateFemaleId: Long = persistMatchUser(userId = 40L, gender = Gender.FEMALE, regionId = regionId)

                val candidateId: Long? = getMatchCandidatePort.findOneCandidate(
                    requesterId = 1L, gender = Gender.FEMALE, regionId = regionId, loginAfter = loginAfter,
                )

                candidateId shouldBe lateFemaleId
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
