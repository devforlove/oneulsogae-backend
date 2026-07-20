package com.org.oneulsogae.config

import com.org.oneulsogae.auth.LoginUser
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springdoc.core.utils.SpringDocUtils
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * springdoc-openapi 문서 설정. API 메타정보와 JWT Bearer 보안 스킴을 등록한다.
 * 모든 엔드포인트가 JWT 인증이므로, Swagger UI "Authorize"에 access token을 넣으면 Try it out으로 호출할 수 있다.
 */
@Configuration
class OpenApiConfig {

	init {
		// @LoginUser로 주입되는 인증 주체(AuthUser)는 argument resolver가 채우므로, 문서의 요청 파라미터로 노출하지 않는다.
		SpringDocUtils.getConfig().addAnnotationsToIgnore(LoginUser::class.java)
	}

	@Bean
	fun openAPI(): OpenAPI {
		val schemeName: String = "bearer-jwt"
		return OpenAPI()
			.info(
				Info()
					.title("Oneulsogae API")
					.version("v1"),
			)
			.components(
				Components().addSecuritySchemes(
					schemeName,
					SecurityScheme()
						.type(SecurityScheme.Type.HTTP)
						.scheme("bearer")
						.bearerFormat("JWT"),
				),
			)
			.addSecurityItem(SecurityRequirement().addList(schemeName))
	}
}
