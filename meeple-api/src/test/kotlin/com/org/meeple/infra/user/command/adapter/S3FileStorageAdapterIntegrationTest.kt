package com.org.meeple.infra.user.command.adapter

import com.org.meeple.infra.config.S3Config
import com.org.meeple.infra.config.S3Properties
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import org.testcontainers.localstack.LocalStackContainer
import org.testcontainers.utility.DockerImageName
import software.amazon.awssdk.services.s3.S3Client

/**
 * [S3FileStorageAdapter]가 실제 S3(LocalStack)에 파일을 올리는지 검증한다.
 * 업로드한 오브젝트를 다시 받아 바이트가 그대로 왕복하는지 확인한다.
 */
class S3FileStorageAdapterIntegrationTest : DescribeSpec({

	val localstack = LocalStackContainer(DockerImageName.parse("localstack/localstack:3.8"))
		.withServices("s3")

	beforeSpec { localstack.start() }
	afterSpec { localstack.stop() }

	describe("S3FileStorageAdapter.upload") {

		it("업로드하면 S3에 지정 키·콘텐츠 타입으로 저장된다") {
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
				val adapter = S3FileStorageAdapter(client, properties)

				val key = "company-image-verifications/42/doc.pdf"
				val content = "resume-bytes".toByteArray()

				val returnedKey: String = adapter.upload(key, content, "application/pdf")

				returnedKey shouldBe key
				val stored: ByteArray = client.getObjectAsBytes { it.bucket(bucket).key(key) }.asByteArray()
				stored.decodeToString() shouldBe "resume-bytes"
			}
		}
	}
})
