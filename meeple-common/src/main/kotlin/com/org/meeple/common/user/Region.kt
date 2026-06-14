package com.org.meeple.common.user

/**
 * 활동지역(시도) → 권역 코드 매핑.
 *
 * 온보딩 시 입력받은 활동지역 문자열에서 시도명을 식별해 권역 코드(1~5)를 산출한다.
 * - 1: 서울, 경기, 인천, 강원
 * - 2: 부산, 대구, 경북, 경남, 울산
 * - 3: 대전, 충남, 충북, 세종
 * - 4: 제주
 * - 5: 광주, 전남, 전북
 */
enum class Region(
	/** 활동지역 문자열에서 시도를 식별할 때 사용하는 시도명. */
	val regionName: String,
	/** 시도가 속한 권역 코드(1~5). */
	val areaCode: Int,
) {

	SEOUL("서울", 1),
	GYEONGGI("경기", 1),
	INCHEON("인천", 1),
	GANGWON("강원", 1),

	BUSAN("부산", 2),
	DAEGU("대구", 2),
	GYEONGBUK("경북", 2),
	GYEONGNAM("경남", 2),
	ULSAN("울산", 2),

	DAEJEON("대전", 3),
	CHUNGNAM("충남", 3),
	CHUNGBUK("충북", 3),
	SEJONG("세종", 3),

	JEJU("제주", 4),

	GWANGJU("광주", 5),
	JEONNAM("전남", 5),
	JEONBUK("전북", 5),
	;

	companion object {

		/**
		 * 활동지역 문자열에서 시도명을 찾아 권역 코드(1~5)를 반환한다.
		 * 매칭되는 시도가 없거나 입력이 비어 있으면 null을 반환한다.
		 */
		fun resolveAreaCode(activityArea: String?): Int? {
			val normalized: String = activityArea?.trim().orEmpty()
			if (normalized.isEmpty()) return null
			return entries.firstOrNull { region: Region -> normalized.contains(region.regionName) }?.areaCode
		}
	}
}
