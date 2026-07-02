package com.org.meeple.infra.matchuser

import com.org.meeple.infra.matchuser.command.entity.QMatchUserEntity
import com.querydsl.core.types.dsl.BooleanExpression

/**
 * 같은 회사 소개 차단 정책([com.org.meeple.common.match.SameCompanyIntroPolicy])의 QueryDSL 표현.
 * SQL 후보 조회 경로(온보딩 추천·추가소개 미리보기)가 공유한다. 인메모리 선정 경로는 MatchSelector가 순수 정책으로 거른다.
 */
object SameCompanyIntroPredicates {

	/**
	 * [candidate]가 요청자와의 같은 회사 소개 차단에 걸리지 않음을 뜻하는 조건.
	 * 요청자 회사 미상(null)이면 차단이 성립하지 않으므로 null(조건 없음)을 반환한다. (QueryDSL where는 null 조건을 무시)
	 * 요청자가 거부하면 같은 회사 후보 전체를, 거부하지 않으면 같은 회사 중 거부한 후보만 제외한다(양방향).
	 */
	fun notBlockedBySameCompanyIntro(
		candidate: QMatchUserEntity,
		requesterCompanyName: String?,
		requesterRefusesSameCompanyIntro: Boolean,
	): BooleanExpression? {
		if (requesterCompanyName == null) return null
		val differentCompany: BooleanExpression = candidate.companyName.isNull.or(candidate.companyName.ne(requesterCompanyName))
		return if (requesterRefusesSameCompanyIntro) {
			differentCompany
		} else {
			differentCompany.or(candidate.refuseSameCompanyIntro.isFalse)
		}
	}
}
