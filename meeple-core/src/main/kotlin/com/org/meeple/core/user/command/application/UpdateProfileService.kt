package com.org.meeple.core.user.command.application

import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.user.UserErrorCode
import com.org.meeple.core.user.command.application.port.`in`.UpdateProfileUseCase
import com.org.meeple.core.user.command.application.port.`in`.command.UpdateProfileCommand
import com.org.meeple.core.user.command.application.port.out.GetUserDetailPort
import com.org.meeple.core.user.command.application.port.out.SaveUserDetailPort
import com.org.meeple.core.user.command.domain.UserDetail
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [UpdateProfileUseCase] 구현.
 * 기존 프로필을 로드해 편집 가능 필드만 교체하고 저장한다.
 * 나이/성별/키/휴대폰번호/회사이메일과 식별/관리값(id/userId/companyName)은 existing 값이 보존된다.
 */
@Service
class UpdateProfileService(
	private val getUserDetailPort: GetUserDetailPort,
	private val saveUserDetailPort: SaveUserDetailPort,
) : UpdateProfileUseCase {

	@Transactional
	override fun updateProfile(userId: Long, command: UpdateProfileCommand): UserDetail {
		val existing: UserDetail = getUserDetailPort.findByUserId(userId)
			?: throw BusinessException(UserErrorCode.USER_DETAIL_NOT_FOUND, "사용자 프로필을 찾을 수 없습니다: $userId")

		val updated: UserDetail = existing.editProfile(
			nickname = command.nickname,
			profileImageCode = command.profileImageCode,
			job = command.job,
			activityArea = command.activityArea,
			introduction = command.introduction,
			traits = command.traits,
			interests = command.interests,
			maritalStatus = command.maritalStatus,
			smokingStatus = command.smokingStatus,
			religion = command.religion,
			drinkingStatus = command.drinkingStatus,
			bodyType = command.bodyType,
		)

		return saveUserDetailPort.save(updated)
	}
}
