package com.org.meeple.core.user.command.application

import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.user.UserErrorCode
import com.org.meeple.core.common.event.DomainEventPublisher
import com.org.meeple.core.common.event.UserLoggedIn
import com.org.meeple.core.common.time.TimeGenerator
import com.org.meeple.core.user.command.application.port.`in`.RegisterUserUseCase
import com.org.meeple.core.user.command.application.port.out.GetUserPort
import com.org.meeple.core.user.command.application.port.out.RestoreUserPort
import com.org.meeple.core.user.command.application.port.out.SaveUserPort
import com.org.meeple.core.user.command.domain.User
import com.org.meeple.core.user.command.domain.event.UserProfileChanged
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * [RegisterUserUseCase] 구현.
 * 계정 식별 정보는 [User]로 저장하고, 로그인할 때마다(신규/기존 모두) 마지막 로그인 시점을 기록한다.
 */
@Service
class RegisterUserService(
	private val getUserPort: GetUserPort,
	private val saveUserPort: SaveUserPort,
	private val restoreUserPort: RestoreUserPort,
	private val timeGenerator: TimeGenerator,
	private val domainEventPublisher: DomainEventPublisher,
) : RegisterUserUseCase {

	@Transactional
	override fun registerIfAbsent(
		provider: String,
		providerId: String,
		email: String?,
		profileImageUrl: String?,
	): User {
		// 이미 가입된 사용자면 프로바이더 값으로 덮어쓰지 않고, 마지막 로그인 시점만 기록해 그대로 반환한다.
		val existing: User? = getUserPort.findByProviderAndProviderId(provider, providerId)
		if (existing != null) {
			return recordLogin(existing)
		}

		// 탈퇴 유예중(소프트삭제) 계정이면 복구한다. (10일 경계는 파기 배치가 provider_id 치환으로 강제)
		val withdrawnId: Long? = getUserPort.findWithdrawnUserId(provider, providerId)
		if (withdrawnId != null) {
			val restored: User = restoreUserPort.restore(withdrawnId, timeGenerator.now())
			// 복구된 사용자를 매칭 읽기모델에 재적재한다(매칭 가능하면). UserLoggedIn은 기존 행만 갱신하므로 ProfileChanged로 전체 동기화.
			domainEventPublisher.publish(UserProfileChanged(restored.id))
			return restored
		}

		// 신규 가입은 이메일이 반드시 있어야 한다. (OAuth provider가 이메일 제공에 동의받지 못하면 null)
		if (email == null) {
			throw BusinessException(UserErrorCode.EMAIL_REQUIRED)
		}

		// 같은 이메일이 다른 계정에서 이미 쓰이고 있으면 중복 가입을 막는다.
		if (getUserPort.existsByEmail(email)) {
			throw BusinessException(UserErrorCode.EMAIL_ALREADY_REGISTERED)
		}

		// 처음 가입한 유저라면, User를 저장하고 첫 로그인 시점을 기록한다.
		val savedUser: User = saveUserPort.save(User.create(provider, providerId, email))
		return recordLogin(savedUser)
	}

	/** 사용자의 마지막 로그인 시점을 기록하고, 매칭 읽기 모델(match_user) 갱신을 위해 로그인 이벤트를 발행한다. */
	private fun recordLogin(user: User): User {
		val now: LocalDateTime = timeGenerator.now()
		val saved: User = saveUserPort.save(user.recordLogin(now))

		// 이미 매칭 풀에 적재된 사용자라면 마지막 로그인 시각만 갱신된다. (미적재 사용자는 수신측에서 무시)
		domainEventPublisher.publish(UserLoggedIn(saved.id, now))
		return saved
	}
}
