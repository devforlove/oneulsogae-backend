package com.org.oneulsogae.core.user.query.service.port.`in`

import com.org.oneulsogae.core.user.query.dto.IdealTypeView

/** userId로 이상형을 조회하는 인포트. 미설정 사용자도 진입 가능해야 하므로 없으면 null(예외 아님). */
interface GetIdealTypeUseCase {

	fun findByUserId(userId: Long): IdealTypeView?
}
