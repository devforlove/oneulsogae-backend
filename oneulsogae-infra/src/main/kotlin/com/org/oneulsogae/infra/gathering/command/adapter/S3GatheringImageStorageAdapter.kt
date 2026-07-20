package com.org.oneulsogae.infra.gathering.command.adapter

import com.org.oneulsogae.admin.gathering.command.application.port.out.UploadGatheringImagePort
import com.org.oneulsogae.infra.config.S3Properties
import org.springframework.stereotype.Component
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest

/**
 * [UploadGatheringImagePort]의 S3 구현. 설정된 버킷([S3Properties.bucket])에 모임 대표 이미지를 올린다.
 * 버킷/객체는 비공개이므로 저장 후 DB에는 오브젝트 키만 보관하고, 열람용 URL은 조회 시 presigned로 발급한다.
 */
@Component
class S3GatheringImageStorageAdapter(
	private val s3Client: S3Client,
	private val s3Properties: S3Properties,
) : UploadGatheringImagePort {

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
