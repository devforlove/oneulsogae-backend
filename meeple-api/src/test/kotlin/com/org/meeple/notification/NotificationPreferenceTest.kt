package com.org.meeple.notification

import com.org.meeple.common.notification.NotificationCategory
import com.org.meeple.core.notification.command.domain.NotificationPreference
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class NotificationPreferenceTest : DescribeSpec({

	describe("NotificationPreference.allows") {

		context("push 마스터가 꺼져 있으면") {
			it("개별 카테고리가 켜져 있어도 모두 false") {
				val pref = NotificationPreference(
					userId = 1L, push = false,
					oneToOne = true, meeting = true, team = true, message = true, marketing = true,
				)

				NotificationCategory.entries.forEach { category ->
					pref.allows(category) shouldBe false
				}
			}
		}

		context("push가 켜져 있으면") {
			it("해당 카테고리 플래그를 그대로 따른다") {
				val pref = NotificationPreference(
					userId = 1L, push = true,
					oneToOne = true, meeting = false, team = true, message = false, marketing = true,
				)

				pref.allows(NotificationCategory.ONE_TO_ONE) shouldBe true
				pref.allows(NotificationCategory.MEETING) shouldBe false
				pref.allows(NotificationCategory.TEAM) shouldBe true
				pref.allows(NotificationCategory.MESSAGE) shouldBe false
				pref.allows(NotificationCategory.MARKETING) shouldBe true
			}
		}
	}

	describe("NotificationPreference.default") {

		it("프론트 기본값과 일치한다 (marketing만 false)") {
			val pref = NotificationPreference.default(userId = 7L)

			pref.userId shouldBe 7L
			pref.push shouldBe true
			pref.oneToOne shouldBe true
			pref.meeting shouldBe true
			pref.team shouldBe true
			pref.message shouldBe true
			pref.marketing shouldBe false
		}
	}
})
