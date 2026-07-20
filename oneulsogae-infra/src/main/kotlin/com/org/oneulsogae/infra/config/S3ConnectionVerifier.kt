package com.org.oneulsogae.infra.config

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.HeadBucketRequest

/**
 * 로컬(local 프로파일) 기동 시 S3 버킷에 접근 가능한지 1회 확인해 로그로 알린다.
 * "로컬에서 S3 환경이 실제로 붙었는지" 눈으로 확인하기 위한 것으로, 실패해도 앱 기동은 막지 않는다.
 * (LocalStack 컨테이너가 떠 있지 않으면 경고만 남긴다) 운영/테스트 프로파일에서는 로드되지 않는다.
 */
@Component
@Profile("local")
class S3ConnectionVerifier(
	private val s3Client: S3Client,
	private val properties: S3Properties,
) : ApplicationRunner {

	private val log: Logger = LoggerFactory.getLogger(javaClass)

	override fun run(args: ApplicationArguments) {
		val endpoint: String = properties.endpoint.ifBlank { "AWS 기본 엔드포인트" }
		try {
			s3Client.headBucket(HeadBucketRequest.builder().bucket(properties.bucket).build())
			log.info("S3 연결 확인 완료: 버킷 '{}' 접근 가능 (endpoint={})", properties.bucket, endpoint)
		} catch (e: Exception) {
			log.warn(
				"S3 연결 확인 실패: 버킷 '{}'에 접근할 수 없습니다. docker-compose의 localstack이 떠 있는지 확인하세요. (endpoint={}, 원인={})",
				properties.bucket,
				endpoint,
				e.message,
			)
		}
	}
}
