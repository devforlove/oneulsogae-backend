package com.org.oneulsogae.api.lounge.response

import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.core.lounge.query.dto.SelfIntroPostDetailView

/**
 * 라운지 셀소 상세 응답.
 * 성별·나이·키·지역·직업은 작성자 프로필에서 온 표시용 값이다. (생년월일은 노출하지 않고 만 나이만 내려준다)
 * 사진은 비공개 저장이라 열람용 presigned URL 목록으로 내려준다(노출 순서).
 */
data class SelfIntroPostDetailResponse(
	val postId: Long,
	val authorNickname: String?,
	val likeCount: Int,
	val gender: Gender?,
	val age: Int?,
	val height: Int?,
	val activityArea: String?,
	val job: String?,
	val longDistance: String,
	val desiredAge: String,
	val mbti: String,
	val marriageThought: String,
	val preferredPartner: String,
	val charmPoint: String,
	val freeWord: String,
	val imageUrls: List<String>,
) {
	companion object {

		fun of(view: SelfIntroPostDetailView): SelfIntroPostDetailResponse =
			SelfIntroPostDetailResponse(
				postId = view.postId,
				authorNickname = view.authorNickname,
				likeCount = view.likeCount,
				gender = view.gender,
				age = view.age,
				height = view.height,
				activityArea = view.activityArea,
				job = view.job,
				longDistance = view.longDistance,
				desiredAge = view.desiredAge,
				mbti = view.mbti,
				marriageThought = view.marriageThought,
				preferredPartner = view.preferredPartner,
				charmPoint = view.charmPoint,
				freeWord = view.freeWord,
				imageUrls = view.imageUrls,
			)
	}
}
