package com.org.oneulsogae.admin.companyverification.command.application.port.out

/**
 * 매칭 읽기 모델(match_user)의 회사명을 갱신하는 out-port.
 * (승인으로 유저 회사명이 바뀌면 같은-회사 소개 차단이 스테일해지지 않도록 match_user도 맞춘다. 행이 없으면 no-op)
 */
fun interface UpdateMatchUserCompanyNamePort {

	fun updateCompanyName(userId: Long, companyName: String)
}
