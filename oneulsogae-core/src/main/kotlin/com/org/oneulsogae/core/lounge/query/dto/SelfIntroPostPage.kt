package com.org.oneulsogae.core.lounge.query.dto

import java.time.LocalDate

/**
 * 라운지 셀소 목록([SelfIntroPostView])의 커서 페이지(일급 컬렉션).
 * 최신(postId 내림차순)순 목록과 다음 페이지 존재 여부·커서를 함께 담아, 커서 산출 규칙을 한곳에 응집시킨다.
 */
class SelfIntroPostPage private constructor(
	/** 현재 페이지의 셀소 목록. 최신(postId 내림차순)순. */
	val values: List<SelfIntroPostView>,
	/** 다음(더 과거) 페이지가 있는지 여부. */
	val hasNext: Boolean,
	/**
	 * 조회한 사용자가 자기 셀소로 받은 신청 중 **아직 수락하지 않은(PENDING)** 건수. 서비스가 채운다.
	 * 내가 쓴 모든 셀소를 합산한 값이며, 목록 화면의 "받은 신청" 배지에 쓴다. (수락하면 줄어든다)
	 */
	val receivedPendingChatRequestCount: Int = 0,
	/**
	 * 조회한 사용자가 남의 셀소에 보낸 신청 중 **아직 수락되지 않은(PENDING)** 건수. 서비스가 채운다.
	 * 상대의 응답을 기다리는 신청 수이며, 목록 화면의 "보낸 신청" 배지에 쓴다. (수락되면 줄어든다)
	 */
	val sentPendingChatRequestCount: Int = 0,
	/** 조회한 사용자가 회사 인증을 마쳤는지 여부. 서비스가 채운다. 프론트엔드가 미인증 사용자 화면을 분기하는 데 쓴다. */
	val companyVerified: Boolean = false,
) {

	/** 다음(더 과거) 페이지 조회의 기준 커서. 현재 페이지 마지막(가장 오래된) 글의 postId이며, 다음 페이지가 없으면 null. */
	val nextCursor: Long?
		get() = if (hasNext) values.lastOrNull()?.postId else null

	/** 각 항목의 대표 사진 키를 [presign]으로 변환한 열람용 URL을 채운 페이지를 만든다. (사진이 없으면 null 유지) */
	fun withImageUrls(presign: (String) -> String): SelfIntroPostPage =
		SelfIntroPostPage(
			values = values.map { view: SelfIntroPostView ->
				view.copy(imageUrl = view.imageKey?.let(presign))
			},
			hasNext = hasNext,
			receivedPendingChatRequestCount = receivedPendingChatRequestCount,
			sentPendingChatRequestCount = sentPendingChatRequestCount,
			companyVerified = companyVerified,
		)

	/** 각 항목의 작성자 만 나이를 기준일([today])로 채운 페이지를 만든다. */
	fun withAuthorAges(today: LocalDate): SelfIntroPostPage =
		SelfIntroPostPage(
			values = values.map { view: SelfIntroPostView -> view.withAge(today) },
			hasNext = hasNext,
			receivedPendingChatRequestCount = receivedPendingChatRequestCount,
			sentPendingChatRequestCount = sentPendingChatRequestCount,
			companyVerified = companyVerified,
		)

	/** 조회한 사용자의 미수락 신청 건수(받은·보낸)를 반영한 페이지를 만든다. */
	fun withPendingChatRequestCounts(received: Int, sent: Int): SelfIntroPostPage =
		SelfIntroPostPage(
			values = values,
			hasNext = hasNext,
			receivedPendingChatRequestCount = received,
			sentPendingChatRequestCount = sent,
			companyVerified = companyVerified,
		)

	/** 조회한 사용자의 회사 인증 여부를 반영한 페이지를 만든다. */
	fun withCompanyVerified(companyVerified: Boolean): SelfIntroPostPage =
		SelfIntroPostPage(
			values = values,
			hasNext = hasNext,
			receivedPendingChatRequestCount = receivedPendingChatRequestCount,
			sentPendingChatRequestCount = sentPendingChatRequestCount,
			companyVerified = companyVerified,
		)

	companion object {

		/**
		 * "한 건 더 읽기(size + 1)"로 조회한 행들로 페이지를 만든다.
		 * [rows]가 [size]보다 많으면 다음 페이지가 있는 것으로 보고, 초과분은 잘라낸다.
		 */
		fun of(rows: List<SelfIntroPostView>, size: Int): SelfIntroPostPage =
			SelfIntroPostPage(values = rows.take(size), hasNext = rows.size > size)
	}
}
