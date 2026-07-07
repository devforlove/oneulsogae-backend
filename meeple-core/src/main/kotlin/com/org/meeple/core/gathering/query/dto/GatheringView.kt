package com.org.meeple.core.gathering.query.dto

import com.org.meeple.common.gathering.GatheringType

/**
 * 유저용 모임 목록 한 건(read model).
 * dao는 [imageKey]까지 채우고 [imageUrl]은 null로 둔다. 서비스가 presign 결과로 [imageUrl]을 채운다(이미지 없으면 null).
 */
data class GatheringView(
	val id: Long,
	val type: GatheringType,
	val title: String,
	val imageKey: String?,
	val imageUrl: String? = null,
	val region: String,
) {
	/** dao 투영용 생성자. imageUrl은 서비스가 presign으로 채운다. */
	constructor(
		id: Long,
		type: GatheringType,
		title: String,
		imageKey: String?,
		region: String,
	) : this(id, type, title, imageKey, null, region)
}
