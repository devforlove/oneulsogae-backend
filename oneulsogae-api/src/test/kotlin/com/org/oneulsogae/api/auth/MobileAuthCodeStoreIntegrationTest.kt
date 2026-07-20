package com.org.oneulsogae.api.auth

import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.infra.auth.code.MobileAuthCodeStore
import com.org.oneulsogae.infra.auth.code.StoredTokens
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class MobileAuthCodeStoreIntegrationTest(
	private val store: MobileAuthCodeStore,
) : AbstractIntegrationSupport({

	describe("MobileAuthCodeStore") {
		context("issue로 발급한 code를") {
			it("consume하면 저장했던 토큰을 돌려준다") {
				val code: String = store.issue(StoredTokens("access-1", "refresh-1"))
				code shouldNotBe ""

				val consumed: StoredTokens? = store.consume(code)

				consumed shouldBe StoredTokens("access-1", "refresh-1")
			}

			it("한 번 consume하면 재사용할 수 없다(단일 사용)") {
				val code: String = store.issue(StoredTokens("access-2", "refresh-2"))
				store.consume(code)

				store.consume(code).shouldBeNull()
			}
		}

		context("존재하지 않는 code를") {
			it("consume하면 null이다") {
				store.consume("no-such-code").shouldBeNull()
			}
		}
	}
})
