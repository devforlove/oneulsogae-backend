package com.org.meeple.infra.user.command.adapter

import com.org.meeple.core.gathering.command.application.port.out.FileStoragePort as GatheringFileStoragePort
import com.org.meeple.core.user.command.application.port.out.FileStoragePort as UserFileStoragePort
import com.org.meeple.infra.config.S3Properties
import org.springframework.stereotype.Component
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest

/**
 * S3 파일 저장 어댑터. [S3Client] 빈으로 설정된 버킷([S3Properties.bucket])에 객체를 올린다.
 * 버킷/객체는 비공개(퍼블릭 ACL 없음)이므로, 저장 후 DB에는 오브젝트 키만 보관하고 공개 URL은 만들지 않는다.
 * user(직장 이미지 인증)·gathering(멤버 인증) 두 도메인의 동일한 파일 저장 out-port를 한 어댑터에서 함께 구현한다.
 * (어드민 조회가 필요해지면 presigned GET URL을 발급하는 조회 포트를 추가한다)
 */
@Component
class S3FileStorageAdapter(
	private val s3Client: S3Client,
	private val s3Properties: S3Properties,
) : UserFileStoragePort, GatheringFileStoragePort {

	override fun upload(key: String, content: ByteArray, contentType: String): String {
		val request: PutObjectRequest = PutObjectRequest.builder()
			.bucket(s3Properties.bucket)
			.key(key)
			.contentType(contentType)
			.build()
		s3Client.putObject(request, RequestBody.fromBytes(content))
		return key
	}
}
