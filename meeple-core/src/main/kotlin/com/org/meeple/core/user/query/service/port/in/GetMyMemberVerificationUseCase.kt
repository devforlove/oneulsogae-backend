package com.org.meeple.core.user.query.service.port.`in`

import com.org.meeple.core.user.query.dto.MemberVerificationView

/**
 * 내 멤버 인증(본인인증) 조회 인포트(유스케이스).
 * 유저의 최신 제출 1건을 조회한다. (재제출이 허용되므로 가장 최근 제출의 상태를 보여준다)
 */
interface GetMyMemberVerificationUseCase {

	/** 유저의 최신 제출 1건을 조회한다. 제출 이력이 없으면 null. */
	fun findLatest(userId: Long): MemberVerificationView?
}
