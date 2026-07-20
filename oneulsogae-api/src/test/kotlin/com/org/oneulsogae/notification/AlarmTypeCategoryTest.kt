package com.org.oneulsogae.notification

import com.org.oneulsogae.common.alarm.AlarmType
import com.org.oneulsogae.common.notification.NotificationCategory
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class AlarmTypeCategoryTest : DescribeSpec({

	describe("AlarmType.category") {

		context("ONE_TO_ONE_* 4종은") {
			it("ONE_TO_ONE 카테고리로 매핑된다 (NO_MATCH_TODAY 포함)") {
				AlarmType.ONE_TO_ONE_INTEREST_RECEIVED.category() shouldBe NotificationCategory.ONE_TO_ONE
				AlarmType.ONE_TO_ONE_MATCHED.category() shouldBe NotificationCategory.ONE_TO_ONE
				AlarmType.ONE_TO_ONE_MATCH_ENDED.category() shouldBe NotificationCategory.ONE_TO_ONE
				AlarmType.ONE_TO_ONE_NO_MATCH_TODAY.category() shouldBe NotificationCategory.ONE_TO_ONE
			}
		}

		context("MANY_TO_MANY_* 4종은") {
			it("MEETING 카테고리로 매핑된다 (NO_MATCH_TODAY 포함)") {
				AlarmType.MANY_TO_MANY_INTEREST_RECEIVED.category() shouldBe NotificationCategory.MEETING
				AlarmType.MANY_TO_MANY_MATCHED.category() shouldBe NotificationCategory.MEETING
				AlarmType.MANY_TO_MANY_MATCH_ENDED.category() shouldBe NotificationCategory.MEETING
				AlarmType.MANY_TO_MANY_NO_MATCH_TODAY.category() shouldBe NotificationCategory.MEETING
			}
		}

		context("TEAM_* 5종은") {
			it("TEAM 카테고리로 매핑된다") {
				AlarmType.TEAM_INVITATION_RECEIVED.category() shouldBe NotificationCategory.TEAM
				AlarmType.TEAM_INVITATION_DECLINED.category() shouldBe NotificationCategory.TEAM
				AlarmType.TEAM_INVITATION_CANCELED.category() shouldBe NotificationCategory.TEAM
				AlarmType.TEAM_INVITATION_ACCEPTED.category() shouldBe NotificationCategory.TEAM
				AlarmType.TEAM_DISBANDED.category() shouldBe NotificationCategory.TEAM
			}
		}

		context("COIN_* 은") {
			it("COIN 카테고리로 매핑된다 (인앱 전용)") {
				AlarmType.COIN_DAILY_ACQUIRED.category() shouldBe NotificationCategory.COIN
			}
		}
	}
})
