package com.org.meeple.admin.common.response

import com.org.meeple.admin.common.error.AdminErrorResponse

/**
 * 어드민 API 응답 봉투. core의 ApiResponse와 동일한 JSON 구조(success/data/error)를 갖는다.
 * (admin은 core에 의존하지 않으므로 봉투를 재사용하지 않고 자체 정의한다. 필드는 core와 일치시켜 응답 계약을 보존한다.
 *  어드민 예외 핸들러가 실패 응답을 만드는 경로만 필요하므로 error 팩터리만 둔다.)
 */
data class AdminApiResponse<out T>(
	/** 요청 처리 성공 여부. */
	val success: Boolean,

	/** 성공 시 응답 데이터. 실패하면 null. */
	val data: T?,

	/** 실패 시 에러 정보. 성공이면 null. */
	val error: AdminErrorResponse?,
) {

	companion object {

		/** 에러 정보를 담은 실패 응답. */
		fun error(error: AdminErrorResponse): AdminApiResponse<Nothing> =
			AdminApiResponse(success = false, data = null, error = error)
	}
}
