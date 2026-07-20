package com.org.oneulsogae.infra.region

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** 지역 대표 좌표 한 점. (거리 계산 입력) */
data class RegionPoint(
	val regionId: Long,
	val latitude: Double,
	val longitude: Double,
)

/**
 * 지역 좌표로부터 "각 지역에서 가까운 지역 순서"를 계산하는 순수 도메인 로직.
 * 유저는 개인 좌표가 없고 regionId만 가지므로, 매칭의 "가까움"은 지역 대표 좌표 간 거리로 수렴한다.
 * 한 번의 조회는 한 지역의 정렬만 필요하므로(O(n log n)), 전체 쌍을 미리 펼치지 않고 호출 시 정렬한다.
 * 프레임워크/인프라에 의존하지 않는다. (캐싱·DB 적재는 [RegionProximityRegistry]가 맡는다)
 */
class RegionProximity private constructor(
	private val points: List<RegionPoint>,
) {

	/**
	 * [regionId]에서 가까운 순으로 정렬한 전체 regionId. 자기 지역이 거리 0으로 맨 앞에 온다.
	 * 거리가 같으면 regionId 오름차순으로 안정 정렬한다. 좌표를 모르는 지역이면 빈 리스트.
	 */
	fun nearbyRegionIds(regionId: Long): List<Long> {
		val origin: RegionPoint = points.firstOrNull { point: RegionPoint -> point.regionId == regionId }
			?: return emptyList()
		return points
			.sortedWith(
				compareBy(
					{ point: RegionPoint -> haversineKm(origin, point) },
					{ point: RegionPoint -> point.regionId },
				),
			)
			.map { point: RegionPoint -> point.regionId }
	}

	companion object {
		/** 좌표가 하나도 없는 빈 근접표. (적재 전 초기 상태) */
		val EMPTY: RegionProximity = RegionProximity(emptyList())

		fun from(points: List<RegionPoint>): RegionProximity =
			RegionProximity(points)

		private const val EARTH_RADIUS_KM: Double = 6371.0

		/** 두 좌표 간 대권 거리(km). 정렬용이라 절대값 정확도보다 단조성만 보장하면 충분하다. */
		private fun haversineKm(a: RegionPoint, b: RegionPoint): Double {
			val dLat: Double = Math.toRadians(b.latitude - a.latitude)
			val dLon: Double = Math.toRadians(b.longitude - a.longitude)
			val lat1: Double = Math.toRadians(a.latitude)
			val lat2: Double = Math.toRadians(b.latitude)
			val h: Double = sin(dLat / 2) * sin(dLat / 2) +
				cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
			return EARTH_RADIUS_KM * 2 * atan2(sqrt(h), sqrt(1 - h))
		}
	}
}
