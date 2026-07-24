package com.org.oneulsogae.core.payments.command.application.port.out

import com.org.oneulsogae.core.payments.command.domain.StorePlatform

/**
 * 스토어 영수증 검증 아웃 포트. iOS(App Store Server API)·Android(Google Play Developer API)에
 * 영수증(purchaseToken)을 검증해 정품·미소비 구매인지 확인한다.
 * 구현 어댑터는 인프라 레이어에 둔다(자격증명·외부 호출은 어댑터 책임).
 */
interface StoreReceiptVerifierPort {

	/** 검증에 성공(정품·유효)하면 [VerifiedReceipt], 실패면 예외를 던진다. */
	fun verify(
		platform: StorePlatform,
		productId: String,
		purchaseToken: String,
		transactionId: String,
	): VerifiedReceipt
}

/** 검증된 영수증 — 스토어가 확인한 상품 id와 거래 id. */
data class VerifiedReceipt(
	val productId: String,
	val transactionId: String,
)
