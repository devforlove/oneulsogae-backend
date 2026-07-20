package com.org.oneulsogae.admin.memberverification.query.dao

import com.org.oneulsogae.admin.memberverification.query.dto.AdminMemberVerificationDetailView
import com.org.oneulsogae.admin.memberverification.query.dto.AdminMemberVerificationViews
import com.org.oneulsogae.common.gathering.MemberVerificationStatus

/** 어드민 멤버 인증 조회 dao(query out-port). */
interface GetAdminMemberVerificationDao {

	/** [status](없으면 전체)를 최신순(id desc)으로 [offset]부터 [limit]건 조회한다. */
	fun findPage(offset: Long, limit: Int, status: MemberVerificationStatus?): AdminMemberVerificationViews

	/** (soft delete 제외) [status](없으면 전체) 조건 전체 개수. (페이징 메타데이터 계산용) */
	fun count(status: MemberVerificationStatus?): Long

	/** 멤버 인증 상세를 [id]로 조회한다. 없거나 soft-delete면 null. */
	fun findDetailById(id: Long): AdminMemberVerificationDetailView?
}
