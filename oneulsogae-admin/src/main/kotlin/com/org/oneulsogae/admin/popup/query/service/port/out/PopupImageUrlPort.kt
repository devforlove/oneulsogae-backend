package com.org.oneulsogae.admin.popup.query.service.port.out

/** 팝업 이미지 presigned GET URL 발급 out-port. infra(S3 presigner) 어댑터가 구현한다. */
fun interface PopupImageUrlPort {

	fun presignedGetUrl(imageKey: String): String
}
