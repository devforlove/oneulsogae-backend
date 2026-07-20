package com.org.oneulsogae.infra.config

import org.springframework.boot.context.properties.ConfigurationProperties

/** CI 앱단 암호화 키. (@ConfigurationPropertiesScan으로 자동 등록) */
@ConfigurationProperties(prefix = "app.identity")
data class IdentityCryptoProperties(
	val ciEncryptionKey: String = "",
)
