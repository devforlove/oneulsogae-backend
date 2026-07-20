package com.org.oneulsogae.admin.gathering.command.domain

/**
 * 모임 소개(Markdown)에 삽입하는 이미지 키 규칙. 대표 이미지([GatheringImage], 프리픽스 "gatherings")와는
 * 별도 프리픽스를 쓴다. 공개 프록시가 이 프리픽스로 시작하는 key만 서빙하도록 화이트리스트에 쓰인다.
 */
object GatheringDescriptionImage {

	/** 소개 이미지 오브젝트 키 프리픽스. */
	const val KEY_PREFIX: String = "gathering-descriptions"

	/** [key]가 이 프리픽스로 시작하고 경로 조작(`..`, `\`)이 없는 유효한 소개 이미지 키인지 검사한다. */
	fun isValidKey(key: String): Boolean =
		key.startsWith("$KEY_PREFIX/") && !key.contains("..") && !key.contains('\\')
}
