package com.org.meeple.infra.user.command.adapter

import com.fasterxml.jackson.annotation.JsonProperty
import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.user.UserErrorCode
import com.org.meeple.core.user.command.application.port.out.CertRegisterCommand
import com.org.meeple.core.user.command.application.port.out.CertRegisterResult
import com.org.meeple.core.user.command.application.port.out.KcpCertCryptoPort
import com.org.meeple.core.user.command.application.port.out.KcpCertRegisterPort
import com.org.meeple.infra.config.KcpProperties
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

/**
 * KCP 거래등록 어댑터. (POST {baseUrl}/api/reg/certDataReg.do)
 * 요청 본문 파라미터 상세는 KCP API Reference(/reference/regist) 확정본으로 실연동 시 재확인한다.
 */
@Component
class KcpCertRegisterAdapter(
	private val kcpRestClient: RestClient,
	private val kcpProperties: KcpProperties,
	private val kcpCertCryptoPort: KcpCertCryptoPort,
) : KcpCertRegisterPort {

	override fun register(command: CertRegisterCommand): CertRegisterResult {
		val plainJson: String = """
			{"site_cd":"${kcpProperties.siteCd}","ordr_idxx":"${command.ordrIdxx}",""" +
			""""web_siteid":"${kcpProperties.webSiteId}","Ret_URL":"${kcpProperties.retUrl}"}"""
		val encData: String = kcpCertCryptoPort.encryptRegisterData(plainJson.trimIndent())

		val response: KcpRegisterResponse = kcpRestClient.post()
			.uri("/api/reg/certDataReg.do")
			.body(
				mapOf(
					"site_cd" to kcpProperties.siteCd,
					"ordr_idxx" to command.ordrIdxx,
					"enc_data" to encData,
				),
			)
			.retrieve()
			.body<KcpRegisterResponse>()
			?: throw BusinessException(UserErrorCode.KCP_REGISTER_FAILED)

		if (response.resCd != SUCCESS_CODE || response.regCertKey == null || response.callUrl == null) {
			throw BusinessException(UserErrorCode.KCP_REGISTER_FAILED, "res_cd=${response.resCd}, res_msg=${response.resMsg}")
		}
		return CertRegisterResult(regCertKey = response.regCertKey, callUrl = response.callUrl)
	}

	companion object {
		private const val SUCCESS_CODE: String = "0000"
	}
}

data class KcpRegisterResponse(
	@JsonProperty("res_cd") val resCd: String?,
	@JsonProperty("res_msg") val resMsg: String?,
	@JsonProperty("reg_cert_key") val regCertKey: String?,
	@JsonProperty("call_url") val callUrl: String?,
)
