package com.org.meeple.core.match.query.service

import com.org.meeple.common.user.Gender
import com.org.meeple.core.match.query.dao.SearchInvitableUsersDao
import com.org.meeple.core.match.query.dto.InvitableUser
import com.org.meeple.core.match.query.service.port.`in`.SearchInvitableUsersUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [SearchInvitableUsersUseCase] 구현. (조회 전용)
 * 요청자 성별을 match_user에서 직접 조회한다. (command 포트·user 도메인 cross-참조 없이 query dao만 의존)
 * 요청자가 match_user에 없으면(매칭 불가) 빈 리스트를 반환한다.
 */
@Service
@Transactional(readOnly = true)
class SearchInvitableUsersService(
	private val searchInvitableUsersDao: SearchInvitableUsersDao,
) : SearchInvitableUsersUseCase {

	override fun search(requesterId: Long, nickname: String): List<InvitableUser> {
		val requesterGender: Gender = searchInvitableUsersDao.findRequesterGender(requesterId)
			?: return emptyList()
		return searchInvitableUsersDao.search(requesterGender, requesterId, nickname)
	}
}
