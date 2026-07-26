package com.org.oneulsogae.core.mission.query.dto

import com.org.oneulsogae.common.mission.MissionType

/**
 * 미션 정의(read model). 보상 코인·문구·정렬 순서를 담는다.
 * 자격 판정 로직은 담지 않는다(유형별 평가자가 코드로 판정).
 * 영속성은 [com.org.oneulsogae.infra.mission.command.entity.MissionEntity]가 담당한다.
 */
data class Mission(
	val id: Long,
	val type: MissionType,
	val rewardCoin: Int,
	val title: String,
	val description: String?,
	val displayOrder: Int,
)
