package com.org.meeple.infra.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.kcp")
data class KcpProperties(
	val siteCd: String = "",
	val encKey: String = "",
	val webSiteId: String = "",
	val baseUrl: String = "https://testcert.kcp.co.kr",
	val retUrl: String = "",
)
