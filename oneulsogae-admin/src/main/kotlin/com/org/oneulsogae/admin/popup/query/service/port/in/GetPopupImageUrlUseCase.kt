package com.org.oneulsogae.admin.popup.query.service.port.`in`

/** 팝업 이미지 key의 presigned GET URL을 발급하는 유스케이스. 팝업 프리픽스가 아닌 key면 null. (공개 프록시용) */
fun interface GetPopupImageUrlUseCase {

	fun execute(key: String): String?
}
