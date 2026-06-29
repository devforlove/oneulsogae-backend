package com.org.meeple.infra.image.repository

import com.org.meeple.infra.image.entity.ImageTemplateEntity
import org.springframework.data.jpa.repository.JpaRepository

interface ImageTemplateJpaRepository : JpaRepository<ImageTemplateEntity, Long>
