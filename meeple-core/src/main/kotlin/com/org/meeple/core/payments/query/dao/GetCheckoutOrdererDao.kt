package com.org.meeple.core.payments.query.dao

import com.org.meeple.core.payments.query.dto.OrdererView

/**
 * 체크아웃 주문자 정보 조회 dao(out-port). infra의 GetCheckoutOrdererDaoImpl이 구현한다.
 * users·user_details·최신 VERIFIED identity_verifications를 [OrdererView]로 투영한다.
 */
interface GetCheckoutOrdererDao {

	/** users 행이 없으면 null을 반환한다. (있으면 나머지 필드는 null 허용 투영) */
	fun findOrdererByUserId(userId: Long): OrdererView?
}
