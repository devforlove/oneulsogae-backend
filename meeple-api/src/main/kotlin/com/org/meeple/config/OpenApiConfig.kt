package com.org.meeple.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * springdoc-openapi 문서 설정. API 메타정보와 JWT Bearer 보안 스킴을 등록한다.
 * 모든 엔드포인트가 JWT 인증이므로, Swagger UI "Authorize"에 access token을 넣으면 Try it out으로 호출할 수 있다.
 */
@Configuration
class OpenApiConfig {

	@Bean
	fun openAPI(): OpenAPI {
		val schemeName: String = "bearer-jwt"
		return OpenAPI()
			.info(
				Info()
					.title("Meeple API")
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
