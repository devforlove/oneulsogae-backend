package com.org.oneulsogae.admin.universityverification.query.service

import com.org.oneulsogae.admin.common.error.AdminErrorCode
import com.org.oneulsogae.admin.common.error.AdminException
import com.org.oneulsogae.admin.universityverification.query.dao.GetAdminUniversityVerificationDao
import com.org.oneulsogae.admin.universityverification.query.dto.AdminUniversityVerificationDetailView
import com.org.oneulsogae.admin.universityverification.query.dto.AdminUniversityVerificationPage
import com.org.oneulsogae.admin.universityverification.query.dto.AdminUniversityVerificationView
import com.org.oneulsogae.admin.universityverification.query.dto.AdminUniversityVerificationViews
import com.org.oneulsogae.admin.universityverification.query.service.port.`in`.GetAdminUniversityVerificationsUseCase
import com.org.oneulsogae.admin.universityverification.query.service.port.out.UniversityVerificationImageUrlPort
import com.org.oneulsogae.common.user.UniversityImageVerificationStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [GetAdminUniversityVerificationsUseCase] 구현. (조회 전용)
 * 학교 이미지 인증을 최신순 페이징 조회한 뒤, 각 행의 imageKey를 presigned 열람 URL로 변환해 반환한다.
 */
@Service
@Transactional(readOnly = true)
class GetAdminUniversityVerificationsService(
	private val getAdminUniversityVerificationDao: GetAdminUniversityVerificationDao,
	private val universityVerificationImageUrlPort: UniversityVerificationImageUrlPort,
) : GetAdminUniversityVerificationsUseCase {

	override fun getVerifications(
		page: Int,
		size: Int,
		status: UniversityImageVerificationStatus?,
	): AdminUniversityVerificationPage {
		val pageNumber: Int = page.coerceAtLeast(0)
		val pageSize: Int = size.coerceIn(1, MAX_PAGE_SIZE)
		val offset: Long = pageNumber.toLong() * pageSize

		val rows: AdminUniversityVerificationViews =
			getAdminUniversityVerificationDao.findPage(offset, pageSize, status)
		val withUrls: List<AdminUniversityVerificationView> = rows.values.map { view: AdminUniversityVerificationView ->
			view.copy(imageUrl = universityVerificationImageUrlPort.presignedGetUrl(view.imageKey))
		}

		return AdminUniversityVerificationPage(
			content = AdminUniversityVerificationViews(withUrls),
			page = pageNumber,
			size = pageSize,
			totalElements = getAdminUniversityVerificationDao.count(status),
		)
	}

	override fun getVerification(id: Long): AdminUniversityVerificationDetailView {
		val view: AdminUniversityVerificationDetailView = getAdminUniversityVerificationDao.findDetailById(id)
			?: throw AdminException(
				AdminErrorCode.UNIVERSITY_IMAGE_VERIFICATION_NOT_FOUND,
				"학교 인증을 찾을 수 없습니다: $id",
			)
		return view.copy(imageUrl = universityVerificationImageUrlPort.presignedGetUrl(view.imageKey))
	}

	companion object {
		private const val MAX_PAGE_SIZE: Int = 100
	}
}
