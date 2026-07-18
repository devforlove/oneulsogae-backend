package com.org.meeple.infra.user.command.adapter

import com.org.meeple.admin.companyverification.command.application.port.out.UpdateUserCompanyNamePort
import com.org.meeple.admin.memberverification.command.application.port.out.UpdateUserVerifiedProfilePort
import com.org.meeple.core.user.command.application.port.out.AnonymizeUserDetailPort
import com.org.meeple.core.user.command.application.port.out.GetUserDetailPort
import com.org.meeple.core.user.command.application.port.out.SaveUserDetailPort
import com.org.meeple.core.user.command.domain.UserDetail
import com.org.meeple.infra.user.command.entity.UserDetailEntity
import com.org.meeple.infra.user.command.mapper.toDomain
import com.org.meeple.infra.user.command.mapper.toEntity
import com.org.meeple.infra.user.command.repository.UserDetailJpaRepository
import java.time.LocalDateTime
import org.springframework.stereotype.Component

/**
 * core 모듈이 쓰는 [com.org.meeple.infra.user.command.entity.UserDetailEntity]의 out-port 어댑터. (Spring Data 메서드 쿼리)
 * 명령 흐름의 단건 로드([GetUserDetailPort])와 저장([SaveUserDetailPort])을 구현한다.
 * 조회용 프로필·사용자+프로필 조인은 query 쪽 QueryDSL 구현체([com.org.meeple.infra.user.query.GetUserDetailDaoImpl], [com.org.meeple.infra.user.query.GetUserWithDetailDaoImpl])가 따로 담당한다.
 */
@Component
class UserDetailCoreAdapter(
	private val userDetailJpaRepository: UserDetailJpaRepository,
) : GetUserDetailPort, SaveUserDetailPort, AnonymizeUserDetailPort, UpdateUserCompanyNamePort, UpdateUserVerifiedProfilePort {

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

	// admin 심사 승인: 어드민이 기입한 회사명을 프로필에 확정한다. (정상 유저에겐 user_details 행이 항상 존재)
	override fun updateCompanyName(userId: Long, companyName: String) {
		val entity: UserDetailEntity = userDetailJpaRepository.findByUserId(userId)
			?: throw IllegalStateException("사용자 프로필을 찾을 수 없습니다: $userId")
		entity.companyName = companyName
		userDetailJpaRepository.save(entity)
	}

	// 멤버 인증 승인: 어드민이 확정한 회사명·직종·직장 상세를 프로필에 반영한다. (정상 유저에겐 user_details 행이 항상 존재)
	override fun updateVerifiedProfile(userId: Long, companyName: String, jobCategory: String, jobDetail: String) {
		val entity: UserDetailEntity = userDetailJpaRepository.findByUserId(userId)
			?: throw IllegalStateException("사용자 프로필을 찾을 수 없습니다: $userId")
		entity.companyName = companyName
		entity.jobCategory = jobCategory
		entity.jobDetail = jobDetail
		userDetailJpaRepository.save(entity)
	}
}
