package com.org.meeple.infra.match.query

import com.org.meeple.infra.match.command.entity.QMatchUserEntity
import com.org.meeple.infra.match.command.entity.QTeamMemberEntity
import com.org.meeple.scheduler.match.query.dao.GetRecommendableSoloUserDao
import com.org.meeple.scheduler.match.query.dto.RecommendableSoloUser
import com.querydsl.core.types.Projections
import com.querydsl.jpa.JPAExpressions
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * scheduler [GetRecommendableSoloUserDao]의 QueryDSL 구현. (조회 전용)
 * match_user 단독 베이스 + team_members NOT EXISTS로 팀 미소속 유저만 거르고, `last_login_at >= :loginAfter`로 최근 로그인 유저만 남긴다.
 * (team_members @SQLRestriction이 소프트 삭제 행을 제외하므로, NOT EXISTS = 비삭제 소속이 전혀 없음 = 팀 미소속)
 * `last_login_at` 범위는 `idx_last_login_at_user_id`로 받쳐져 풀 테이블 스캔이 아니다.
 * 솔로 매칭 배치와 동일하게 대상 전체를 한 번에 반환한다.
 */
@Component
class GetRecommendableSoloUserDaoImpl(
    private val queryFactory: JPAQueryFactory,
) : GetRecommendableSoloUserDao {

    override fun findRecommendableSoloUsers(loginAfter: LocalDateTime): List<RecommendableSoloUser> {
        val matchUser: QMatchUserEntity = QMatchUserEntity.matchUserEntity
        val teamMember: QTeamMemberEntity = QTeamMemberEntity.teamMemberEntity

        return queryFactory
            .select(
                Projections.constructor(
                    RecommendableSoloUser::class.java,
                    matchUser.userId,
                    matchUser.gender,
                    matchUser.regionId,
                ),
            )
            .from(matchUser)
            .where(
                matchUser.lastLoginAt.goe(loginAfter),
                JPAExpressions.selectOne()
                    .from(teamMember)
                    .where(teamMember.userId.eq(matchUser.userId))
                    .notExists(),
            )
            .fetch()
    }
}
