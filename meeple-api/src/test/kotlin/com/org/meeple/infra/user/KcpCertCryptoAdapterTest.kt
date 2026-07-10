package com.org.meeple.infra.user

import com.org.meeple.infra.config.KcpProperties
import com.org.meeple.infra.user.command.adapter.KcpCertCryptoAdapter
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank

/**
 * KCP 공식 라이브러리(utils.Crypto) 연동 검증. KCP가 제공한 테스트 site_cd/ENC_KEY로
 * encryptRegisterData → decryptCertData 라운드트립이 원본을 복원하는지 확인한다.
 * (JAR 클래스패스 적재 + Map 키("encData"/"rv") 추출까지 함께 검증)
 */
class KcpCertCryptoAdapterTest : DescribeSpec({

	// KCP 제공 본인확인 V2 테스트 사이트코드/ENC_KEY (샘플 site_conf_inc.jsp 기준)
	val adapter = KcpCertCryptoAdapter(
		KcpProperties(
			siteCd = "AO7F3",
			encKey = "c2a22fa3ebe4698075bcac6b433d52e351c881b02fb83488d4283a43385b1f8e",
		),
	)

	describe("encryptRegisterData → decryptCertData") {

		it("암호화 결과의 encData/rv로 복호화하면 원본 JSON을 복원한다") {
			val plainJson = """{"site_cd":"AO7F3","ordr_idxx":"TEST1234567890","Ret_URL":"https://x/return","web_siteid":""}"""

			val encrypted = adapter.encryptRegisterData(plainJson)
			encrypted.encData.shouldNotBeBlank()
			encrypted.rv.shouldNotBeBlank()

			val decrypted = adapter.decryptCertData(encrypted.encData, encrypted.rv)
			decrypted shouldBe plainJson
		}
	}
})
