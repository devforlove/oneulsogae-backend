package com.org.meeple.common.config

import com.org.meeple.admin.companyverification.query.service.port.out.CompanyVerificationImageUrlPort
import com.org.meeple.admin.gathering.command.application.port.out.UploadGatheringImagePort
import com.org.meeple.admin.memberverification.query.service.port.out.MemberVerificationImageUrlPort
import com.org.meeple.admin.gathering.query.service.port.out.GatheringImageUrlPort as AdminGatheringImageUrlPort
import com.org.meeple.core.gathering.command.application.port.out.FileStoragePort as GatheringFileStoragePort
import com.org.meeple.core.gathering.query.service.port.out.GatheringImageUrlPort as UserGatheringImageUrlPort
import com.org.meeple.core.lounge.command.application.port.out.FileStoragePort as LoungeFileStoragePort
import com.org.meeple.core.lounge.query.service.port.out.LoungeImageUrlPort
import com.org.meeple.core.user.command.application.port.out.FileStoragePort as UserFileStoragePort
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

/**
 * 통합 테스트에서 S3 관련 out-port를 인메모리 페이크로 대체한다.
 * - [FileStoragePort](업로드): 넘어온 key를 그대로 돌려준다.
 * - [CompanyVerificationImageUrlPort](presign): imageKey로 결정적 URL을 만든다.
 * E2E 컨텍스트에는 실제 S3(LocalStack)를 띄우지 않으므로 컨트롤러→서비스→DB 슬라이스만 검증한다.
 * 실제 S3 업로드는 S3FileStorageAdapterIntegrationTest(LocalStack)에서 따로 검증한다. (presign 서명은 별도 통합 테스트 없음)
 * [AbstractIntegrationSupport]에 등록돼 모든 통합 테스트가 단일 컨텍스트를 공유한다.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestFileStorageConfig {

	@Bean
	@Primary
	fun fakeFileStoragePort(): UserFileStoragePort =
		UserFileStoragePort { key: String, _: ByteArray, _: String -> key }

	@Bean
	@Primary
	fun fakeGatheringFileStoragePort(): GatheringFileStoragePort =
		GatheringFileStoragePort { key: String, _: ByteArray, _: String -> key }

	@Bean
	@Primary
	fun fakeLoungeFileStoragePort(): LoungeFileStoragePort =
		LoungeFileStoragePort { key: String, _: ByteArray, _: String -> key }

	@Bean
	@Primary
	fun fakeLoungeImageUrlPort(): LoungeImageUrlPort =
		LoungeImageUrlPort { imageKey: String -> "https://presigned.test/$imageKey" }

	@Bean
	@Primary
	fun fakeCompanyVerificationImageUrlPort(): CompanyVerificationImageUrlPort =
		CompanyVerificationImageUrlPort { imageKey: String -> "https://presigned.test/$imageKey" }

	@Bean
	@Primary
	fun fakeMemberVerificationImageUrlPort(): MemberVerificationImageUrlPort =
		MemberVerificationImageUrlPort { imageKey: String -> "https://presigned.test/$imageKey" }

	@Bean
	@Primary
	fun fakeUploadGatheringImagePort(): UploadGatheringImagePort =
		UploadGatheringImagePort { key: String, _: ByteArray, _: String -> key }

	@Bean
	@Primary
	fun fakeGatheringImageUrlPort(): AdminGatheringImageUrlPort =
		AdminGatheringImageUrlPort { imageKey: String -> "https://presigned.test/$imageKey" }

	@Bean
	@Primary
	fun fakeUserGatheringImageUrlPort(): UserGatheringImageUrlPort =
		UserGatheringImageUrlPort { imageKey: String -> "https://presigned.test/$imageKey" }
}
