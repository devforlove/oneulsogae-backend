package com.org.oneulsogae.infra.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 공개 이미지 프록시(/images/{key}) 설정. (@ConfigurationPropertiesScan으로 자동 등록)
 * 이미지 템플릿(image_templates)에는 앱 클라이언트가 그대로 로드할 절대 URL을 저장해야 해서,
 * 프록시 앞에 붙일 API 서버 공개 base URL을 여기서 주입한다.
 */
@ConfigurationProperties(prefix = "app.image-proxy")
data class ImageProxyProperties(
	/** API 서버 공개 base URL. (예: https://api.oneulsogae.com — 끝 슬래시 없이) */
	val publicBaseUrl: String,
)
