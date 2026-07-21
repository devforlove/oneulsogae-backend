package com.org.oneulsogae.core.lounge.query.dto

import com.org.oneulsogae.common.coin.CoinUsageType
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
	val longDistance: String,
	val desiredAge: String,
	val mbti: String,
	val marriageThought: String,
	val preferredPartner: String,
	val charmPoint: String,
	val freeWord: String,
	/** 작성자 만 나이. 서비스가 [birthday]와 기준일로 채운다. (생년월일이 없으면 null) */
	val age: Int? = null,
	/** 사진의 열람용 URL 목록(노출 순서). 서비스가 [imageKeys]를 presign해 채운다. */
	val imageUrls: List<String> = emptyList(),
	/** 사진의 S3 오브젝트 키 목록(노출 순서). */
	val imageKeys: List<String> = emptyList(),
	/**
	 * 이 글의 작성자에게 대화를 신청할 때 드는 코인 수.
	 * 글마다 다르지 않은 전역 정책값([CoinUsageType.LOUNGE_CHAT_INIT])이며, 클라이언트가 신청 전에 비용을 안내할 수 있도록 상세에 함께 내려준다.
	 * (실제 차감도 서버가 같은 유형의 정책값으로 산출한다 — [com.org.oneulsogae.core.lounge.command.application.RequestLoungeChatService])
	 */
	val chatRequestCoinAmount: Int = CoinUsageType.LOUNGE_CHAT_INIT.coinAmount,
	/**
	 * 조회한 사용자가 이 글에 이미 대화를 신청했는지 여부. 서비스가 채운다.
	 * 신청 버튼을 "신청함"으로 바꾸는 데 쓴다. 상태(PENDING/ACCEPTED)는 구분하지 않는다 — 어느 쪽이든 다시 신청할 수 없다.
	 */
	val chatRequestedByMe: Boolean = false,
) {
	/** dao 투영용 생성자. 나이·사진은 서비스가 채운다. */
	constructor(
		postId: Long,
		authorNickname: String?,
		likeCount: Int,
		gender: Gender?,
		birthday: LocalDate?,
		height: Int?,
		activityArea: String?,
		job: String?,
		companyName: String?,
		universityName: String?,
		longDistance: String,
		desiredAge: String,
		mbti: String,
		marriageThought: String,
		preferredPartner: String,
		charmPoint: String,
		freeWord: String,
	) : this(
		postId, authorNickname, likeCount, gender, birthday, height, activityArea, job, companyName, universityName,
		longDistance, desiredAge, mbti, marriageThought, preferredPartner, charmPoint, freeWord,
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
}
