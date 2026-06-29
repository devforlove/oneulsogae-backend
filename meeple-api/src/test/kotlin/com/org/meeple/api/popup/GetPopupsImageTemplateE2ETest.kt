package com.org.meeple.api.popup

import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.get
import com.org.meeple.common.popup.PopupType
import com.org.meeple.infra.fixture.ImageTemplateEntityFixture
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.image.entity.QImageTemplateEntity
import com.org.meeple.infra.popup.command.entity.PopupEntity
import com.org.meeple.infra.popup.command.entity.QPopupEntity
import org.hamcrest.Matchers.nullValue
import java.time.LocalDateTime

/**
 * `GET /popups/v1`에서 팝업 이미지가 image_templates 코드 참조로 해석되는지 검증하는 E2E.
 * - image_code에 해당하는 템플릿이 있으면 그 url/width/height가 내려온다.
 * - 템플릿이 없는(또는 null) image_code면 이미지 필드가 null로 내려온다. (LEFT JOIN)
 */
class GetPopupsImageTemplateE2ETest : AbstractIntegrationSupport({

	describe("GET /popups/v1") {

		context("팝업의 image_code에 해당하는 image_template이 있으면") {
			it("템플릿의 url/width/height가 join되어 내려오고, 템플릿 없는 코드는 이미지가 null이다 (200)") {
				val userId = 7001L
				IntegrationUtil.persist(
					ImageTemplateEntityFixture.create(
						code = "TEST_POPUP_IMAGE",
						imageUrl = "https://img.test/popup-a.png",
						imageWidth = 300,
						imageHeight = 500,
					),
				)
				val now: LocalDateTime = LocalDateTime.now()
				// 템플릿이 연결된 개인 팝업 (displayOrder 1)
				IntegrationUtil.persist(
					PopupEntity(
						title = "A",
						displayOrder = 1,
						imageCode = "TEST_POPUP_IMAGE",
						popUpType = PopupType.NORMAL,
						userId = userId,
						exposedFrom = now.minusDays(1),
						exposedTo = now.plusDays(1),
					),
				)
				// 템플릿이 없는 코드의 개인 팝업 (displayOrder 2) → 이미지 null
				IntegrationUtil.persist(
					PopupEntity(
						title = "B",
						displayOrder = 2,
						imageCode = "MISSING_CODE",
						popUpType = PopupType.NORMAL,
						userId = userId,
						exposedFrom = now.minusDays(1),
						exposedTo = now.plusDays(1),
					),
				)

				get("/popups/v1") {
					bearer(accessTokenFor(userId))
				} expect {
					status(200)
					body("success", true)
					body("data.size()", 2)
					body("data[0].title", "A")
					body("data[0].imageUrl", "https://img.test/popup-a.png")
					body("data[0].imageWidth", 300)
					body("data[0].imageHeight", 500)
					body("data[1].title", "B")
					body("data[1].imageUrl", nullValue())
					body("data[1].imageWidth", nullValue())
					body("data[1].imageHeight", nullValue())
				}
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QPopupEntity.popupEntity)
		IntegrationUtil.deleteAll(QImageTemplateEntity.imageTemplateEntity)
	}
})
