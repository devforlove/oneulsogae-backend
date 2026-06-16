package com.org.meeple.core.user.command.application.port.`in`.command

import com.org.meeple.common.user.BodyType
import com.org.meeple.common.user.DrinkingStatus
import com.org.meeple.common.user.MaritalStatus
import com.org.meeple.common.user.Religion
import com.org.meeple.common.user.SmokingStatus

/**
 * 프로필 수정 명령. 사용자가 가입 이후 자유롭게 바꿀 수 있는 필드만 담는다.
 * 나이/성별/키/휴대폰번호/회사이메일은 프로필 수정으로 변경할 수 없어 제외한다.
 * 전체 교체(PUT)라 모든 편집 필드가 필수이며, 진입점(요청 DTO의 Bean Validation)에서 null·빈 값을 거른 뒤 채워진다.
 * 따라서 이 명령의 필드는 모두 non-null이다. (단, 활동지역의 권역 인식은 도메인에서 별도로 검증한다)
 */
data class UpdateProfileCommand(
	val nickname: String,
	val profileImageCode: String,
	val job: String,
	val activityArea: String,
	val introduction: String,
	val traits: List<String>,
	val interests: List<String>,
	val maritalStatus: MaritalStatus,
	val smokingStatus: SmokingStatus,
	val religion: Religion,
	val drinkingStatus: DrinkingStatus,
	val bodyType: BodyType,
)
