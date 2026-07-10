package com.org.meeple.infra.user.command.adapter

import com.fasterxml.jackson.annotation.JsonProperty
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
import tools.jackson.databind.ObjectMapper
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * KCP 결과조회 어댑터. (POST {baseUrl}/api/query/getCertData.do)
 * 평문 JSON 요청(Header: site_cd) → 응답의 enc_cert_data를 동봉된 rv로 복호화해 [CertifiedIdentity]로 매핑한다.
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
			.header("site_cd", kcpProperties.siteCd)
			.body(mapOf("reg_cert_key" to regCertKey, "ordr_idxx" to ordrIdxx))
			.retrieve()
			.body<KcpQueryResponse>()
			?: throw BusinessException(UserErrorCode.KCP_QUERY_FAILED)

		if (response.resCd != SUCCESS_CODE || response.encCertData == null || response.rv == null) {
			throw BusinessException(UserErrorCode.KCP_QUERY_FAILED, "res_cd=${response.resCd}, res_msg=${response.resMsg}")
		}

		val decrypted: String = kcpCertCryptoPort.decryptCertData(response.encCertData, response.rv)
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
			// CI/DI는 특수문자 유실 방지를 위해 URL 인코딩된 값을 내려주므로 URL 디코딩해 사용한다. (가이드 3-5)
			ci = URLDecoder.decode(ciUrl, StandardCharsets.UTF_8),
			di = URLDecoder.decode(diUrl, StandardCharsets.UTF_8),
			foreigner = localCode != LOCAL_CODE,
			telecom = commId,
		)
}

private const val MALE_CODE: String = "01"
private const val LOCAL_CODE: String = "01"

data class KcpQueryResponse(
	@JsonProperty("res_cd") val resCd: String?,
	@JsonProperty("res_msg") val resMsg: String?,
	@JsonProperty("enc_cert_data") val encCertData: String?,
	@JsonProperty("rv") val rv: String?,
)

/** KCP 복호화 결과(dec_data). 필드명·코드값은 본인확인 V2 가이드 3-5 기준. */
data class KcpCertData(
	@JsonProperty("user_name") val userName: String,
	@JsonProperty("birth_day") val birthDay: String,
	@JsonProperty("sex_code") val sexCode: String,
	@JsonProperty("phone_no") val phoneNo: String,
	@JsonProperty("CI_URL") val ciUrl: String,
	@JsonProperty("DI_URL") val diUrl: String,
	@JsonProperty("local_code") val localCode: String,
	@JsonProperty("comm_id") val commId: String,
)
