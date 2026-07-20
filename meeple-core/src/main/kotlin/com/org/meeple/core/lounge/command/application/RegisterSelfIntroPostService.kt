package com.org.meeple.core.lounge.command.application

import com.org.meeple.core.common.time.TimeGenerator
import com.org.meeple.core.lounge.command.application.port.`in`.RegisterSelfIntroPostUseCase
import com.org.meeple.core.lounge.command.application.port.`in`.command.RegisterSelfIntroPostCommand
import com.org.meeple.core.lounge.command.application.port.`in`.result.RegisterSelfIntroPostResult
import com.org.meeple.core.lounge.command.application.port.out.CountRecentSelfIntroPostPort
import com.org.meeple.core.lounge.command.application.port.out.FileStoragePort
import com.org.meeple.core.lounge.command.application.port.out.SaveLoungePostImagePort
import com.org.meeple.core.lounge.command.application.port.out.SaveLoungePostPort
import com.org.meeple.core.lounge.command.application.port.out.SaveSelfIntroPostPort
import com.org.meeple.core.lounge.command.domain.LoungePost
import com.org.meeple.core.lounge.command.domain.LoungePostImages
import com.org.meeple.core.lounge.command.domain.SelfIntroPost
import com.org.meeple.core.user.query.service.port.`in`.GetUserByIdUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

/**
 * [RegisterSelfIntroPostUseCase] 구현.
 * 본문·사진과 등록 빈도를 모두 검증한 뒤 사진을 S3에 비공개로 올리고([FileStoragePort]),
 * 라운지 글(공통 골격) → 셀소 본문 → 사진 순으로 저장한다.
 * 유저 존재 확인은 user 도메인 in-port([GetUserByIdUseCase])로 위임한다(없으면 그쪽이 USER_NOT_FOUND).
 */
@Service
class RegisterSelfIntroPostService(
	private val getUserByIdUseCase: GetUserByIdUseCase,
	private val countRecentSelfIntroPostPort: CountRecentSelfIntroPostPort,
	private val fileStoragePort: FileStoragePort,
	private val saveLoungePostPort: SaveLoungePostPort,
	private val saveSelfIntroPostPort: SaveSelfIntroPostPort,
	private val saveLoungePostImagePort: SaveLoungePostImagePort,
	private val timeGenerator: TimeGenerator,
) : RegisterSelfIntroPostUseCase {

	@Transactional
	override fun register(userId: Long, command: RegisterSelfIntroPostCommand): RegisterSelfIntroPostResult {
		getUserByIdUseCase.getById(userId) // 존재 검증 (없으면 USER_NOT_FOUND)

		// 잘못된 입력이 S3에 올라가지 않도록 업로드 전에 본문·사진·등록 빈도를 모두 검증한다. (롤백돼도 S3 고아 객체가 남지 않게)
		SelfIntroPost.validateContent(
			longDistance = command.longDistance,
			desiredAge = command.desiredAge,
			mbti = command.mbti,
			marriageThought = command.marriageThought,
			preferredPartner = command.preferredPartner,
			charmPoint = command.charmPoint,
			freeWord = command.freeWord,
		)
		SelfIntroPost.validatePhotoCount(command.photos.size)
		command.photos.forEach { photo: RegisterSelfIntroPostCommand.FilePart ->
			SelfIntroPost.validatePhoto(photo.contentType, photo.size)
		}
		val since: LocalDateTime = SelfIntroPost.limitWindowSince(timeGenerator.now())
		SelfIntroPost.validateDailyLimit(countRecentSelfIntroPostPort.countSelfIntroPostsCreatedAfter(userId, since))

		val imageKeys: List<String> = command.photos.map { photo: RegisterSelfIntroPostCommand.FilePart ->
			uploadPhoto(userId, photo)
		}

		val post: LoungePost = saveLoungePostPort.save(LoungePost.createSelfIntro(userId))
		saveSelfIntroPostPort.save(
			SelfIntroPost.create(
				postId = post.id,
				longDistance = command.longDistance,
				desiredAge = command.desiredAge,
				mbti = command.mbti,
				marriageThought = command.marriageThought,
				preferredPartner = command.preferredPartner,
				charmPoint = command.charmPoint,
				freeWord = command.freeWord,
			),
		)
		saveLoungePostImagePort.saveAll(LoungePostImages.of(post.id, imageKeys))

		return RegisterSelfIntroPostResult(post.id)
	}

	/** 사진 한 장을 사용자별 폴더 아래 충돌 없는 키로 업로드하고 그 키를 반환한다. (예: lounge-posts/42/{uuid}.jpg) */
	private fun uploadPhoto(userId: Long, photo: RegisterSelfIntroPostCommand.FilePart): String {
		// 검증을 통과한 파일이므로 contentType은 null이 아니다.
		val contentType: String = photo.contentType!!
		val extension: String = SelfIntroPost.extensionOf(contentType)
		val key: String = "$KEY_PREFIX/$userId/${UUID.randomUUID()}.$extension"
		return fileStoragePort.upload(key, photo.content, contentType)
	}

	companion object {
		private const val KEY_PREFIX: String = "lounge-posts"
	}
}
