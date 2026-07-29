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
	/** 상세 조회수. (이번 조회 반영분 포함) */
	val viewCount: Int,
	/** 조회한 사용자가 이 글에 좋아요를 눌렀는지 여부. (비로그인이면 false) */
	val likedByMe: Boolean,
	val gender: Gender?,
	val age: Int?,
	val height: Int?,
	val activityArea: String?,
	val job: String?,
	/** 회사명. 회사 인증을 하지 않았으면 null. */
	val companyName: String?,
	/** 학교명. 학교 인증을 하지 않았으면 null. */
	val universityName: String?,
	/** 등록 시점 프로필의 MBTI 스냅샷. 프로필에 MBTI가 없던 글은 null. */
	val mbti: String?,
	/** 관심사. (필드 도입 전 구버전 글은 빈 문자열) */
	val interests: String,
	/** 이상형/연애관. (필드 도입 전 구버전 글은 빈 문자열) */
	val idealType: String,
	/** 나의 매력 어필. */
	val charmPoint: String,
	val imageUrls: List<String>,
	/** 이 작성자에게 대화를 신청할 때 드는 코인 수. 조회한 사용자(뷰어) 성별로 갈리며, 비로그인이면 null이다. */
	val chatRequestCoinAmount: Int?,
	/** 조회한 사용자가 이 글에 이미 대화를 신청했는지 여부. true면 신청 버튼을 "신청함"으로 바꾼다. */
	val chatRequestedByMe: Boolean,
	/** 조회한 사용자가 회사 인증을 마쳤는지 여부. 비로그인이면 false. false면 신청 시도 시 회사 인증 안내로 분기한다. */
	val companyVerified: Boolean,
) {
	companion object {

		fun of(view: SelfIntroPostDetailView): SelfIntroPostDetailResponse =
			SelfIntroPostDetailResponse(
				postId = view.postId,
				authorNickname = view.authorNickname,
				likeCount = view.likeCount,
				viewCount = view.viewCount,
				likedByMe = view.likedByMe,
				gender = view.gender,
				age = view.age,
				height = view.height,
				activityArea = view.activityArea,
				job = view.job,
				companyName = view.companyName,
				universityName = view.universityName,
				mbti = view.mbti,
				interests = view.interests,
				idealType = view.idealType,
				charmPoint = view.charmPoint,
				imageUrls = view.imageUrls,
				chatRequestCoinAmount = view.chatRequestCoinAmount,
				chatRequestedByMe = view.chatRequestedByMe,
				companyVerified = view.companyVerified,
			)
	}
}
