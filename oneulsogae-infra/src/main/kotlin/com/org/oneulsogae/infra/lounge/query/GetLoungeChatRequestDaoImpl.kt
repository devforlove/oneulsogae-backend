package com.org.oneulsogae.infra.lounge.query

import com.org.oneulsogae.core.lounge.query.dao.GetLoungeChatRequestDao
import com.org.oneulsogae.core.lounge.query.dto.LoungeChatRequestView
import com.org.oneulsogae.infra.lounge.command.entity.QLoungeChatRequestEntity
import com.org.oneulsogae.infra.region.entity.QRegionEntity
import com.org.oneulsogae.infra.user.command.entity.QUserDetailEntity
import com.querydsl.core.types.Projections
import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.core.types.dsl.NumberPath
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * [GetLoungeChatRequestDao]의 QueryDSL 구현. (조회 전용)
 * 엔티티를 거치지 않고 [LoungeChatRequestView] read model로 바로 투영한다. 만 나이는 서비스가 생년월일로 채운다.
 * 받은 목록과 보낸 목록은 **기준 컬럼(receiver/requester)과 상대방 프로필 조인 대상만 다르고 나머지가 같아** 한 쿼리로 묶었다.
 * 상대방 프로필(user_details)과 활동지역(regions)은 없어도 신청은 보여야 하므로 left join한다.
 * 기준 사용자 동등 + id 내림차순 keyset(`id < :beforeId`)이 각각 `idx_receiver_user_id_id`·`idx_requester_user_id_id`로
 * 받쳐져 뒤 페이지에서도 seek로 끝난다(offset 스캔·filesort 없음).
 */
@Component
class GetLoungeChatRequestDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetLoungeChatRequestDao {

	override fun findReceivedPage(receiverUserId: Long, beforeId: Long?, limit: Int): List<LoungeChatRequestView> {
		val request: QLoungeChatRequestEntity = QLoungeChatRequestEntity.loungeChatRequestEntity
		// 받은 신청이므로 상대방은 신청자다.
		return findPage(
			ownerColumn = request.receiverUserId,
			ownerUserId = receiverUserId,
			partnerColumn = request.requesterUserId,
			beforeId = beforeId,
			limit = limit,
		)
	}

	override fun findSentPage(requesterUserId: Long, beforeId: Long?, limit: Int): List<LoungeChatRequestView> {
		val request: QLoungeChatRequestEntity = QLoungeChatRequestEntity.loungeChatRequestEntity
		// 보낸 신청이므로 상대방은 글 작성자(수신자)다.
		return findPage(
			ownerColumn = request.requesterUserId,
			ownerUserId = requesterUserId,
			partnerColumn = request.receiverUserId,
			beforeId = beforeId,
			limit = limit,
		)
	}

	/**
	 * 신청 목록 한 페이지를 투영한다.
	 * [ownerColumn]은 조회 기준(내가 수신자냐 신청자냐), [partnerColumn]은 프로필을 붙일 상대방 컬럼이다.
	 */
	private fun findPage(
		ownerColumn: NumberPath<Long>,
		ownerUserId: Long,
		partnerColumn: NumberPath<Long>,
		beforeId: Long?,
		limit: Int,
	): List<LoungeChatRequestView> {
		val request: QLoungeChatRequestEntity = QLoungeChatRequestEntity.loungeChatRequestEntity
		val partnerDetail: QUserDetailEntity = QUserDetailEntity.userDetailEntity
		val partnerRegion: QRegionEntity = QRegionEntity.regionEntity
		val beforeCursor: BooleanExpression? = beforeId?.let { cursor: Long -> request.id.lt(cursor) }
		return queryFactory
			.select(
				Projections.constructor(
					LoungeChatRequestView::class.java,
					request.id,
					request.postId,
					partnerColumn,
					partnerDetail.nickname,
					partnerDetail.gender,
					partnerDetail.birthday,
					partnerDetail.profileImageCode,
					// 표시용 활동지역은 regions를 join해 "시/도 시/군/구"로 만든다. (지역 미설정이면 null)
					partnerRegion.sido.concat(" ").concat(partnerRegion.sigungu),
					partnerDetail.job,
					partnerDetail.companyName,
					request.status,
					request.createdAt,
				),
			)
			.from(request)
			.leftJoin(partnerDetail).on(partnerDetail.userId.eq(partnerColumn))
			.leftJoin(partnerRegion).on(partnerRegion.id.eq(partnerDetail.regionId))
			.where(ownerColumn.eq(ownerUserId), beforeCursor)
			.orderBy(request.id.desc())
			.limit(limit.toLong())
			.fetch()
	}
}
