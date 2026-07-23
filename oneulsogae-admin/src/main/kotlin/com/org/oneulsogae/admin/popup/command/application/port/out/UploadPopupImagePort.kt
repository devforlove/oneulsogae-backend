package com.org.oneulsogae.admin.popup.command.application.port.out

/** 팝업 이미지 파일 업로드 out-port. 저장 후 오브젝트 key를 반환한다. infra(S3) 어댑터가 구현한다. */
fun interface UploadPopupImagePort {

	fun upload(key: String, content: ByteArray, contentType: String): String
}
