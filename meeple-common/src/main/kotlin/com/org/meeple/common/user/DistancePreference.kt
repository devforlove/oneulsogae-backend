package com.org.meeple.common.user

/**
 * 이상형 지역 거리 선호. 매칭 후보 탐색의 근접 지역 순회 깊이로 매핑된다.
 * null = "상관없음"(제한 없음)이므로 별도 값을 두지 않는다.
 */
enum class DistancePreference(val description: String) {

	/** 같은 활동지역(regionId 일치)만. */
	SAME_REGION("같은 지역만"),

	/** 같은 + 인접 지역까지. */
	ADJACENT_REGION("인접 지역까지"),
}
