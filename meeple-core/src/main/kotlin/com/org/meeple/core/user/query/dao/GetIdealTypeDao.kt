package com.org.meeple.core.user.query.dao

import com.org.meeple.core.user.query.dto.IdealTypeView

/** 이상형 조회 dao(query out-port). read model([IdealTypeView])을 반환한다. 없으면 null. */
interface GetIdealTypeDao {

	fun findByUserId(userId: Long): IdealTypeView?
}
