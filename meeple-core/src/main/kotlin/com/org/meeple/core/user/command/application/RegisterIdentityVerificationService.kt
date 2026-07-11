package com.org.meeple.core.user.command.application

import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.user.UserErrorCode
import com.org.meeple.core.user.command.application.port.`in`.RegisterIdentityVerificationUseCase
import com.org.meeple.core.user.command.application.port.`in`.result.RegisterIdentityVerificationResult
import com.org.meeple.core.user.command.application.port.out.CertRegisterCommand
import com.org.meeple.core.user.command.application.port.out.CertRegisterResult
import com.org.meeple.core.user.command.application.port.out.GetUserPort
import com.org.meeple.core.user.command.application.port.out.KcpCertRegisterPort
import com.org.meeple.core.user.command.application.port.out.SaveIdentityVerificationPort
import com.org.meeple.core.user.command.domain.IdentityVerification
import com.org.meeple.core.user.command.domain.User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class RegisterIdentityVerificationService(
	private val kcpCertRegisterPort: KcpCertRegisterPort,
	private val saveIdentityVerificationPort: SaveIdentityVerificationPort,
	private val getUserPort: GetUserPort,
) : RegisterIdentityVerificationUseCase {

	@Transactional
	override fun register(userId: Long): RegisterIdentityVerificationResult {
		// 본인확인은 온보딩 중에만 시작할 수 있다. (이미 가입 절차를 지난 사용자의 재인증 차단)
		val user: User = getUserPort.findById(userId)
			?: throw BusinessException(UserErrorCode.USER_NOT_FOUND, "사용자를 찾을 수 없습니다: $userId")
		user.validateCanStartIdentityVerification()

		val ordrIdxx: String = generateOrderId()
		val result: CertRegisterResult = kcpCertRegisterPort.register(CertRegisterCommand(ordrIdxx))

		saveIdentityVerificationPort.save(
			IdentityVerification.request(userId = userId, ordrIdxx = ordrIdxx, regCertKey = result.regCertKey),
		)

		return RegisterIdentityVerificationResult(
			callUrl = result.callUrl,
			regCertKey = result.regCertKey,
			ordrIdxx = ordrIdxx,
		)
	}

	/** KCP 주문번호(최대 50자). 거래 추적용 유니크 값. */
	private fun generateOrderId(): String =
		"MPL" + UUID.randomUUID().toString().replace("-", "")
}
