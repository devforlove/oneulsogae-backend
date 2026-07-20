package com.org.oneulsogae.core.image.query.service.port.`in`

import com.org.oneulsogae.core.image.query.dto.ImageTemplateView

/**
 * 이미지 템플릿을 [code]로 조회하는 유스케이스(in-port).
 * 여러 도메인이 배포 없이 교체 가능한 이미지(url/width/height)를 [code]로 재사용한다. (없으면 null — 소비처가 부재를 허용)
 */
interface GetImageTemplateUseCase {

	fun getByCode(code: String): ImageTemplateView?
}
