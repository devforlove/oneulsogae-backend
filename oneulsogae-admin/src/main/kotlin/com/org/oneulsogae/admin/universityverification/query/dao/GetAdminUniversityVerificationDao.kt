package com.org.oneulsogae.admin.universityverification.query.dao

import com.org.oneulsogae.admin.universityverification.query.dto.AdminUniversityVerificationDetailView
import com.org.oneulsogae.admin.universityverification.query.dto.AdminUniversityVerificationViews
import com.org.oneulsogae.common.user.UniversityImageVerificationStatus

/** 어드민 학교 이미지 인증 조회 dao(query out-port). */
interface GetAdminUniversityVerificationDao {

	/** [status](없으면 전체)를 최신순(id desc)으로 [offset]부터 [limit]건 조회한다. */
	fun findPage(offset: Long, limit: Int, status: UniversityImageVerificationStatus?): AdminUniversityVerificationViews

	/** (soft delete 제외) [status](없으면 전체) 조건 전체 개수. (페이징 메타데이터 계산용) */
	fun count(status: UniversityImageVerificationStatus?): Long

	/** 인증 상세를 [id]로 조회한다. 없거나 soft-delete면 null. */
	fun findDetailById(id: Long): AdminUniversityVerificationDetailView?
}
