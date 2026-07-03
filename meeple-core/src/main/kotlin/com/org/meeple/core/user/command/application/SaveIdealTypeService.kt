package com.org.meeple.core.user.command.application

import com.org.meeple.core.user.command.application.port.`in`.SaveIdealTypeUseCase
import com.org.meeple.core.user.command.application.port.`in`.command.SaveIdealTypeCommand
import com.org.meeple.core.user.command.application.port.out.GetIdealTypePort
import com.org.meeple.core.user.command.application.port.out.SaveIdealTypePort
import com.org.meeple.core.user.command.domain.UserIdealType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [SaveIdealTypeUseCase] 구현. 기존 이상형이 있으면 교체(update), 없으면 새로 만들어(of) 저장한다(upsert).
 * 매칭 읽기 모델과 무관하므로 프로필과 달리 도메인 이벤트를 발행하지 않는다.
 */
@Service
class SaveIdealTypeService(
	private val getIdealTypePort: GetIdealTypePort,
	private val saveIdealTypePort: SaveIdealTypePort,
) : SaveIdealTypeUseCase {

	@Transactional
	override fun save(userId: Long, command: SaveIdealTypeCommand): UserIdealType {
		val existing: UserIdealType? = getIdealTypePort.findByUserId(userId)
		val idealType: UserIdealType = existing?.update(
			ageMin = command.ageMin,
			ageMax = command.ageMax,
			heightMin = command.heightMin,
			heightMax = command.heightMax,
			maritalStatus = command.maritalStatus,
			smokingStatus = command.smokingStatus,
			drinkingStatus = command.drinkingStatus,
			religion = command.religion,
		) ?: UserIdealType.of(
			userId = userId,
			ageMin = command.ageMin,
			ageMax = command.ageMax,
			heightMin = command.heightMin,
			heightMax = command.heightMax,
			maritalStatus = command.maritalStatus,
			smokingStatus = command.smokingStatus,
			drinkingStatus = command.drinkingStatus,
			religion = command.religion,
		)
		return saveIdealTypePort.save(idealType)
	}
}
