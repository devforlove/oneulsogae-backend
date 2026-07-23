package com.org.oneulsogae.core.user.command.application

import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.user.UserErrorCode
import com.org.oneulsogae.core.user.command.application.port.`in`.IssueReferralCodeUseCase
import com.org.oneulsogae.core.user.command.application.port.out.GetUserPort
import com.org.oneulsogae.core.user.command.application.port.out.SaveUserPort
import com.org.oneulsogae.core.user.command.domain.ReferralCode
import com.org.oneulsogae.core.user.command.domain.User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom

/**
 * [IssueReferralCodeUseCase] 구현.
 * 코드가 이미 있으면 그대로 반환하고, 없으면 생성해 저장한다. (조회처럼 보이지만 쓰기가 발생하는 command)
 */
@Service
class IssueReferralCodeService(
	private val getUserPort: GetUserPort,
	private val saveUserPort: SaveUserPort,
) : IssueReferralCodeUseCase {

	private val random: SecureRandom = SecureRandom()

	@Transactional
	override fun issue(userId: Long): String {
		val user: User = getUserPort.findById(userId)
			?: throw BusinessException(UserErrorCode.USER_NOT_FOUND, "사용자를 찾을 수 없습니다: $userId")
		user.referralCode?.let { return it }

		// ponytail: 생성→중복조회→저장. ux_referral_code 유니크 제약은 유저 간 코드 충돌을 방어한다.
		// 동일 유저의 동시 최초 발급은 마지막 저장이 이겨 먼저 반환된 코드가 무효가 될 수 있으나(극저빈도·무효 코드는 조용히 무시됨),
		// 필요해지면 referral_code IS NULL 조건부 UPDATE로 강화한다.
		repeat(MAX_GENERATE_ATTEMPTS) {
			val code: String = ReferralCode.generate(random)
			if (getUserPort.findByReferralCode(code) == null) {
				saveUserPort.save(user.assignReferralCode(code))
				return code
			}
		}
		throw BusinessException(UserErrorCode.REFERRAL_CODE_ISSUE_FAILED)
	}

	companion object {
		/** 36^8 공간이라 충돌은 사실상 없지만, 무한 루프 방지 상한. */
		private const val MAX_GENERATE_ATTEMPTS: Int = 5
	}
}
