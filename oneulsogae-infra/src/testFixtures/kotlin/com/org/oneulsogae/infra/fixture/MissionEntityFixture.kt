package com.org.oneulsogae.infra.fixture

import com.org.oneulsogae.common.mission.MissionType
import com.org.oneulsogae.infra.mission.command.entity.MissionEntity

/**
 * [MissionEntity] 테스트 픽스처. 기본은 첫 미션(자기소개 100자 → 50코인, 활성)이다.
 */
object MissionEntityFixture {

	fun create(
		type: MissionType = MissionType.WRITE_INTRODUCTION,
		rewardCoin: Int = 50,
		title: String = "자기소개 작성",
		description: String? = "자기소개를 100자 이상 작성하고 50코인을 받으세요",
		active: Boolean = true,
		displayOrder: Int = 0,
	): MissionEntity =
		MissionEntity(
			type = type,
			rewardCoin = rewardCoin,
			title = title,
			description = description,
			active = active,
			displayOrder = displayOrder,
		)
}
