package com.org.oneulsogae.core.region.query.dto

/**
 * 지역 선택 옵션 read model. 온보딩 등에서 사용자가 활동지역을 고를 때 쓰는 시/도 + 시/군/구 쌍이다.
 * (좌표는 선택 UI에 불필요해 담지 않는다 — 거리 계산용 좌표는 별도 조회에서 쓴다)
 */
data class RegionView(
	val id: Long,
	val sido: String,
	val sigungu: String,
)
