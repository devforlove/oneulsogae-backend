package com.org.meeple.core.common.response

import com.org.meeple.core.common.error.ErrorCode
import com.org.meeple.core.common.error.ErrorResponse

/**
 * 모든 API 응답을 감싸는 공통 응답 봉투.
 *
 * 성공 시 [success]=true, 결과를 [data]에 담고 [error]는 null이다.
 * 실패 시 [success]=false, [error]에 에러 정보([ErrorResponse])를 담고 [data]는 null이다.
 */
data class ApiResponse<out T>(
	/** 요청 처리 성공 여부. */
	val success: Boolean,

	/** 성공 시 응답 데이터. 실패하거나 본문이 없으면 null. */
	val data: T?,

	/** 실패 시 에러 정보. 성공이면 null. */
	val error: ErrorResponse?,
) {

	companion object {

		/** 데이터를 담은 성공 응답. */
		fun <T> success(data: T): ApiResponse<T> =
			ApiResponse(success = true, data = data, error = null)

		/** 본문이 없는 성공 응답. (생성/삭제 등 반환 데이터가 없는 경우) */
		fun success(): ApiResponse<Unit> =
			ApiResponse(success = true, data = null, error = null)

		/** 에러 정보를 담은 실패 응답. */
		fun error(error: ErrorResponse): ApiResponse<Nothing> =
			ApiResponse(success = false, data = null, error = error)

		/** 에러 코드로부터 실패 응답을 만든다. (메시지를 생략하면 [ErrorCode.message]를 사용) */
		fun error(errorCode: ErrorCode, message: String = errorCode.message): ApiResponse<Nothing> =
			error(ErrorResponse.of(errorCode, message))
	}
}
