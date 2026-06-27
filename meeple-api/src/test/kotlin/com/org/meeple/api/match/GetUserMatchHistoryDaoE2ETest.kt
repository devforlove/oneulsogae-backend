package com.org.meeple.api.match

import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.match.MatchStatus
import com.org.meeple.common.match.MatchedTeamStatus
import com.org.meeple.common.match.TeamMatchType
import com.org.meeple.common.match.TeamMemberStatus
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.match.command.entity.MatchedTeamEntity
import com.org.meeple.infra.match.command.entity.QMatchedTeamEntity
import com.org.meeple.infra.match.command.entity.QTeamMatchEntity
import com.org.meeple.infra.match.command.entity.QTeamMemberEntity
import com.org.meeple.infra.match.command.entity.TeamMatchEntity
import com.org.meeple.infra.match.command.entity.TeamMemberEntity
import com.org.meeple.scheduler.match.query.dao.GetUserMatchHistoryDao
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 유저의 과거 MATCHED 상대 팀 조회(네이티브 SQL)를 실DB로 검증한다.
 * - 소프트 삭제된 과거 팀·매칭(@SQLRestriction 우회)도 읽는다.
 * - 성사(expires_at 연장)된 매칭의 상대만 반환하고, 미성사 종료 상대는 반환하지 않는다.
 */
class GetUserMatchHistoryDaoE2ETest : AbstractIntegrationSupport() {

    @Autowired
    private lateinit var dao: GetUserMatchHistoryDao

    // 소프트 삭제 상태의 team_members 행을 만든다. (솔로 유저가 된 뒤 과거 팀 기록)
    private fun persistDeletedMember(teamId: Long, userId: Long) {
        val member = TeamMemberEntity(teamId = teamId, userId = userId, status = TeamMemberStatus.DEACTIVE)
        member.softDelete(LocalDateTime.of(2026, 1, 10, 0, 0))
        IntegrationUtil.persist(member)
    }

    // 두 팀을 묶은 종료(CLOSED·소프트삭제) 매칭. matched=true면 성사 흔적(expires_at +100년)을 남긴다.
    private fun persistEndedMatch(teamA: Long, teamB: Long, matched: Boolean) {
        val introduced = LocalDate.of(2026, 1, 1)
        val expiresAt: LocalDateTime =
            if (matched) LocalDateTime.of(2126, 1, 2, 0, 0) else LocalDateTime.of(2026, 1, 8, 0, 0)
        val header = TeamMatchEntity(
            memberKey = listOf(teamA, teamB).sorted().joinToString("-"),
            introducedDate = introduced,
            expiresAt = expiresAt,
            status = MatchStatus.CLOSED,
            matchType = TeamMatchType.RECOMMENDED,
            dateInitAmount = 40,
            dateAcceptAmount = 40,
        )
        header.softDelete(LocalDateTime.of(2026, 1, 10, 0, 0))
        IntegrationUtil.persist(header)
        val teamMatchId: Long = header.id!!
        listOf(teamA, teamB).forEach { teamId: Long ->
            val mt = MatchedTeamEntity(teamMatchId = teamMatchId, teamId = teamId, status = MatchedTeamStatus.DEACTIVE)
            mt.softDelete(LocalDateTime.of(2026, 1, 10, 0, 0))
            IntegrationUtil.persist(mt)
        }
    }

    init {
        describe("findPreviouslyMatchedTeamIdsByUser") {
            it("과거 성사(MATCHED) 종료된 상대만 반환하고, 미성사 종료 상대는 제외한다") {
                val userId = 9001L
                val myTeamId = 9100L
                val matchedOpponentId = 9200L
                val notMatchedOpponentId = 9300L
                persistDeletedMember(myTeamId, userId)
                persistEndedMatch(myTeamId, matchedOpponentId, matched = true)
                persistEndedMatch(myTeamId, notMatchedOpponentId, matched = false)

                val result = dao.findPreviouslyMatchedTeamIdsByUser(setOf(userId))

                result.opponentTeamIdsOf(userId) shouldBe setOf(matchedOpponentId)
            }

            it("userIds가 비면 빈 결과를 돌려준다") {
                dao.findPreviouslyMatchedTeamIdsByUser(emptySet()).opponentTeamIdsOf(1L) shouldBe emptySet()
            }
        }

        afterTest {
            IntegrationUtil.deleteAll(QMatchedTeamEntity.matchedTeamEntity)
            IntegrationUtil.deleteAll(QTeamMatchEntity.teamMatchEntity)
            IntegrationUtil.deleteAll(QTeamMemberEntity.teamMemberEntity)
        }
    }
}
