package com.org.meeple.infra.gathering.query

import com.org.meeple.admin.gathering.query.service.port.out.GatheringImageUrlPort as AdminGatheringImageUrlPort
import com.org.meeple.core.gathering.query.service.port.out.GatheringImageUrlPort as UserGatheringImageUrlPort
import com.org.meeple.infra.config.S3Properties
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import java.time.Duration

/**
 * 모임 대표 이미지의 presigned GET URL 발급 어댑터. (어드민·유저 두 [GatheringImageUrlPort]를 함께 구현)
 * 비공개 버킷의 대표 이미지 오브젝트에 대해
 * 일정 시간([S3Properties.presignedGetExpiryMinutes]) 유효한 presigned GET URL을 발급한다.
 * (서명은 로컬에서 이뤄져 S3 네트워크 왕복이 없다)
 */
@Component
class S3GatheringImageUrlAdapter(
	private val s3Presigner: S3Presigner,
	private val s3Properties: S3Properties,
) : AdminGatheringImageUrlPort, UserGatheringImageUrlPort {

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
