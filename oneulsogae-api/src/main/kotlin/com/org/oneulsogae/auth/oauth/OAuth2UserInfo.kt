package com.org.oneulsogae.auth.oauth

/**
 * OAuth2 provider별 사용자 정보를 표준 형태로 추상화한 DTO.
 * 새 provider를 지원하려면 [of]의 분기와 `ofXxx` 파서를 추가하면 된다.
 */
@ConsistentCopyVisibility
data class OAuth2UserInfo private constructor(
	val providerId: String,
	val nickname: String,
	val email: String?,
	val profileImageUrl: String?,
) {

	companion object {

		fun of(registrationId: String, attributes: Map<String, Any>): OAuth2UserInfo =
			when (registrationId) {
				"google" -> ofGoogle(attributes)
				"kakao" -> ofKakao(attributes)
				else -> throw IllegalArgumentException("지원하지 않는 OAuth2 provider 입니다: $registrationId")
			}

		private fun ofGoogle(attributes: Map<String, Any>): OAuth2UserInfo =
			OAuth2UserInfo(
				providerId = attributes["sub"] as String,
				nickname = attributes["name"] as? String ?: "구글사용자",
				email = attributes["email"] as? String,
				profileImageUrl = attributes["picture"] as? String,
			)

		private fun ofKakao(attributes: Map<String, Any>): OAuth2UserInfo {
			val account: Map<String, Any> = attributes["kakao_account"].asStringMap()
			val profile: Map<String, Any> = account["profile"].asStringMap()

			return OAuth2UserInfo(
				providerId = attributes["id"].toString(),
				nickname = profile["nickname"] as? String ?: "카카오사용자",
				email = account["email"] as? String,
				profileImageUrl = profile["profile_image_url"] as? String,
			)
		}

		@Suppress("UNCHECKED_CAST")
		private fun Any?.asStringMap(): Map<String, Any> =
			(this as? Map<String, Any>) ?: emptyMap()
	}
}
