package com.org.oneulsogae.infra.popup.command.adapter

import com.org.oneulsogae.admin.popup.command.application.port.out.UploadPopupImagePort
import com.org.oneulsogae.infra.config.S3Properties
import org.springframework.stereotype.Component
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest

/**
 * [UploadPopupImagePort]의 S3 구현. 설정된 버킷([S3Properties.bucket])에 팝업 이미지를 올린다.
 * 버킷/객체는 비공개이므로 열람은 공개 프록시(/images/{key})가 presigned URL로 리다이렉트한다.
 */
@Component
class S3PopupImageStorageAdapter(
	private val s3Client: S3Client,
	private val s3Properties: S3Properties,
) : UploadPopupImagePort {

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
