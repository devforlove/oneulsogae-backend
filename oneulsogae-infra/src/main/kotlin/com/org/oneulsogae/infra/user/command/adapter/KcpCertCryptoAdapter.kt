package com.org.oneulsogae.infra.user.command.adapter

import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.user.UserErrorCode
import com.org.oneulsogae.core.user.command.application.port.out.EncryptedRegisterData
import com.org.oneulsogae.core.user.command.application.port.out.KcpCertCryptoPort
import com.org.oneulsogae.infra.config.KcpProperties
import org.springframework.stereotype.Component
import utils.Crypto

/**
 * KCP 본인확인 V2 암복호화 어댑터. 공식 라이브러리(utils.Crypto)에 ENC_KEY·site_cd를 채워 위임한다.
 * - encryptJson(json, enc_key, site_cd) → Map{encData, rv} : 거래등록 요청 암호화
 * - decryptJson(enc_cert_data, rv, enc_key, site_cd) → 평문 JSON : 결과조회 응답 복호화
 */
@Component
class KcpCertCryptoAdapter(
	private val kcpProperties: KcpProperties,
) : KcpCertCryptoPort {

	override fun encryptRegisterData(plainJson: String): EncryptedRegisterData {
		val result: Map<*, *> = try {
			Crypto.encryptJson(plainJson, kcpProperties.encKey, kcpProperties.siteCd)
		} catch (e: Exception) {
			throw BusinessException(UserErrorCode.KCP_REGISTER_FAILED, "encryptJson 실패: ${e.message}")
		}
		val encData: String = result["encData"] as? String
			?: throw BusinessException(UserErrorCode.KCP_REGISTER_FAILED, "encryptJson 응답에 encData 없음")
		val rv: String = result["rv"] as? String
			?: throw BusinessException(UserErrorCode.KCP_REGISTER_FAILED, "encryptJson 응답에 rv 없음")
		return EncryptedRegisterData(encData = encData, rv = rv)
	}

	override fun decryptCertData(encCertData: String, rv: String): String =
		try {
			Crypto.decryptJson(encCertData, rv, kcpProperties.encKey, kcpProperties.siteCd)
		} catch (e: Exception) {
			throw BusinessException(UserErrorCode.KCP_QUERY_FAILED, "decryptJson 실패: ${e.message}")
		}
}
