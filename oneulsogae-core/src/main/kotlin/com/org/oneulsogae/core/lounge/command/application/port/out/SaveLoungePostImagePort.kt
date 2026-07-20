package com.org.oneulsogae.core.lounge.command.application.port.out

import com.org.oneulsogae.core.lounge.command.domain.LoungePostImages

/** 라운지 글 사진 저장 out-port. */
interface SaveLoungePostImagePort {

	fun saveAll(images: LoungePostImages): LoungePostImages
}
