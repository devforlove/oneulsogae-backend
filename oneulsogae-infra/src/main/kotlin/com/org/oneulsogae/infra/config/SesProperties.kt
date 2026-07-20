package com.org.oneulsogae.infra.config

import org.springframework.boot.context.properties.ConfigurationProperties

/** SES 이메일 발송 설정. (@ConfigurationPropertiesScan으로 자동 등록) */
@ConfigurationProperties(prefix = "app.ses")
data class SesProperties(
	val region: String = "ap-northeast-2",
	/** 발신 주소. SES에서 검증된 도메인의 주소여야 한다. */
	val fromAddress: String = "no-reply@meeple.life",
)
