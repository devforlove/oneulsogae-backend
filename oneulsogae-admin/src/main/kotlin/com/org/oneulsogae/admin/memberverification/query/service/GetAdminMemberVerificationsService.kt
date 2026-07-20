package com.org.oneulsogae.admin.memberverification.query.service

import com.org.oneulsogae.admin.common.error.AdminErrorCode
import com.org.oneulsogae.admin.common.error.AdminException
import com.org.oneulsogae.admin.memberverification.query.dao.GetAdminMemberVerificationDao
import com.org.oneulsogae.admin.memberverification.query.dto.AdminMemberVerificationDetailView
import com.org.oneulsogae.admin.memberverification.query.dto.AdminMemberVerificationPage
import com.org.oneulsogae.admin.memberverification.query.dto.AdminMemberVerificationViews
import com.org.oneulsogae.admin.memberverification.query.service.port.`in`.GetAdminMemberVerificationsUseCase
import com.org.oneulsogae.admin.memberverification.query.service.port.out.MemberVerificationImageUrlPort
import com.org.oneulsogae.common.gathering.MemberVerificationStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [GetAdminMemberVerificationsUseCase] 구현. (조회 전용)
 * 멤버 인증을 최신순 페이징 조회하고, 상세는 사진 3종(얼굴·신분증·서류)의 오브젝트 키를 presigned 열람 URL로 변환해 반환한다.
 * (목록에는 이미지가 없어 presign을 하지 않는다)
 */
@Service
@Transactional(readOnly = true)
class GetAdminMemberVerificationsService(
	private val getAdminMemberVerificationDao: GetAdminMemberVerificationDao,
	private val memberVerificationImageUrlPort: MemberVerificationImageUrlPort,
) : GetAdminMemberVerificationsUseCase {

	override fun getVerifications(
		page: Int,
		size: Int,
		status: MemberVerificationStatus?,
	): AdminMemberVerificationPage {
		val pageNumber: Int = page.coerceAtLeast(0)
		val pageSize: Int = size.coerceIn(1, MAX_PAGE_SIZE)
		val offset: Long = pageNumber.toLong() * pageSize

		val rows: AdminMemberVerificationViews = getAdminMemberVerificationDao.findPage(offset, pageSize, status)

		return AdminMemberVerificationPage(
			content = rows,
			page = pageNumber,
			size = pageSize,
			totalElements = getAdminMemberVerificationDao.count(status),
		)
	}

	override fun getVerification(id: Long): AdminMemberVerificationDetailView {
		val view: AdminMemberVerificationDetailView = getAdminMemberVerificationDao.findDetailById(id)
			?: throw AdminException(
				AdminErrorCode.MEMBER_VERIFICATION_NOT_FOUND,
				"멤버 인증을 찾을 수 없습니다: $id",
			)
		return view.copy(
			faceImageUrl = memberVerificationImageUrlPort.presignedGetUrl(view.faceImageKey),
			idCardImageUrl = memberVerificationImageUrlPort.presignedGetUrl(view.idCardImageKey),
			documentImageUrl = memberVerificationImageUrlPort.presignedGetUrl(view.documentImageKey),
		)
	}

	companion object {
		private const val MAX_PAGE_SIZE: Int = 100
	}
}
