package com.org.meeple.infra.user.command.adapter

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.org.meeple.common.user.Gender
import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.user.UserErrorCode
import com.org.meeple.core.user.command.application.port.out.KcpCertCryptoPort
import com.org.meeple.core.user.command.application.port.out.KcpCertQueryPort
import com.org.meeple.core.user.command.domain.CertifiedIdentity
import com.org.meeple.infra.config.KcpProperties
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * KCP 결과조회 어댑터. (POST {baseUrl}/api/query/getCertData.do)
 * enc_cert_data 복호화 후 KCP 필드를 [CertifiedIdentity]로 매핑한다.
 * 복호화 결과 필드명/코드값(sex_code, local_flag 등)은 KCP 결과 매뉴얼 확정본으로 실연동 시 재확인한다.
 */
@Component
class KcpCertQueryAdapter(
	private val kcpRestClient: RestClient,
	private val kcpProperties: KcpProperties,
	private val kcpCertCryptoPort: KcpCertCryptoPort,
	private val objectMapper: ObjectMapper,
) : KcpCertQueryPort {

	override fun query(regCertKey: String, ordrIdxx: String): CertifiedIdentity {
		val response: KcpQueryResponse = kcpRestClient.post()
			.uri("/api/query/getCertData.do")
			.contentType(MediaType.APPLICATION_JSON)
			.body(mapOf("reg_cert_key" to regCertKey, "ordr_idxx" to ordrIdxx))
			.retrieve()
			.body<KcpQueryResponse>()
			?: throw BusinessException(UserErrorCode.KCP_QUERY_FAILED)

		if (response.resCd != SUCCESS_CODE || response.encCertData == null) {
			throw BusinessException(UserErrorCode.KCP_QUERY_FAILED, "res_cd=${response.resCd}, res_msg=${response.resMsg}")
		}

		val decrypted: String = kcpCertCryptoPort.decryptCertData(response.encCertData)
		val data: KcpCertData = objectMapper.readValue(decrypted, KcpCertData::class.java)
		return data.toCertifiedIdentity()
	}

	companion object {
		private const val SUCCESS_CODE: String = "0000"
		private val BIRTHDAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
	}

	private fun KcpCertData.toCertifiedIdentity(): CertifiedIdentity =
		CertifiedIdentity(
			realName = userName,
			birthday = LocalDate.parse(birthDay, BIRTHDAY_FORMAT),
			gender = if (sexCode == MALE_CODE) Gender.MALE else Gender.FEMALE,
			phoneNumber = phoneNo,
			ci = ci,
			di = di,
			foreigner = localFlag != LOCAL_CODE,
			telecom = commId,
		)
}

private const val MALE_CODE: String = "01"
private const val LOCAL_CODE: String = "01"

data class KcpQueryResponse(
	@JsonProperty("res_cd") val resCd: String?,
	@JsonProperty("res_msg") val resMsg: String?,
	@JsonProperty("enc_cert_data") val encCertData: String?,
)

/** KCP 복호화 결과(dec_data). 필드명은 KCP 결과 매뉴얼 기준(실연동 시 확정). */
data class KcpCertData(
	@JsonProperty("user_name") val userName: String,
	@JsonProperty("birth_day") val birthDay: String,
	@JsonProperty("sex_code") val sexCode: String,
	@JsonProperty("phone_no") val phoneNo: String,
	@JsonProperty("ci") val ci: String,
	@JsonProperty("di") val di: String,
	@JsonProperty("local_flag") val localFlag: String,
	@JsonProperty("comm_id") val commId: String,
)
