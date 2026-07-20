package com.org.oneulsogae.infra.config

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import org.testcontainers.localstack.LocalStackContainer
import org.testcontainers.utility.DockerImageName
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client

/**
 * [S3Config]가 만든 [S3Client]가 설정한 엔드포인트의 S3와 실제로 통신하는지 검증한다.
 * docker-compose와 동일한 LocalStack 이미지를 Testcontainer로 띄워, 버킷 생성·객체 put/get 왕복을 확인한다.
 * (로컬 S3 환경의 "연결 검증"을 자동화한 것 — 실제 업로드 기능은 아직 없다)
 */
class S3ClientIntegrationTest : DescribeSpec({

	// docker-compose와 동일한 커뮤니티(무료) 이미지. CalVer 이미지는 라이선스 토큰을 요구한다.
	val localstack = LocalStackContainer(DockerImageName.parse("localstack/localstack:3.8"))
		.withServices("s3")

	beforeSpec { localstack.start() }
	afterSpec { localstack.stop() }

	describe("S3Config가 만든 S3Client는") {

		it("설정한 엔드포인트의 S3와 객체를 put/get 왕복한다") {
			val bucket = "verify-bucket"
			val properties = S3Properties(
				bucket = bucket,
				region = localstack.region,
				endpoint = localstack.endpoint.toString(),
				pathStyleAccess = true,
				accessKey = localstack.accessKey,
				secretKey = localstack.secretKey,
			)
			val s3Client: S3Client = S3Config(properties).s3Client()

			s3Client.use { client: S3Client ->
				client.createBucket { it.bucket(bucket) }
				client.putObject({ it.bucket(bucket).key("hello.txt") }, RequestBody.fromString("hello oneulsogae"))

				val content: String = client.getObjectAsBytes { it.bucket(bucket).key("hello.txt") }.asUtf8String()

				content shouldBe "hello oneulsogae"
			}
		}
	}
})
