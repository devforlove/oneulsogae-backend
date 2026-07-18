package com.org.meeple.infra.config

import org.springframework.boot.context.properties.ConfigurationProperties

/** 토스페이먼츠 결제 연동 설정. (@ConfigurationPropertiesScan으로 자동 등록) */
@ConfigurationProperties(prefix = "app.toss")
data class TossProperties(
	val secretKey: String = "",
	val baseUrl: String = "https://api.tosspayments.com",
)
