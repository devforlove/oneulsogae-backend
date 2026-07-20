package com.org.oneulsogae.core.gathering.query.dao

import com.org.oneulsogae.core.gathering.query.dto.MemberVerificationView

/**
 * 멤버 인증(본인인증) 조회 dao(query out-port 인터페이스). 구현은 infra가 담당한다.
 */
interface GetMemberVerificationDao {

	/** 유저의 최신 제출 1건을 조회한다. 제출 이력이 없으면 null. */
	fun findLatestByUserId(userId: Long): MemberVerificationView?
}
