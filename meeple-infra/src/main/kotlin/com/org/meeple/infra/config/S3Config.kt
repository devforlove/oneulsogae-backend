package com.org.meeple.infra.config

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3ClientBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.net.URI

/**
 * S3 동기 클라이언트([S3Client]) 빈을 등록한다. 접속 설정은 [S3Properties]로 주입된다.
 * - 엔드포인트가 지정되면(로컬 LocalStack) 해당 URL로 override하고, 비면 실제 AWS 기본 엔드포인트를 쓴다.
 * - 정적 자격증명이 있으면 그것으로, 없으면 기본 자격증명 체인(IAM 역할 등)으로 접속한다.
 * 빈 생성만으로는 연결하지 않으며(첫 호출 시 접속), 종료 시 커넥션을 닫도록 destroyMethod를 건다.
 */
@Configuration
class S3Config(
	private val properties: S3Properties,
) {

	@Bean(destroyMethod = "close")
	fun s3Client(): S3Client {
		val builder: S3ClientBuilder = S3Client.builder()
			.region(Region.of(properties.region))
			.forcePathStyle(properties.pathStyleAccess)
			.credentialsProvider(credentialsProvider())
			.httpClientBuilder(UrlConnectionHttpClient.builder())

		if (properties.endpoint.isNotBlank()) {
			builder.endpointOverride(URI.create(properties.endpoint))
		}
		return builder.build()
	}

	private fun credentialsProvider(): AwsCredentialsProvider =
		if (properties.accessKey.isNotBlank() && properties.secretKey.isNotBlank()) {
			StaticCredentialsProvider.create(AwsBasicCredentials.create(properties.accessKey, properties.secretKey))
		} else {
			DefaultCredentialsProvider.create()
		}
}
