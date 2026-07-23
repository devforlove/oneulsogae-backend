package com.org.oneulsogae.infra.popup.query

import com.org.oneulsogae.admin.popup.query.service.port.out.PopupImageUrlPort
import com.org.oneulsogae.infra.config.S3Properties
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import java.time.Duration

/**
 * 팝업 이미지의 presigned GET URL 발급 어댑터.
 * 비공개 버킷의 팝업 이미지 오브젝트에 대해 일정 시간([S3Properties.presignedGetExpiryMinutes]) 유효한
 * presigned GET URL을 발급한다. (서명은 로컬에서 이뤄져 S3 네트워크 왕복이 없다)
 */
@Component
class S3PopupImageUrlAdapter(
	private val s3Presigner: S3Presigner,
	private val s3Properties: S3Properties,
) : PopupImageUrlPort {

	override fun presignedGetUrl(imageKey: String): String {
		val getObjectRequest: GetObjectRequest = GetObjectRequest.builder()
			.bucket(s3Properties.bucket)
			.key(imageKey)
			.build()
		val presignRequest: GetObjectPresignRequest = GetObjectPresignRequest.builder()
			.signatureDuration(Duration.ofMinutes(s3Properties.presignedGetExpiryMinutes))
			.getObjectRequest(getObjectRequest)
			.build()
		return s3Presigner.presignGetObject(presignRequest).url().toString()
	}
}
