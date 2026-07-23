package com.org.oneulsogae.infra.image.repository

import com.org.oneulsogae.infra.image.entity.ImageTemplateEntity
import org.springframework.data.jpa.repository.JpaRepository

interface ImageTemplateJpaRepository : JpaRepository<ImageTemplateEntity, Long> {

	fun findByCode(code: String): ImageTemplateEntity?
}
