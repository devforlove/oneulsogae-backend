package com.org.oneulsogae.api.lounge.response

import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.core.lounge.query.dto.SelfIntroPostPage
import com.org.oneulsogae.core.lounge.query.dto.SelfIntroPostView

/**
 * 라운지 셀소 목록(커서 페이지) 응답.
 * [nextCursor]를 다음 요청의 `cursor`로 그대로 넘기면 이어지는 페이지를 받는다. (다음 페이지가 없으면 null)
 */
data class SelfIntroPostPageResponse(
	val items: List<SelfIntroPostItemResponse>,
	/**
	 * 요청한 사용자가 자기 셀소로 받은 신청 중 아직 수락하지 않은(PENDING) 건수. (내가 쓴 모든 셀소 합산)
	 * "받은 신청" 배지에 쓴다. 수락하면 줄어든다.
	 */
	val receivedPendingChatRequestCount: Int,
	val hasNext: Boolean,
	val nextCursor: Long?,
	/** 요청한 사용자가 회사 인증을 마쳤는지 여부. 미인증이면 프론트엔드가 이용 제한 화면으로 분기한다. */
	val companyVerified: Boolean,
) {
	companion object {

		fun of(page: SelfIntroPostPage): SelfIntroPostPageResponse =
			SelfIntroPostPageResponse(
				items = page.values.map { view: SelfIntroPostView -> SelfIntroPostItemResponse.of(view) },
				receivedPendingChatRequestCount = page.receivedPendingChatRequestCount,
				hasNext = page.hasNext,
				nextCursor = page.nextCursor,
				companyVerified = page.companyVerified,
			)
	}
}

/** 라운지 그리드 타일 한 칸. 사진은 비공개 저장이라 열람용 presigned URL로 내려준다. */
data class SelfIntroPostItemResponse(
	val postId: Long,
	val authorNickname: String?,
	val likeCount: Int,
	val imageUrl: String?,
	val authorGender: Gender?,
	/** 작성자 만 나이. 생년월일 미설정이면 null. */
	val authorAge: Int?,
	val authorProfileImageCode: String?,
	val authorJob: String?,
	/** 작성자 회사명. 회사 인증을 하지 않았으면 null. (있으면 카드에 회사 인증 뱃지를 단다) */
	val authorCompanyName: String?,
	/** 작성자 학교명. 학교 인증을 하지 않았으면 null. (있으면 카드에 학교 인증 뱃지를 단다) */
	val authorUniversityName: String?,
	/** 작성자 활동지역 표시 문자열(시/도 시/군/구). 지역 미설정이면 null. */
	val authorActivityArea: String?,
) {
	companion object {

		fun of(view: SelfIntroPostView): SelfIntroPostItemResponse =
			SelfIntroPostItemResponse(
				postId = view.postId,
				authorNickname = view.authorNickname,
				likeCount = view.likeCount,
				imageUrl = view.imageUrl,
				authorGender = view.authorGender,
				authorAge = view.authorAge,
				authorProfileImageCode = view.authorProfileImageCode,
				authorJob = view.authorJob,
				authorCompanyName = view.authorCompanyName,
				authorUniversityName = view.authorUniversityName,
				authorActivityArea = view.authorActivityArea,
			)
	}
}
