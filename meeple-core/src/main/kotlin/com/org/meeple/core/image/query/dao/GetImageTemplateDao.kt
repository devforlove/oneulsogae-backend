package com.org.meeple.core.image.query.dao

import com.org.meeple.core.image.query.dto.ImageTemplateView

/**
 * 이미지 템플릿 조회 dao(query out-port). QueryDSL 구현은 infra가 담당한다.
 * [code]로 단건 조회한다. (없으면 null)
 */
interface GetImageTemplateDao {

	fun findByCode(code: String): ImageTemplateView?
}
