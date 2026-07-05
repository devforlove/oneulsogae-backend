package com.org.meeple.admin.companyverification.query.service

import com.org.meeple.admin.common.error.AdminErrorCode
import com.org.meeple.admin.common.error.AdminException
import com.org.meeple.admin.companyverification.query.dao.GetAdminCompanyVerificationDao
import com.org.meeple.admin.companyverification.query.dto.AdminCompanyVerificationDetailView
import com.org.meeple.admin.companyverification.query.dto.AdminCompanyVerificationPage
import com.org.meeple.admin.companyverification.query.dto.AdminCompanyVerificationView
import com.org.meeple.admin.companyverification.query.dto.AdminCompanyVerificationViews
import com.org.meeple.admin.companyverification.query.service.port.`in`.GetAdminCompanyVerificationsUseCase
import com.org.meeple.admin.companyverification.query.service.port.out.CompanyVerificationImageUrlPort
import com.org.meeple.common.user.CompanyImageVerificationStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [GetAdminCompanyVerificationsUseCase] 구현. (조회 전용)
 * 회사 이미지 인증을 최신순 페이징 조회한 뒤, 각 행의 imageKey를 presigned 열람 URL로 변환해 반환한다.
 */
@Service
@Transactional(readOnly = true)
class GetAdminCompanyVerificationsService(
	private val getAdminCompanyVerificationDao: GetAdminCompanyVerificationDao,
	private val companyVerificationImageUrlPort: CompanyVerificationImageUrlPort,
) : GetAdminCompanyVerificationsUseCase {

	override fun getVerifications(
		page: Int,
		size: Int,
		status: CompanyImageVerificationStatus?,
	): AdminCompanyVerificationPage {
		val pageNumber: Int = page.coerceAtLeast(0)
		val pageSize: Int = size.coerceIn(1, MAX_PAGE_SIZE)
		val offset: Long = pageNumber.toLong() * pageSize

		val rows: AdminCompanyVerificationViews =
			getAdminCompanyVerificationDao.findPage(offset, pageSize, status)
		val withUrls: List<AdminCompanyVerificationView> = rows.values.map { view: AdminCompanyVerificationView ->
			view.copy(imageUrl = companyVerificationImageUrlPort.presignedGetUrl(view.imageKey))
		}

		return AdminCompanyVerificationPage(
			content = AdminCompanyVerificationViews(withUrls),
			page = pageNumber,
			size = pageSize,
			totalElements = getAdminCompanyVerificationDao.count(status),
		)
	}

	override fun getVerification(id: Long): AdminCompanyVerificationDetailView {
		val view: AdminCompanyVerificationDetailView = getAdminCompanyVerificationDao.findDetailById(id)
			?: throw AdminException(
				AdminErrorCode.COMPANY_IMAGE_VERIFICATION_NOT_FOUND,
				"직장 인증을 찾을 수 없습니다: $id",
			)
		return view.copy(imageUrl = companyVerificationImageUrlPort.presignedGetUrl(view.imageKey))
	}

	companion object {
		private const val MAX_PAGE_SIZE: Int = 100
	}
}
