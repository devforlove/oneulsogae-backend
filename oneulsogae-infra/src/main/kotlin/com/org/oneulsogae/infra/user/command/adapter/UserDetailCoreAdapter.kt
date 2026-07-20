package com.org.oneulsogae.infra.user.command.adapter

import com.org.oneulsogae.admin.companyverification.command.application.port.out.UpdateUserCompanyNamePort
import com.org.oneulsogae.admin.memberverification.command.application.port.out.GetVerifiedUserProfilePort
import com.org.oneulsogae.admin.memberverification.command.application.port.out.UpdateUserCompanyNamePort as UpdateUserCompanyNameOnMemberVerificationPort
import com.org.oneulsogae.admin.memberverification.command.application.port.out.VerifiedUserProfile
import com.org.oneulsogae.core.user.command.application.port.out.AnonymizeUserDetailPort
import com.org.oneulsogae.core.user.command.application.port.out.GetUserDetailPort
import com.org.oneulsogae.core.user.command.application.port.out.SaveUserDetailPort
import com.org.oneulsogae.core.user.command.domain.UserDetail
import com.org.oneulsogae.infra.user.command.entity.UserDetailEntity
import com.org.oneulsogae.infra.user.command.mapper.toDomain
import com.org.oneulsogae.infra.user.command.mapper.toEntity
import com.org.oneulsogae.infra.user.command.repository.UserDetailJpaRepository
import java.time.LocalDateTime
import org.springframework.stereotype.Component

/**
 * core 모듈이 쓰는 [com.org.oneulsogae.infra.user.command.entity.UserDetailEntity]의 out-port 어댑터. (Spring Data 메서드 쿼리)
 * 명령 흐름의 단건 로드([GetUserDetailPort])와 저장([SaveUserDetailPort])을 구현한다.
 * admin 심사 승인 시 회사명 확정([UpdateUserCompanyNamePort], 회사·멤버 인증 동명 포트를 함께 구현)과
 * gathering_profile 스냅샷 소스 조회([GetVerifiedUserProfilePort])도 함께 담당한다.
 * 조회용 프로필·사용자+프로필 조인은 query 쪽 QueryDSL 구현체([com.org.oneulsogae.infra.user.query.GetUserDetailDaoImpl], [com.org.oneulsogae.infra.user.query.GetUserWithDetailDaoImpl])가 따로 담당한다.
 */
@Component
class UserDetailCoreAdapter(
	private val userDetailJpaRepository: UserDetailJpaRepository,
) : GetUserDetailPort,
	SaveUserDetailPort,
	AnonymizeUserDetailPort,
	UpdateUserCompanyNamePort,
	UpdateUserCompanyNameOnMemberVerificationPort,
	GetVerifiedUserProfilePort {

	override fun findByUserId(userId: Long): UserDetail? =
		userDetailJpaRepository.findByUserId(userId)?.toDomain()

	override fun existsCompanyEmailUsedByOther(companyEmail: String, excludeUserId: Long): Boolean =
		userDetailJpaRepository.existsByCompanyEmailAndUserIdNot(companyEmail, excludeUserId)

	override fun existsUniversityEmailUsedByOther(universityEmail: String, excludeUserId: Long): Boolean =
		userDetailJpaRepository.existsByUniversityEmailAndUserIdNot(universityEmail, excludeUserId)

	override fun save(userDetail: UserDetail): UserDetail =
		userDetailJpaRepository.save(userDetail.toEntity()).toDomain()

	override fun anonymize(userId: Long, at: LocalDateTime) {
		// 파기 시점의 user_details는 아직 deleted_at이 null이라 일반 조회로 로드된다.
		val entity = userDetailJpaRepository.findByUserId(userId) ?: return
		entity.anonymize()
		entity.softDelete(at)
		userDetailJpaRepository.save(entity)
	}

	// admin 심사 승인: 어드민이 기입한 회사명을 프로필에 확정한다. (회사·멤버 인증 두 포트를 함께 만족. 정상 유저에겐 user_details 행이 항상 존재)
	override fun updateCompanyName(userId: Long, companyName: String) {
		val entity: UserDetailEntity = userDetailJpaRepository.findByUserId(userId)
			?: throw IllegalStateException("사용자 프로필을 찾을 수 없습니다: $userId")
		entity.companyName = companyName
		userDetailJpaRepository.save(entity)
	}

	// 멤버 인증 승인: gathering_profile 스냅샷에 담을 생일·키·프로필이미지코드를 조회한다. (없으면 null)
	override fun findProfileSource(userId: Long): VerifiedUserProfile? =
		userDetailJpaRepository.findByUserId(userId)
			?.let { entity: UserDetailEntity ->
				VerifiedUserProfile(
					birthday = entity.birthday,
					height = entity.height,
					profileImageCode = entity.profileImageCode,
				)
			}
}
