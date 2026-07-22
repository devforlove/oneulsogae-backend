package com.org.oneulsogae.core.lounge.query.service

import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.common.time.TimeGenerator
import com.org.oneulsogae.core.lounge.LoungeErrorCode
import com.org.oneulsogae.core.lounge.query.dao.GetSelfIntroPostDao
import com.org.oneulsogae.core.lounge.query.dto.SelfIntroPostDetailView
import com.org.oneulsogae.core.lounge.query.dto.SelfIntroPostPage
import com.org.oneulsogae.core.lounge.query.dto.SelfIntroPostView
import com.org.oneulsogae.core.lounge.query.service.port.`in`.GetSelfIntroPostsUseCase
import com.org.oneulsogae.core.lounge.query.service.port.out.LoungeImageUrlPort
import com.org.oneulsogae.core.user.query.service.port.`in`.CheckCompanyVerifiedUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [GetSelfIntroPostsUseCase] 구현. (조회 전용 - 쓰기 부수효과 없음)
 * 조회 dao([GetSelfIntroPostDao])에만 의존하며, 목록은 페이지 크기 + 1건을 읽어 다음 페이지 존재 여부를 판정한다. (COUNT 없이 커서 페이징)
 * 사진 키는 presigned URL로 변환해 내려주고, 상세의 만 나이는 [TimeGenerator]의 오늘 날짜로 계산한다.
 * 회사 인증 여부는 user 도메인 in-port([CheckCompanyVerifiedUseCase])로 읽어 화면 분기용 플래그로 함께 내려준다.
 * 목록 조회는 비로그인도 허용되며, 그때 개인화 값(미수락 건수·회사 인증)은 기본값(0/false)이다.
 */
@Service
@Transactional(readOnly = true)
class GetSelfIntroPostsService(
	private val getSelfIntroPostDao: GetSelfIntroPostDao,
	private val loungeImageUrlPort: LoungeImageUrlPort,
	private val timeGenerator: TimeGenerator,
	private val checkCompanyVerifiedUseCase: CheckCompanyVerifiedUseCase,
) : GetSelfIntroPostsUseCase {

	override fun getPosts(userId: Long?, cursor: Long?): SelfIntroPostPage {
		val rows: List<SelfIntroPostView> = getSelfIntroPostDao.findPage(cursor, PAGE_SIZE + 1)
		// 비로그인(userId == null)이면 개인화 값(미수락 건수·회사 인증)은 기본값(0/false)으로 내려간다.
		return SelfIntroPostPage.of(rows, PAGE_SIZE)
			.withImageUrls { imageKey: String -> loungeImageUrlPort.presignedGetUrl(imageKey) }
			.withAuthorAges(timeGenerator.today())
			.let { page: SelfIntroPostPage ->
				if (userId == null) {
					page
				} else {
					page
						.withPendingChatRequestCounts(
							received = getSelfIntroPostDao.countReceivedPendingChatRequests(userId),
							sent = getSelfIntroPostDao.countSentPendingChatRequests(userId),
						)
						.withCompanyVerified(checkCompanyVerifiedUseCase.isCompanyVerified(userId))
				}
			}
	}

	override fun getPost(userId: Long?, postId: Long): SelfIntroPostDetailView {
		val view: SelfIntroPostDetailView = getSelfIntroPostDao.findDetailByPostId(postId)
			?: throw BusinessException(LoungeErrorCode.SELF_INTRO_POST_NOT_FOUND, "셀소를 찾을 수 없습니다: $postId")
		// 비로그인(userId == null)이면 개인화 값(기존 신청 여부·회사 인증)은 기본값(false)이다.
		return view
			.withPhotosAndAge(
				imageKeys = getSelfIntroPostDao.findImageKeysByPostId(postId),
				today = timeGenerator.today(),
			) { imageKey: String -> loungeImageUrlPort.presignedGetUrl(imageKey) }
			.withChatRequested(userId != null && getSelfIntroPostDao.existsChatRequest(postId, userId))
			.withCompanyVerified(userId != null && checkCompanyVerifiedUseCase.isCompanyVerified(userId))
	}

	companion object {
		/** 한 페이지에 내려주는 셀소 건수. (프론트 라운지 그리드 6줄 × 4열) */
		const val PAGE_SIZE: Int = 24
	}
}
