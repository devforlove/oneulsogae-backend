package com.org.oneulsogae.config

import com.org.oneulsogae.auth.AuthUserArgumentResolver
import org.springframework.context.annotation.Configuration
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/** 커스텀 컨트롤러 인자 리졸버(@LoginUser 등)를 등록한다. */
@Configuration
class WebConfig(
	private val authUserArgumentResolver: AuthUserArgumentResolver,
) : WebMvcConfigurer {

	override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
		resolvers.add(authUserArgumentResolver)
	}
}
