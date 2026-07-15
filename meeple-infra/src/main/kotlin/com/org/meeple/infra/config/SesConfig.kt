package com.org.meeple.infra.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sesv2.SesV2Client

/**
 * SES 클라이언트 설정. prod 프로파일에서만 활성화된다(local·test는 로깅 스텁이 발송을 대신한다).
 * 자격 증명은 기본 체인(EC2 IAM 인스턴스 롤)을 쓰므로 키 설정이 없다.
 */
@Configuration
@Profile("prod")
class SesConfig(
	private val properties: SesProperties,
) {

	@Bean(destroyMethod = "close")
	fun sesV2Client(): SesV2Client =
		SesV2Client.builder()
			.region(Region.of(properties.region))
			.credentialsProvider(DefaultCredentialsProvider.create())
			.httpClientBuilder(UrlConnectionHttpClient.builder())
			.build()
}
