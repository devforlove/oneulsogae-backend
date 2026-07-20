package com.org.oneulsogae.api.popup

import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.expect
import com.org.oneulsogae.common.integration.get
import com.org.oneulsogae.common.popup.PopupType
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.popup.command.entity.PopupEntity
import com.org.oneulsogae.infra.popup.command.entity.QPopupEntity
import java.time.LocalDateTime

/**
 * `GET /popups/v1`에서 1회성 팝업([PopupType.removeAfterView])이 한 번 조회되면 제거(soft-delete)되는지 검증하는 E2E.
 * - 환불 팝업(MATCH_FAILED_REFUND)은 첫 조회엔 내려오고, 같은 사용자의 두 번째 조회엔 사라진다.
 * - 일반(NORMAL) 팝업은 조회해도 계속 노출된다.
 */
class GetPopupsViewOnceRemovalE2ETest : AbstractIntegrationSupport({

	describe("GET /popups/v1 — 1회성 팝업 제거") {

		context("1회성 환불 팝업과 일반 팝업이 함께 노출 중이면") {
			it("첫 조회엔 둘 다 내려오고, 두 번째 조회엔 환불 팝업만 사라진다 (200)") {
				val userId = 8001L
				val now: LocalDateTime = LocalDateTime.now()
				IntegrationUtil.persist(
					PopupEntity(
						title = "환불",
						displayOrder = 1,
						popUpType = PopupType.MATCH_FAILED_REFUND,
						userId = userId,
						exposedFrom = now.minusDays(1),
						exposedTo = now.plusDays(1),
					),
				)
				IntegrationUtil.persist(
					PopupEntity(
						title = "공지",
						displayOrder = 2,
						popUpType = PopupType.NORMAL,
						userId = userId,
						exposedFrom = now.minusDays(1),
						exposedTo = now.plusDays(1),
					),
				)

				// 1st: 환불 + 공지 모두 노출
				get("/popups/v1") {
					bearer(accessTokenFor(userId))
				} expect {
					status(200)
					body("data.size()", 2)
					body("data[0].popUpType", PopupType.MATCH_FAILED_REFUND.name)
					body("data[1].popUpType", PopupType.NORMAL.name)
				}

				// 2nd: 1회성 환불 팝업은 제거되고 공지만 남는다
				get("/popups/v1") {
					bearer(accessTokenFor(userId))
				} expect {
					status(200)
					body("data.size()", 1)
					body("data[0].popUpType", PopupType.NORMAL.name)
				}
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QPopupEntity.popupEntity)
	}
})
