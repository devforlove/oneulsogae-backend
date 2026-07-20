package com.org.oneulsogae.core.lounge.command.application.port.`in`

import com.org.oneulsogae.core.lounge.command.application.port.`in`.command.RegisterSelfIntroPostCommand
import com.org.oneulsogae.core.lounge.command.application.port.`in`.result.RegisterSelfIntroPostResult

/** 셀프 소개팅(셀소) 글 등록 유스케이스. */
interface RegisterSelfIntroPostUseCase {

	fun register(userId: Long, command: RegisterSelfIntroPostCommand): RegisterSelfIntroPostResult
}
