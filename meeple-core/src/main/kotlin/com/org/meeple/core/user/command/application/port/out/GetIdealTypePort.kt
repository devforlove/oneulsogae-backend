package com.org.meeple.core.user.command.application.port.out

import com.org.meeple.core.user.command.domain.UserIdealType

/** 이상형 단건 로드 아웃포트(upsert 시 기존 행 조회용). 조회 read model은 query 쪽 [com.org.meeple.core.user.query.dao.GetIdealTypeDao]가 따로 둔다. */
interface GetIdealTypePort {

	fun findByUserId(userId: Long): UserIdealType?
}
