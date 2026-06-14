package com.org.meeple.core.user.application

import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.common.time.TimeGenerator
import com.org.meeple.core.user.application.port.`in`.RegisterUserUseCase
import com.org.meeple.core.user.application.port.out.GetUserPort
import com.org.meeple.core.user.application.port.out.SaveUserPort
import com.org.meeple.core.user.domain.User
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
	private val timeGenerator: TimeGenerator,
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

	/** 사용자의 마지막 로그인 시점을 기록한다. */
	private fun recordLogin(user: User): User {
		val now: LocalDateTime = timeGenerator.now()
		return saveUserPort.save(user.recordLogin(now))
	}
}
