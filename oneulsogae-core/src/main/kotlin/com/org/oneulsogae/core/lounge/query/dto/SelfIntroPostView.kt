package com.org.oneulsogae.core.lounge.query.dto

import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.core.common.time.ageAt
import java.time.LocalDate

/**
 * 라운지 셀소 목록 한 건(read model). 그리드 타일에 필요한 값만 담는다.
 * dao는 [imageKey]까지 채우고 [imageUrl]은 null로 둔다. 서비스가 presign 결과로 [imageUrl]을 채운다.
 * `author*`는 작성자 프로필(user_details)에서 조인해 온 표시용 값이며, 프로필이 없으면 null이다.
 * dao는 [authorBirthday]까지 채우고, 서비스가 [authorAge](만 나이)를 채운다.
 */
data class SelfIntroPostView(
	val postId: Long,
	val authorNickname: String?,
	val likeCount: Int,
	val imageKey: String?,
	val authorGender: Gender?,
	/** 작성자 생년월일. 응답에는 노출하지 않고 [authorAge] 계산에만 쓴다. */
	val authorBirthday: LocalDate?,
	val authorProfileImageCode: String?,
	val authorJob: String?,
	val authorCompanyName: String?,
	/** 작성자 활동지역 표시 문자열(시/도 시/군/구). 지역 미설정이면 null. */
	val authorActivityArea: String?,
	val imageUrl: String? = null,
	/** 작성자 만 나이. 서비스가 [authorBirthday]와 기준일로 채운다. (생년월일이 없으면 null) */
	val authorAge: Int? = null,
) {
	/** dao 투영용 생성자. imageUrl·나이는 서비스가 채운다. */
	constructor(
		postId: Long,
		authorNickname: String?,
		likeCount: Int,
		imageKey: String?,
		authorGender: Gender?,
		authorBirthday: LocalDate?,
		authorProfileImageCode: String?,
		authorJob: String?,
		authorCompanyName: String?,
		authorActivityArea: String?,
	) : this(
		postId, authorNickname, likeCount, imageKey,
		authorGender, authorBirthday, authorProfileImageCode, authorJob, authorCompanyName, authorActivityArea, null, null,
	)

	/** 기준일([today])로 작성자 만 나이를 채운 항목을 만든다. */
	fun withAge(today: LocalDate): SelfIntroPostView =
		copy(authorAge = authorBirthday?.ageAt(today))
}
