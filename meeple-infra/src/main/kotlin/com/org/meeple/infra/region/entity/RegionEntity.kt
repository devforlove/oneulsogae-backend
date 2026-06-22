package com.org.meeple.infra.region.entity

import com.org.meeple.infra.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.SQLRestriction

/**
 * regions 테이블 영속성 엔티티. 활동지역(시/도 + 시/군/구)별 위치 정보를 관리하는 참조 데이터다.
 * 좌표([longitude]/[latitude])로 지역 간 거리를 계산해 가까운 사용자·팀을 소개하는 데 쓴다.
 * 시/도([sido]) + 시/군/구([sigungu]) 조합이 지역을 유일하게 식별하는 조회 키다. (예: "서울특별시" + "강남구")
 * 도메인 로직을 두지 않고 상태만 보관한다.
 */
@Entity
@SQLRestriction("deleted_at is null")
@Table(
	name = "regions",
	uniqueConstraints = [
		// 시/도 + 시/군/구 조합이 지역 조회 키이므로 유니크.
		UniqueConstraint(name = "ux_sido_sigungu", columnNames = ["sido", "sigungu"]),
	],
)
class RegionEntity(
	/** 시/도. 예: "서울특별시". */
	@Column(name = "sido", nullable = false, length = 50)
	var sido: String,

	/** 시/군/구. 예: "강남구". */
	@Column(name = "sigungu", nullable = false, length = 50)
	var sigungu: String,

	/** 경도. (거리 계산용) */
	@Column(name = "longitude", nullable = false)
	var longitude: Double,

	/** 위도. (거리 계산용) */
	@Column(name = "latitude", nullable = false)
	var latitude: Double,

	/** 노출 정렬 순서. 목록 조회는 이 값 오름차순으로 정렬한다. (컬럼명은 SQL 예약어 회피를 위해 display_order) */
	@Column(name = "display_order", nullable = false)
	var order: Int,
) : BaseEntity()
