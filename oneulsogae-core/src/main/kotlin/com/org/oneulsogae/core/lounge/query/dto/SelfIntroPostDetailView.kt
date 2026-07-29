package com.org.oneulsogae.core.lounge.query.dto

import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.core.common.time.ageAt
import java.time.LocalDate

/**
 * 라운지 셀소 상세(read model).
 * 성별·생년월일·키·지역·직업·회사명·학교명은 작성자 프로필(user_details·regions)에서 조인해 온 표시용 값이다.
 * dao는 [birthday]와 [imageKeys]까지 채우고, 서비스가 [age](만 나이)와 [imageUrls](presigned)를 채운다.
 */
data class SelfIntroPostDetailView(
	val postId: Long,
	val authorNickname: String?,
	val likeCount: Int,
	val viewCount: Int,
	val gender: Gender?,
	/** 작성자 생년월일. 응답에는 노출하지 않고 [age] 계산에만 쓴다. */
	val birthday: LocalDate?,
	val height: Int?,
	/** 활동지역 표시 문자열(시/도 시/군/구). */
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
	/** 작성자 만 나이. 서비스가 [birthday]와 기준일로 채운다. (생년월일이 없으면 null) */
	val age: Int? = null,
	/** 사진의 열람용 URL 목록(노출 순서). 서비스가 [imageKeys]를 presign해 채운다. */
	val imageUrls: List<String> = emptyList(),
	/** 사진의 S3 오브젝트 키 목록(노출 순서). */
	val imageKeys: List<String> = emptyList(),
	/**
	 * 이 글의 작성자에게 대화를 신청할 때 드는 코인 수.
	 * 뷰어(조회한 사용자) 성별로 갈리는 값이라 서비스가 채운다. 비로그인이면 신청할 수 없으므로 null이다.
	 * (실제 차감도 서버가 신청자 성별 기준으로 산출한다 — [com.org.oneulsogae.core.lounge.command.application.RequestLoungeChatService])
	 */
	val chatRequestCoinAmount: Int? = null,
	/**
	 * 조회한 사용자가 이 글에 이미 대화를 신청했는지 여부. 서비스가 채운다.
	 * 신청 버튼을 "신청함"으로 바꾸는 데 쓴다. 상태(PENDING/ACCEPTED)는 구분하지 않는다 — 어느 쪽이든 다시 신청할 수 없다.
	 */
	val chatRequestedByMe: Boolean = false,
	/**
	 * 조회한 사용자가 회사 인증을 마쳤는지 여부. 서비스가 채운다. (비로그인이면 false)
	 * 대화 신청은 인증 사용자만 가능하므로, 클라이언트가 신청 시도 시점에 인증 안내로 선차단하는 데 쓴다.
	 */
	val companyVerified: Boolean = false,
	/** 조회한 사용자가 이 글에 좋아요를 눌렀는지 여부. 서비스가 채운다. (비로그인이면 false) */
	val likedByMe: Boolean = false,
) {
	/** dao 투영용 생성자. 나이·사진·likedByMe는 서비스가 채운다. */
	constructor(
		postId: Long,
		authorNickname: String?,
		likeCount: Int,
		viewCount: Int,
		gender: Gender?,
		birthday: LocalDate?,
		height: Int?,
		activityArea: String?,
		job: String?,
		companyName: String?,
		universityName: String?,
		mbti: String?,
		interests: String,
		idealType: String,
		charmPoint: String,
	) : this(
		postId, authorNickname, likeCount, viewCount, gender, birthday, height, activityArea, job, companyName, universityName,
		mbti, interests, idealType, charmPoint,
		null, emptyList(), emptyList(),
	)

	/** 사진 키와 기준일을 반영해 만 나이·열람용 URL을 채운 상세를 만든다. */
	fun withPhotosAndAge(imageKeys: List<String>, today: LocalDate, presign: (String) -> String): SelfIntroPostDetailView =
		copy(
			age = birthday?.ageAt(today),
			imageKeys = imageKeys,
			imageUrls = imageKeys.map(presign),
		)

	/** 조회한 사용자의 기존 신청 여부를 반영한 상세를 만든다. */
	fun withChatRequested(requested: Boolean): SelfIntroPostDetailView =
		copy(chatRequestedByMe = requested)

	/** 조회한 사용자(뷰어) 성별 기준 대화 신청 비용을 반영한 상세를 만든다. 비로그인이면 null을 넘긴다. */
	fun withChatRequestCoinAmount(amount: Int?): SelfIntroPostDetailView =
		copy(chatRequestCoinAmount = amount)

	/** 조회한 사용자의 회사 인증 여부를 반영한 상세를 만든다. */
	fun withCompanyVerified(companyVerified: Boolean): SelfIntroPostDetailView =
		copy(companyVerified = companyVerified)

	/** 조회한 사용자의 좋아요 여부를 반영한 상세를 만든다. */
	fun withLikedByMe(liked: Boolean): SelfIntroPostDetailView =
		copy(likedByMe = liked)
}
