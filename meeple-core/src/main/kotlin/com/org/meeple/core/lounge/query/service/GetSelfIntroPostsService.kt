package com.org.meeple.core.lounge.query.service

import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.common.time.TimeGenerator
import com.org.meeple.core.lounge.LoungeErrorCode
import com.org.meeple.core.lounge.query.dao.GetSelfIntroPostDao
import com.org.meeple.core.lounge.query.dto.SelfIntroPostDetailView
import com.org.meeple.core.lounge.query.dto.SelfIntroPostPage
import com.org.meeple.core.lounge.query.dto.SelfIntroPostView
import com.org.meeple.core.lounge.query.service.port.`in`.GetSelfIntroPostsUseCase
import com.org.meeple.core.lounge.query.service.port.out.LoungeImageUrlPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [GetSelfIntroPostsUseCase] 구현. (조회 전용 - 쓰기 부수효과 없음)
 * 조회 dao([GetSelfIntroPostDao])에만 의존하며, 목록은 페이지 크기 + 1건을 읽어 다음 페이지 존재 여부를 판정한다. (COUNT 없이 커서 페이징)
 * 사진 키는 presigned URL로 변환해 내려주고, 상세의 만 나이는 [TimeGenerator]의 오늘 날짜로 계산한다.
 */
@Service
@Transactional(readOnly = true)
class GetSelfIntroPostsService(
	private val getSelfIntroPostDao: GetSelfIntroPostDao,
	private val loungeImageUrlPort: LoungeImageUrlPort,
	private val timeGenerator: TimeGenerator,
) : GetSelfIntroPostsUseCase {

	override fun getPosts(cursor: Long?): SelfIntroPostPage {
		val rows: List<SelfIntroPostView> = getSelfIntroPostDao.findPage(cursor, PAGE_SIZE + 1)
		return SelfIntroPostPage.of(rows, PAGE_SIZE)
			.withImageUrls { imageKey: String -> loungeImageUrlPort.presignedGetUrl(imageKey) }
	}

	override fun getPost(postId: Long): SelfIntroPostDetailView {
		val view: SelfIntroPostDetailView = getSelfIntroPostDao.findDetailByPostId(postId)
			?: throw BusinessException(LoungeErrorCode.SELF_INTRO_POST_NOT_FOUND, "셀소를 찾을 수 없습니다: $postId")
		return view.withPhotosAndAge(
			imageKeys = getSelfIntroPostDao.findImageKeysByPostId(postId),
			today = timeGenerator.today(),
		) { imageKey: String -> loungeImageUrlPort.presignedGetUrl(imageKey) }
	}

	companion object {
		/** 한 페이지에 내려주는 셀소 건수. (프론트 라운지 그리드 6줄 × 4열) */
		const val PAGE_SIZE: Int = 24
	}
}
