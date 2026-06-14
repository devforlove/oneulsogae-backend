package com.org.meeple.auth.oauth

import com.org.meeple.auth.PrincipalDetails
import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.user.application.port.`in`.RegisterUserUseCase
import com.org.meeple.core.user.domain.User
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.stereotype.Service

@Service
class CustomOAuth2UserService(
	private val registerUserUseCase: RegisterUserUseCase,
) : OAuth2UserService<OAuth2UserRequest, OAuth2User> {

	private val delegate = DefaultOAuth2UserService()

	override fun loadUser(userRequest: OAuth2UserRequest): OAuth2User {
		val oAuth2User: OAuth2User = delegate.loadUser(userRequest)
		val provider: String = userRequest.clientRegistration.registrationId
		val userInfo: OAuth2UserInfo = OAuth2UserInfo.of(provider, oAuth2User.attributes)

		// 회원가입 검증 실패(이메일 누락/중복 등)는 인증 필터가 실패로 인식해 OAuth2FailureHandler로 보내도록
		// OAuth2AuthenticationException으로 감싼다. (감싸지 않으면 인증 밖 예외라 500 서버 에러 페이지가 노출됨)
		val user: User = try {
			registerUserUseCase.registerIfAbsent(
				provider = provider,
				providerId = userInfo.providerId,
				email = userInfo.email,
				profileImageUrl = userInfo.profileImageUrl,
			)
		} catch (e: BusinessException) {
			throw OAuth2AuthenticationException(OAuth2Error(e.errorCode.code), e.message, e)
		}

		return PrincipalDetails(
			email = user.email ?: "",
			id = user.id,
			authorities = listOf(SimpleGrantedAuthority(user.role.authority())),
			attributes = oAuth2User.attributes,
		)
	}
}
