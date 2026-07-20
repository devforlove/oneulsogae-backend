package com.org.oneulsogae.infra.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sesv2.SesV2Client
import java.time.Duration

/**
 * SES 클라이언트 설정. prod 프로파일에서만 활성화된다(local·test는 로깅 스텁이 발송을 대신한다).
 * 자격 증명은 기본 체인(EC2 IAM 인스턴스 롤)을 쓰므로 키 설정이 없다.
 * 발송이 요청 트랜잭션 안에서 실행되므로, SES 지연이 DB 커넥션 점유로 번지지 않도록 API 호출 시간에 상한을 둔다.
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
			.overrideConfiguration(
				ClientOverrideConfiguration.builder()
					.apiCallAttemptTimeout(API_CALL_ATTEMPT_TIMEOUT)
					.apiCallTimeout(API_CALL_TIMEOUT)
					.build(),
			)
			.build()

	companion object {

		/** 시도 1회의 상한. */
		private val API_CALL_ATTEMPT_TIMEOUT: Duration = Duration.ofSeconds(5)

		/** 재시도를 포함한 호출 전체의 상한. */
		private val API_CALL_TIMEOUT: Duration = Duration.ofSeconds(10)
	}
}
