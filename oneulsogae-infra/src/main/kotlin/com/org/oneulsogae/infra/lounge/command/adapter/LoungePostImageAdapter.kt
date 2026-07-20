package com.org.oneulsogae.infra.lounge.command.adapter

import com.org.oneulsogae.core.lounge.command.application.port.out.SaveLoungePostImagePort
import com.org.oneulsogae.core.lounge.command.domain.LoungePostImage
import com.org.oneulsogae.core.lounge.command.domain.LoungePostImages
import com.org.oneulsogae.infra.lounge.command.entity.LoungePostImageEntity
import com.org.oneulsogae.infra.lounge.command.mapper.toDomain
import com.org.oneulsogae.infra.lounge.command.mapper.toEntity
import com.org.oneulsogae.infra.lounge.command.repository.LoungePostImageJpaRepository
import org.springframework.stereotype.Component

/** 라운지 글 사진 엔티티의 out-port 어댑터. (엔티티당 어댑터 하나) */
@Component
class LoungePostImageAdapter(
	private val loungePostImageJpaRepository: LoungePostImageJpaRepository,
) : SaveLoungePostImagePort {

	override fun saveAll(images: LoungePostImages): LoungePostImages =
		LoungePostImages(
			loungePostImageJpaRepository
				.saveAll(images.values.map { image: LoungePostImage -> image.toEntity() })
				.map { entity: LoungePostImageEntity -> entity.toDomain() },
		)
}
