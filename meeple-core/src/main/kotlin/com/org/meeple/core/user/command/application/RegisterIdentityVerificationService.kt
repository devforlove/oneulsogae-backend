package com.org.meeple.core.user.command.application

import com.org.meeple.core.user.command.application.port.`in`.RegisterIdentityVerificationUseCase
import com.org.meeple.core.user.command.application.port.`in`.result.RegisterIdentityVerificationResult
import com.org.meeple.core.user.command.application.port.out.CertRegisterCommand
import com.org.meeple.core.user.command.application.port.out.CertRegisterResult
import com.org.meeple.core.user.command.application.port.out.KcpCertRegisterPort
import com.org.meeple.core.user.command.application.port.out.SaveIdentityVerificationPort
import com.org.meeple.core.user.command.domain.IdentityVerification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class RegisterIdentityVerificationService(
	private val kcpCertRegisterPort: KcpCertRegisterPort,
	private val saveIdentityVerificationPort: SaveIdentityVerificationPort,
) : RegisterIdentityVerificationUseCase {

	@Transactional
	override fun register(userId: Long): RegisterIdentityVerificationResult {
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
