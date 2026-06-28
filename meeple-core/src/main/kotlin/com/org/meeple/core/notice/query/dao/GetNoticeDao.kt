package com.org.meeple.core.notice.query.dao

import com.org.meeple.core.notice.query.dto.NoticeViews

/**
 * 공지 조회 dao(query out-port 인터페이스). (조회 전용 read model 반환)
 * 일급 컬렉션([NoticeViews])으로 반환하며, 실제 구현은 infra 레이어의 dao가 담당한다.
 */
interface GetNoticeDao {

	/** 공지를 저장 날짜(생성 시각) 최신순으로 [offset]부터 [limit]건 조회한다. (없으면 빈 [NoticeViews]) */
	fun findPage(offset: Long, limit: Int): NoticeViews

	/** (soft delete 제외) 전체 공지 개수. (페이징 메타데이터 계산용) */
	fun count(): Long
}
