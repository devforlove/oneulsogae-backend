package com.org.oneulsogae.infra.notice.command.entity

import com.org.oneulsogae.infra.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction

/**
 * 앱에 노출하는 공지사항 영속성 엔티티. 제목(title)과 설명(description)을 보관한다.
 * 저장 날짜는 별도 컬럼 없이 [BaseEntity]의 created_at(생성 시각, JPA Auditing)으로 갈음한다.
 * 삭제는 soft delete(deleted_at)로 처리한다. (@SQLRestriction으로 삭제 행은 조회에서 제외)
 */
@Entity
@SQLRestriction("deleted_at is null")
@Table(name = "notices")
class NoticeEntity(
	@Column(name = "title", nullable = false, length = 200)
	var title: String,

	@Column(name = "description", nullable = false, length = 2000)
	var description: String,
) : BaseEntity()
