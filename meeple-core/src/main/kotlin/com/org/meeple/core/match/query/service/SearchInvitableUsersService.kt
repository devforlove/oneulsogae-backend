package com.org.meeple.core.match.query.service

import com.org.meeple.common.user.Gender
import com.org.meeple.core.match.query.dao.SearchInvitableUsersDao
import com.org.meeple.core.match.query.dto.InvitableUser
import com.org.meeple.core.match.query.service.port.`in`.SearchInvitableUsersUseCase
import com.org.meeple.core.user.query.service.port.`in`.GetUserWithDetailUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [SearchInvitableUsersUseCase] 구현. (조회 전용)
 * 요청자 성별을 user 도메인 in-port([GetUserWithDetailUseCase])로 읽어 dao에 넘긴다. (command 포트 미참조 — query 규칙)
 * 성별이 없으면(매칭 불가) 빈 리스트를 반환한다. ([com.org.meeple.core.user.query.dto.UserWithDetailView.getGender]는 null에서 NPE이므로 호출하지 않고 detail.gender를 직접 본다)
 */
@Service
@Transactional(readOnly = true)
class SearchInvitableUsersService(
	private val getUserWithDetailUseCase: GetUserWithDetailUseCase,
	private val searchInvitableUsersDao: SearchInvitableUsersDao,
) : SearchInvitableUsersUseCase {

	override fun search(requesterId: Long, nickname: String): List<InvitableUser> {
		val requesterGender: Gender = getUserWithDetailUseCase.getByUserId(requesterId).detail.gender
			?: return emptyList()
		return searchInvitableUsersDao.search(requesterGender, requesterId, nickname)
	}
}
