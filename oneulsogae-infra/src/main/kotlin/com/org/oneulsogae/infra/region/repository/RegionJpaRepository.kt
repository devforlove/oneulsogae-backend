package com.org.oneulsogae.infra.region.repository

import com.org.oneulsogae.infra.region.entity.RegionEntity
import org.springframework.data.jpa.repository.JpaRepository

/**
 * regions 테이블 Spring Data JPA 리포지토리.
 * 시/도 + 시/군/구로 지역(좌표 포함)을 단건 조회한다. (근거리 소개 시 좌표 lookup에 쓴다)
 */
interface RegionJpaRepository : JpaRepository<RegionEntity, Long> {

	/** 시/도 + 시/군/구로 단건 조회한다. (활동지역 → 좌표 lookup) */
	fun findBySidoAndSigungu(sido: String, sigungu: String): RegionEntity?
}
