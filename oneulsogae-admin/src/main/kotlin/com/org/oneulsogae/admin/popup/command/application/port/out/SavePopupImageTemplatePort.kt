package com.org.oneulsogae.admin.popup.command.application.port.out

/**
 * 팝업 이미지 템플릿(image_templates) 저장 out-port.
 * [code]가 이미 있으면 이미지(키·치수)만 교체하고, 없으면 새 행을 만든다.
 * [imageKey]는 스토리지 오브젝트 키 — 어댑터가 공개 프록시(/images/{key}) 절대 URL로 바꿔 저장한다.
 */
fun interface SavePopupImageTemplatePort {

	fun upsert(code: String, imageKey: String, imageWidth: Int, imageHeight: Int)
}
