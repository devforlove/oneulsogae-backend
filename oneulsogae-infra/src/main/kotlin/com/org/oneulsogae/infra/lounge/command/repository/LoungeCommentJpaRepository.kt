package com.org.oneulsogae.infra.lounge.command.repository

import com.org.oneulsogae.infra.lounge.command.entity.LoungeCommentEntity
import org.springframework.data.jpa.repository.JpaRepository

/** 라운지 댓글 JPA 리포지토리. */
interface LoungeCommentJpaRepository : JpaRepository<LoungeCommentEntity, Long>
