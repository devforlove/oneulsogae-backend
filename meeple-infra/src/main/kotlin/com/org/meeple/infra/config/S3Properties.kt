package com.org.meeple.infra.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * S3(파일 스토리지) 접속 설정. `app.s3.*` 프로퍼티로 주입된다.
 * 기본값은 실제 AWS를 향하고(엔드포인트 override 없음·가상 호스트 스타일·기본 자격증명 체인),
 * 로컬(LocalStack)에서는 application-local.yml이 엔드포인트·path-style·더미 자격증명을 덮어쓴다.
 */
@ConfigurationProperties(prefix = "app.s3")
data class S3Properties(
	/** 사용할 버킷 이름. */
	val bucket: String,
	/** AWS 리전. (예: ap-northeast-2) */
	val region: String,
	/**
	 * S3 엔드포인트 override. 로컬(LocalStack/MinIO)에서만 지정하고, 실제 AWS면 비워 둔다(SDK 기본 엔드포인트 사용).
	 */
	val endpoint: String = "",
	/** path-style 접근 여부. LocalStack/MinIO는 true, 실제 AWS(가상 호스트 스타일)는 false. */
	val pathStyleAccess: Boolean = false,
	/** 정적 액세스 키. 비우면 [software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider](IAM 역할·환경변수 등)를 쓴다. */
	val accessKey: String = "",
	/** 정적 시크릿 키. [accessKey]와 함께 비우면 기본 자격증명 체인을 쓴다. */
	val secretKey: String = "",
	/** presigned GET URL 서명 유효시간(분). 어드민 서류 열람용. */
	val presignedGetExpiryMinutes: Long = 10,
)
