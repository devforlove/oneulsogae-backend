package com.org.meeple.core.user.command.application

import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.common.event.DomainEventPublisher
import com.org.meeple.core.user.UserErrorCode
import com.org.meeple.core.user.command.application.port.`in`.ResolveCompanyNameUseCase
import com.org.meeple.core.user.command.application.port.out.GetUserDetailPort
import com.org.meeple.core.user.command.application.port.out.GetUserPort
import com.org.meeple.core.user.command.application.port.out.SaveUserDetailPort
import com.org.meeple.core.user.command.application.port.out.SaveUserPort
import com.org.meeple.core.user.command.domain.User
import com.org.meeple.core.user.command.domain.UserDetail
import com.org.meeple.core.user.command.domain.event.UserProfileChanged
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [ResolveCompanyNameUseCase] 구현.
 * 사용자가 직접 입력한 회사명을 프로필에 반영하고 정식 가입(ACTIVE)으로 전환한다.
 * (회사 이메일 인증은 마쳤으나 도메인 매핑으로 회사명을 찾지 못한 사용자를 위한 보완 경로)
 */
@Service
class ResolveCompanyNameService(
	private val getUserDetailPort: GetUserDetailPort,
	private val saveUserDetailPort: SaveUserDetailPort,
	private val getUserPort: GetUserPort,
	private val saveUserPort: SaveUserPort,
	private val domainEventPublisher: DomainEventPublisher,
) : ResolveCompanyNameUseCase {

	@Transactional
	override fun resolve(userId: Long, companyName: String) {
		val user: User = getUserPort.findById(userId)
			?: throw BusinessException(UserErrorCode.USER_NOT_FOUND, "사용자를 찾을 수 없습니다: $userId")

		val detail: UserDetail = getUserDetailPort.findByUserId(userId)
			?: throw BusinessException(UserErrorCode.USER_DETAIL_NOT_FOUND, "사용자 프로필을 찾을 수 없습니다: $userId")
		saveUserDetailPort.save(detail.copy(companyName = companyName))

		saveUserPort.save(user.completeSignUp())

		// 가입 상태가 바뀌었음을 알린다. (UserEventHandler가 매칭 읽기 모델 동기화로 이어간다)
		domainEventPublisher.publish(UserProfileChanged(userId))
	}
}
