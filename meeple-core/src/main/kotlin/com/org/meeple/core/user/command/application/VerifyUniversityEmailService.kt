package com.org.meeple.core.user.command.application

import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.common.time.TimeGenerator
import com.org.meeple.core.match.command.application.port.`in`.SyncMatchUserUseCase
import com.org.meeple.core.user.UserErrorCode
import com.org.meeple.core.user.command.application.port.`in`.VerifyUniversityEmailUseCase
import com.org.meeple.core.user.command.application.port.`in`.result.VerifyUniversityEmailResult
import com.org.meeple.core.user.command.application.port.out.GetUniversityEmailVerificationPort
import com.org.meeple.core.user.command.application.port.out.GetUserDetailPort
import com.org.meeple.core.user.command.application.port.out.SaveUniversityEmailVerificationPort
import com.org.meeple.core.user.command.application.port.out.SaveUserDetailPort
import com.org.meeple.core.user.command.domain.UniversityEmailVerification
import com.org.meeple.core.user.command.domain.UserDetail
import com.org.meeple.core.user.query.service.port.`in`.GetUserUniversityUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * [VerifyUniversityEmailUseCase] 구현.
 * 사용자의 가장 최근 인증 요청을 찾아 입력한 인증번호와 비교한다. (재전송으로 누적된 옛 코드는 자동 무효)
 * 일치/미만료/미사용을 확인한 뒤 학교명을 조회([GetUserUniversityUseCase])해 학교 이메일·학교명을 프로필(user_details)에 반영하고,
 * 매칭 읽기 모델(match_user)의 학교명도 갱신한다. 인증번호를 사용 처리한다.
 * 온보딩과 무관한 선택적 추가 인증이므로 가입 상태 전이·코인 지급·추천을 하지 않는다.
 */
@Service
class VerifyUniversityEmailService(
	private val getUniversityEmailVerificationPort: GetUniversityEmailVerificationPort,
	private val saveUniversityEmailVerificationPort: SaveUniversityEmailVerificationPort,
	private val getUserUniversityUseCase: GetUserUniversityUseCase,
	private val getUserDetailPort: GetUserDetailPort,
	private val saveUserDetailPort: SaveUserDetailPort,
	private val syncMatchUserUseCase: SyncMatchUserUseCase,
	private val timeGenerator: TimeGenerator,
) : VerifyUniversityEmailUseCase {

	@Transactional
	override fun verify(userId: Long, code: String): VerifyUniversityEmailResult {
		val now: LocalDateTime = timeGenerator.now()

		// 사용자의 가장 최근 인증 요청을 조회한다. (없으면 예외)
		val verification: UniversityEmailVerification = getUniversityEmailVerificationPort.findLatestByUserId(userId)
			?: throw BusinessException(UserErrorCode.VERIFICATION_NOT_FOUND)
		verification.validate(code, now)

		// 인증번호를 사용(검증) 처리해 재사용을 막는다.
		saveUniversityEmailVerificationPort.save(verification.verify(now))

		// 학교 이메일 도메인으로 학교명을 조회한다. (등록된 학교가 아니면 예외 — 발송 시점과 동일한 규칙)
		val universityName: String = getUserUniversityUseCase.findUniversityNameByEmail(verification.universityEmail)
			?: throw BusinessException(UserErrorCode.UNIVERSITY_NOT_FOUND)

		// 검증을 마친 학교 이메일·학교명을 프로필(user_details)에 기록한다.
		confirmUniversityOnProfile(verification.userId, verification.universityEmail, universityName)

		// 매칭 읽기 모델(match_user)에도 학교명을 기록한다. (매칭 풀에 적재돼 있을 때만 반영 — 미적재면 무시)
		syncMatchUserUseCase.updateUniversity(verification.userId, universityName)

		return VerifyUniversityEmailResult(universityName)
	}

	// 검증을 마친 학교 이메일과 (조회한) 학교명을 프로필에 반영한다.
	private fun confirmUniversityOnProfile(userId: Long, universityEmail: String, universityName: String) {
		val detail: UserDetail = getUserDetailPort.findByUserId(userId)
			?: throw BusinessException(UserErrorCode.USER_DETAIL_NOT_FOUND, "사용자 프로필을 찾을 수 없습니다: $userId")
		saveUserDetailPort.save(detail.copy(universityEmail = universityEmail, universityName = universityName))
	}
}
