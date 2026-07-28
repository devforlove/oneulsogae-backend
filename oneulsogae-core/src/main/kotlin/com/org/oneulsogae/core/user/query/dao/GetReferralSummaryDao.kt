package com.org.oneulsogae.core.user.query.dao

/** 추천 실적 조회 dao(query out-port 인터페이스). QueryDSL 구현은 infra가 담당한다. */
interface GetReferralSummaryDao {

	/** [referrerUserId]의 추천 코드로 가입을 완료한 사용자 수. (탈퇴로 삭제된 사용자는 빠진다) */
	fun countReferredUsers(referrerUserId: Long): Int
}
