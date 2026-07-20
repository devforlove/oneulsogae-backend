package com.org.oneulsogae.core.image.query.service

import com.org.oneulsogae.core.image.query.dao.GetImageTemplateDao
import com.org.oneulsogae.core.image.query.dto.ImageTemplateView
import com.org.oneulsogae.core.image.query.service.port.`in`.GetImageTemplateUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [GetImageTemplateUseCase] 구현. (조회 전용)
 * [code]로 이미지 템플릿을 단건 조회한다. 없으면 null을 반환해 소비처가 부재를 처리하게 한다.
 */
@Service
@Transactional(readOnly = true)
class GetImageTemplateService(
	private val getImageTemplateDao: GetImageTemplateDao,
) : GetImageTemplateUseCase {

	override fun getByCode(code: String): ImageTemplateView? =
		getImageTemplateDao.findByCode(code)
}
