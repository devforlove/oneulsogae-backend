package com.org.oneulsogae.infra.user.command.adapter

import com.fasterxml.jackson.annotation.JsonProperty
import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.user.UserErrorCode
import com.org.oneulsogae.core.user.command.application.port.out.CertRegisterCommand
import com.org.oneulsogae.core.user.command.application.port.out.CertRegisterResult
import com.org.oneulsogae.core.user.command.application.port.out.EncryptedRegisterData
import com.org.oneulsogae.core.user.command.application.port.out.KcpCertCryptoPort
import com.org.oneulsogae.core.user.command.application.port.out.KcpCertRegisterPort
import com.org.oneulsogae.infra.config.KcpProperties
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body
import tools.jackson.databind.ObjectMapper

/**
 * KCP 거래등록 어댑터. (POST {baseUrl}/api/reg/certDataReg.do)
 * 요청 전문 JSON을 암호화한 뒤, Header(site_cd, rv) + Body(enc_data 문자열)로 전송한다.
 */
@Component
class KcpCertRegisterAdapter(
	private val kcpRestClient: RestClient,
	private val kcpProperties: KcpProperties,
	private val kcpCertCryptoPort: KcpCertCryptoPort,
	private val objectMapper: ObjectMapper,
) : KcpCertRegisterPort {

	override fun register(command: CertRegisterCommand): CertRegisterResult {
		val plainJson: String = objectMapper.writeValueAsString(
			linkedMapOf(
				"site_cd" to kcpProperties.siteCd,
				"ordr_idxx" to command.ordrIdxx,
				"Ret_URL" to kcpProperties.retUrl,
				"web_siteid" to kcpProperties.webSiteId,
				// ordr_idxx를 param_opt_1로 실어 보내면 KCP가 인증 결과 콜백(Ret_URL)에 그대로 echo한다.
				// 프론트 콜백이 이 값으로 confirm에 필요한 ordrIdxx를 회수한다. (KCP는 register 전문의 param_opt_*만 echo)
				"param_opt_1" to command.ordrIdxx,
			),
		)
		val encrypted: EncryptedRegisterData = kcpCertCryptoPort.encryptRegisterData(plainJson)

		val response: KcpRegisterResponse = kcpRestClient.post()
			.uri("/api/reg/certDataReg.do")
			.contentType(MediaType.APPLICATION_JSON)
			.header("site_cd", kcpProperties.siteCd)
			.header("rv", encrypted.rv)
			.body(encrypted.encData)
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
