package com.org.oneulsogae.infra.payments.command.adapter

import com.org.oneulsogae.core.payments.command.application.port.out.StoreReceiptVerifierPort
import com.org.oneulsogae.core.payments.command.application.port.out.VerifiedReceipt
import com.org.oneulsogae.core.payments.command.domain.StorePlatform
import org.springframework.stereotype.Component

/**
 * [StoreReceiptVerifierPort] 스텁 구현 — 영수증 형식만 확인하고 통과시킨다.
 *
 * TODO(검증 연동): 실제 정품 검증을 붙여야 한다.
 *  - iOS: App Store Server API(JWS 서명 검증 or /inApps/v1/transactions/{id} 조회).
 *    App Store Connect API Key(발급자 id·key id·.p8)가 필요하다.
 *  - Android: Google Play Developer API purchases.products.get.
 *    서비스 계정(JSON key)과 Play Console 권한이 필요하다.
 *  자격증명이 준비되면 platform으로 분기해 각 스토어에 검증 요청하고, 위조·환불·중복
 *  소비를 걸러낸 뒤에만 VerifiedReceipt를 반환하도록 교체한다.
 *
 * 지금은 자격증명이 없어, 토큰이 비어 있지 않은지만 확인한다(개발용). 운영 배포 전 반드시 교체.
 */
@Component
class StubStoreReceiptVerifierAdapter : StoreReceiptVerifierPort {

	override fun verify(
		platform: StorePlatform,
		productId: String,
		purchaseToken: String,
		transactionId: String,
	): VerifiedReceipt {
		require(purchaseToken.isNotBlank()) { "영수증 토큰이 비어 있습니다." }
		require(transactionId.isNotBlank()) { "거래 식별자가 비어 있습니다." }
		return VerifiedReceipt(productId = productId, transactionId = transactionId)
	}
}
