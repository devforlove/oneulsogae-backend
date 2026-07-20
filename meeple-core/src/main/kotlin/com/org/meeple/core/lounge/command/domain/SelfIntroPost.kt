package com.org.meeple.core.lounge.command.domain

import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.lounge.LoungeErrorCode
import java.time.LocalDateTime

/**
 * 셀프 소개팅(셀소) 본문 도메인 모델. 공통 골격([LoungePost])에 [postId]로 붙는다.
 * 작성자의 성별·나이·키·지역·직업은 프로필(user 도메인)이 소유하므로 여기에 담지 않고 조회 시 조인한다.
 * 본문 항목은 모두 필수이며 각자 최대 길이가 있다. 등록 빈도는 [validateDailyLimit]로 제한한다.
 */
data class SelfIntroPost(
	val id: Long = 0,
	val postId: Long,
	/** 장거리 연애 가능 여부에 대한 답변. */
	val longDistance: String,
	/** 원하는 상대 나이대. */
	val desiredAge: String,
	/** 본인 MBTI. */
	val mbti: String,
	/** 결혼에 대한 생각. */
	val marriageThought: String,
	/** 선호하는 상대의 성격·가치관. */
	val preferredPartner: String,
	/** 나의 매력 어필. */
	val charmPoint: String,
	/** 자유 한마디. */
	val freeWord: String,
) {

	companion object {

		/** 허용하는 사진 콘텐츠 타입. (JPEG·PNG) */
		val PHOTO_CONTENT_TYPES: Set<String> = setOf("image/jpeg", "image/png")

		/** 사진 한 장의 최대 크기(바이트). 10MB. */
		const val MAX_PHOTO_SIZE_BYTES: Long = 10L * 1024 * 1024

		/** 한 글에 올릴 수 있는 최대 사진 장수. */
		const val MAX_PHOTO_COUNT: Int = 5

		/** 한 줄 입력(장거리·희망 나이대)의 최대 길이. */
		const val MAX_SHORT_TEXT_LENGTH: Int = 40

		/** MBTI의 최대 길이. */
		const val MAX_MBTI_LENGTH: Int = 10

		/** 서술형 입력(결혼관·선호 상대·매력 어필·자유 한마디)의 최대 길이. */
		const val MAX_LONG_TEXT_LENGTH: Int = 500

		/** 등록 빈도 제한 기준 시간(시간 단위). 이 구간 안에 등록한 셀소가 [MAX_POSTS_PER_WINDOW]건 이상이면 막는다. */
		const val LIMIT_WINDOW_HOURS: Long = 24

		/** 제한 구간 안에 등록할 수 있는 셀소 수. */
		const val MAX_POSTS_PER_WINDOW: Int = 1

		/** 본문을 검증해 신규 셀소를 만든다. */
		fun create(
			postId: Long,
			longDistance: String,
			desiredAge: String,
			mbti: String,
			marriageThought: String,
			preferredPartner: String,
			charmPoint: String,
			freeWord: String,
		): SelfIntroPost {
			validateContent(longDistance, desiredAge, mbti, marriageThought, preferredPartner, charmPoint, freeWord)
			return SelfIntroPost(
				postId = postId,
				longDistance = longDistance,
				desiredAge = desiredAge,
				mbti = mbti,
				marriageThought = marriageThought,
				preferredPartner = preferredPartner,
				charmPoint = charmPoint,
				freeWord = freeWord,
			)
		}

		/**
		 * 본문 검증. 모든 항목이 필수이며 각자 최대 길이를 넘을 수 없다.
		 * 하나라도 비었거나 길이를 넘으면 [LoungeErrorCode.SELF_INTRO_INVALID_CONTENT].
		 */
		fun validateContent(
			longDistance: String,
			desiredAge: String,
			mbti: String,
			marriageThought: String,
			preferredPartner: String,
			charmPoint: String,
			freeWord: String,
		) {
			validateText(longDistance, MAX_SHORT_TEXT_LENGTH)
			validateText(desiredAge, MAX_SHORT_TEXT_LENGTH)
			validateText(mbti, MAX_MBTI_LENGTH)
			validateText(marriageThought, MAX_LONG_TEXT_LENGTH)
			validateText(preferredPartner, MAX_LONG_TEXT_LENGTH)
			validateText(charmPoint, MAX_LONG_TEXT_LENGTH)
			validateText(freeWord, MAX_LONG_TEXT_LENGTH)
		}

		/**
		 * 첨부한 사진 장수 검증.
		 * - 0장: [LoungeErrorCode.SELF_INTRO_PHOTO_REQUIRED]
		 * - [MAX_PHOTO_COUNT] 초과: [LoungeErrorCode.SELF_INTRO_TOO_MANY_PHOTOS]
		 */
		fun validatePhotoCount(count: Int) {
			if (count < 1) {
				throw BusinessException(LoungeErrorCode.SELF_INTRO_PHOTO_REQUIRED)
			}
			if (count > MAX_PHOTO_COUNT) {
				throw BusinessException(LoungeErrorCode.SELF_INTRO_TOO_MANY_PHOTOS)
			}
		}

		/**
		 * 사진 파일 한 장 검증. (업로드 전에 호출해 잘못된 파일이 S3에 올라가지 않게 한다)
		 * - 빈 파일: [LoungeErrorCode.SELF_INTRO_EMPTY_PHOTO]
		 * - 허용하지 않는 형식: [LoungeErrorCode.SELF_INTRO_INVALID_PHOTO_TYPE]
		 * - 크기 초과: [LoungeErrorCode.SELF_INTRO_PHOTO_TOO_LARGE]
		 */
		fun validatePhoto(contentType: String?, size: Long) {
			if (size <= 0L) {
				throw BusinessException(LoungeErrorCode.SELF_INTRO_EMPTY_PHOTO)
			}
			if (contentType == null || contentType.lowercase() !in PHOTO_CONTENT_TYPES) {
				throw BusinessException(LoungeErrorCode.SELF_INTRO_INVALID_PHOTO_TYPE)
			}
			if (size > MAX_PHOTO_SIZE_BYTES) {
				throw BusinessException(LoungeErrorCode.SELF_INTRO_PHOTO_TOO_LARGE)
			}
		}

		/** 등록 빈도를 셀 구간의 시작 시각. [now]로부터 [LIMIT_WINDOW_HOURS] 이전이다. */
		fun limitWindowSince(now: LocalDateTime): LocalDateTime = now.minusHours(LIMIT_WINDOW_HOURS)

		/**
		 * 등록 빈도 검증. 제한 구간([limitWindowSince] 이후) 안에 등록한 셀소가 [recentCount]건일 때
		 * [MAX_POSTS_PER_WINDOW]건 이상이면 [LoungeErrorCode.SELF_INTRO_DAILY_LIMIT_EXCEEDED].
		 */
		fun validateDailyLimit(recentCount: Int) {
			if (recentCount >= MAX_POSTS_PER_WINDOW) {
				throw BusinessException(LoungeErrorCode.SELF_INTRO_DAILY_LIMIT_EXCEEDED)
			}
		}

		/** 콘텐츠 타입에 대응하는 파일 확장자. (오브젝트 키 생성에 쓴다) */
		fun extensionOf(contentType: String): String =
			when (contentType.lowercase()) {
				"image/jpeg" -> "jpg"
				"image/png" -> "png"
				else -> "bin"
			}

		private fun validateText(value: String, maxLength: Int) {
			if (value.isBlank() || value.length > maxLength) {
				throw BusinessException(LoungeErrorCode.SELF_INTRO_INVALID_CONTENT)
			}
		}
	}
}
