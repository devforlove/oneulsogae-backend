package com.org.oneulsogae.core.user.query.service.port.`in`

/**
 * 회사 인증(직장 인증) 완료 여부를 확인하는 인포트(유스케이스).
 * 인증이 끝나면 프로필(user_details)의 회사명이 채워지므로, 회사명 보유 여부를 인증 완료 신호로 본다.
 * (이메일 인증·서류 이미지 심사 어느 경로든 승인 시 회사명이 채워진다)
 *
 * 회사 인증을 마친 사용자만 소개·미팅·라운지 기능을 쓸 수 있어, 조회 경로는 [isCompanyVerified]로 화면 분기용
 * 플래그를 얻고 명령 경로는 [validateCompanyVerified]로 미인증 요청을 막는다.
 */
interface CheckCompanyVerifiedUseCase {

	/** 회사 인증을 마쳤는지 여부. 프로필이 없으면 false. (목록 응답의 화면 분기 플래그용) */
	fun isCompanyVerified(userId: Long): Boolean

	/** 회사 인증을 마치지 않았으면 [com.org.oneulsogae.core.user.UserErrorCode.COMPANY_NOT_VERIFIED]를 던진다. (명령 경로 차단용) */
	fun validateCompanyVerified(userId: Long)
}
