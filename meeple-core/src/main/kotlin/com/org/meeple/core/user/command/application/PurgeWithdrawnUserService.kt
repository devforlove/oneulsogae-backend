package com.org.meeple.core.user.command.application

import com.org.meeple.core.common.time.TimeGenerator
import com.org.meeple.core.user.command.application.port.`in`.PurgeWithdrawnUserUseCase
import com.org.meeple.core.user.command.application.port.out.AnonymizeUserDetailPort
import com.org.meeple.core.user.command.application.port.out.AnonymizeUserPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [PurgeWithdrawnUserUseCase] 구현. 탈퇴 유예가 지난 사용자의 개인정보를 익명화한다.
 * provider_id를 "withdrawn_{userId}"(유일·결정적)로 치환해 (provider, provider_id) 유니크를 풀고 복구 불가로 만든다.
 * 코인 원장 등 법령 보존 데이터는 건드리지 않는다(user_id 링크는 익명화된 user를 가리켜 비식별).
 */
@Service
class PurgeWithdrawnUserService(
	private val anonymizeUserPort: AnonymizeUserPort,
	private val anonymizeUserDetailPort: AnonymizeUserDetailPort,
	private val timeGenerator: TimeGenerator,
) : PurgeWithdrawnUserUseCase {

	@Transactional
	override fun purge(userId: Long) {
		// users 익명화가 실제로 적용된 경우에만 user_details도 익명화한다.
		// deleted_at이 null(배치 적재 후 복구된 활성 계정)이면 0행 → false → user_details 건드리지 않음.
		val anonymized: Boolean = anonymizeUserPort.anonymize(userId, "withdrawn_$userId")
		if (anonymized) {
			anonymizeUserDetailPort.anonymize(userId, timeGenerator.now())
		}
	}
}
