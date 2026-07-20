package com.org.oneulsogae.core.user.query.service.port.`in`

import com.org.oneulsogae.core.user.query.dto.UserView

/**
 * id로 사용자를 조회하는 인포트(유스케이스). 조회 결과 read model([UserView])을 반환한다.
 */
interface GetUserByIdUseCase {

	fun getById(id: Long): UserView
}
