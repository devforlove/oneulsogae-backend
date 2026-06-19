package com.org.meeple.core.user.command.application

import com.org.meeple.core.common.event.DomainEventPublisher
import com.org.meeple.core.user.command.application.port.`in`.UpdateUserDetailUseCase
import com.org.meeple.core.user.command.application.port.`in`.command.UpdateUserDetailCommand
import com.org.meeple.core.user.command.application.port.out.GetUserDetailPort
import com.org.meeple.core.user.command.application.port.out.SaveUserDetailPort
import com.org.meeple.core.user.command.domain.UserDetail
import com.org.meeple.core.user.command.domain.event.UserProfileChanged
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [UpdateUserDetailUseCase] 구현.
 * 온보딩 입력값(프로필 상세 전체)을 도메인 모델([UserDetail.initProfile])에 위임해 갱신·저장한다.
 * 프로필 갱신 규칙(필드 교체/regionCode 산출/profileImageCode 배정)은 도메인이 담당하고,
 * 서비스는 조회·저장만 맡는다.
 */
@Service
class UpdateUserDetailService(
	private val getUserDetailPort: GetUserDetailPort,
	private val saveUserDetailPort: SaveUserDetailPort,
	private val domainEventPublisher: DomainEventPublisher,
) : UpdateUserDetailUseCase {

	@Transactional
	override fun update(userId: Long, command: UpdateUserDetailCommand): UserDetail {
		// 온보딩 첫 프로필 입력이면 detail이 아직 없으므로, 빈 detail을 만들어 채운다. (가입 시 User만 생성됨)
		val existing: UserDetail = getUserDetailPort.findByUserId(userId)
			?: UserDetail.create(userId)

		val updated: UserDetail = existing.initProfile(
			nickname = command.nickname,
			age = command.age,
			height = command.height,
			gender = command.gender,
			phoneNumber = command.phoneNumber,
			job = command.job,
			activityArea = command.activityArea,
			introduction = command.introduction,
			traits = command.traits,
			interests = command.interests,
			companyEmail = command.companyEmail,
			maritalStatus = command.maritalStatus,
			smokingStatus = command.smokingStatus,
			religion = command.religion,
			drinkingStatus = command.drinkingStatus,
			bodyType = command.bodyType,
		)

		val saved: UserDetail = saveUserDetailPort.save(updated)

		// 프로필이 바뀌었음을 알린다. (UserEventHandler가 매칭 읽기 모델 동기화로 이어간다)
		domainEventPublisher.publish(UserProfileChanged(userId))
		return saved
	}
}
