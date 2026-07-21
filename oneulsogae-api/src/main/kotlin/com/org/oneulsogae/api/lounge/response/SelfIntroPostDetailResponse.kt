package com.org.oneulsogae.api.lounge.response

import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.core.lounge.query.dto.SelfIntroPostDetailView

/**
 * 라운지 셀소 상세 응답.
 * 성별·나이·키·지역·직업·회사명·학교명은 작성자 프로필에서 온 표시용 값이다. (생년월일은 노출하지 않고 만 나이만 내려준다)
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
	/** 회사명. 회사 인증을 하지 않았으면 null. */
	val companyName: String?,
	/** 학교명. 학교 인증을 하지 않았으면 null. */
	val universityName: String?,
	val longDistance: String,
	val desiredAge: String,
	val mbti: String,
	val marriageThought: String,
	val preferredPartner: String,
	val charmPoint: String,
	val freeWord: String,
	val imageUrls: List<String>,
	/** 이 작성자에게 대화를 신청할 때 드는 코인 수. 글마다 다르지 않은 전역 정책값이며, 신청 전 비용 안내에 쓴다. */
	val chatRequestCoinAmount: Int,
	/** 조회한 사용자가 이 글에 이미 대화를 신청했는지 여부. true면 신청 버튼을 "신청함"으로 바꾼다. */
	val chatRequestedByMe: Boolean,
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
				companyName = view.companyName,
				universityName = view.universityName,
				longDistance = view.longDistance,
				desiredAge = view.desiredAge,
				mbti = view.mbti,
				marriageThought = view.marriageThought,
				preferredPartner = view.preferredPartner,
				charmPoint = view.charmPoint,
				freeWord = view.freeWord,
				imageUrls = view.imageUrls,
				chatRequestCoinAmount = view.chatRequestCoinAmount,
				chatRequestedByMe = view.chatRequestedByMe,
			)
	}
}
