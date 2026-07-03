package com.org.meeple.infra.user.query

import com.org.meeple.core.user.query.dao.GetIdealTypeDao
import com.org.meeple.core.user.query.dto.IdealTypeView
import com.org.meeple.infra.user.command.repository.UserIdealTypeJpaRepository
import org.springframework.stereotype.Component

/**
 * [GetIdealTypeDao]의 구현체. 조인이 없어 QueryDSL 대신 command 리포지토리를 재사용해 read model로 투영한다.
 * (infra 내부 query→command 참조 허용 규칙에 따른다)
 */
@Component
class GetIdealTypeDaoImpl(
	private val userIdealTypeJpaRepository: UserIdealTypeJpaRepository,
) : GetIdealTypeDao {

	override fun findByUserId(userId: Long): IdealTypeView? =
		userIdealTypeJpaRepository.findByUserId(userId)?.let {
			IdealTypeView(
				ageMin = it.ageMin,
				ageMax = it.ageMax,
				heightMin = it.heightMin,
				heightMax = it.heightMax,
				maritalStatus = it.maritalStatus,
				smokingStatus = it.smokingStatus,
				drinkingStatus = it.drinkingStatus,
				religion = it.religion,
			)
		}
}
