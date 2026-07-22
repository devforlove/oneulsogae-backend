package com.org.oneulsogae.infra.user.command.adapter

/**
 * 인증번호 메일 본문 템플릿. 회사/학교 어댑터가 대상 명칭([kind])만 달리해 사용한다.
 * HTML은 이메일 클라이언트 호환을 위해 테이블 레이아웃 + 인라인 스타일만 쓴다(외부 CSS·이미지 없음).
 */
object VerificationMailTemplate {

	fun subject(kind: String): String = "[오늘의 소개] $kind 이메일 인증번호"

	fun text(kind: String, code: String): String = """
		|인증번호: $code
		|
		|오늘의 소개에서 요청하신 $kind 이메일 인증번호입니다.
		|10분 안에 화면에 입력해 주세요.
		|
		|본인이 요청하지 않았다면 이 메일을 무시하셔도 됩니다.
	""".trimMargin()

	fun html(kind: String, code: String): String = """
		|<!DOCTYPE html>
		|<html lang="ko">
		|<body style="margin:0; padding:0; background-color:#f4f4f6;">
		|<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background-color:#f4f4f6; padding:32px 16px;">
		|  <tr>
		|    <td align="center">
		|      <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="max-width:480px; background-color:#ffffff; border-radius:12px; overflow:hidden;">
		|        <tr>
		|          <td style="padding:28px 32px 0 32px; font-family:'Apple SD Gothic Neo','Malgun Gothic',sans-serif;">
		|            <p style="margin:0; font-size:15px; font-weight:700; color:#ff6b5b;">오늘의 소개</p>
		|          </td>
		|        </tr>
		|        <tr>
		|          <td style="padding:20px 32px 0 32px; font-family:'Apple SD Gothic Neo','Malgun Gothic',sans-serif;">
		|            <p style="margin:0; font-size:20px; font-weight:700; color:#1a1a1a;">$kind 이메일 인증번호</p>
		|            <p style="margin:12px 0 0 0; font-size:14px; line-height:1.6; color:#555555;">
		|              오늘의 소개에서 요청하신 $kind 이메일 인증번호입니다.<br>10분 안에 화면에 입력해 주세요.
		|            </p>
		|          </td>
		|        </tr>
		|        <tr>
		|          <td style="padding:24px 32px 0 32px;">
		|            <table role="presentation" width="100%" cellpadding="0" cellspacing="0">
		|              <tr>
		|                <td align="center" style="background-color:#f7f7f9; border-radius:8px; padding:20px 0; font-family:'Apple SD Gothic Neo','Malgun Gothic',sans-serif;">
		|                  <span style="font-size:32px; font-weight:700; letter-spacing:8px; color:#1a1a1a;">$code</span>
		|                </td>
		|              </tr>
		|            </table>
		|          </td>
		|        </tr>
		|        <tr>
		|          <td style="padding:24px 32px 28px 32px; font-family:'Apple SD Gothic Neo','Malgun Gothic',sans-serif;">
		|            <p style="margin:0; font-size:12px; line-height:1.6; color:#999999;">
		|              본인이 요청하지 않았다면 이 메일을 무시하셔도 됩니다.<br>이 메일은 발신 전용입니다.
		|            </p>
		|          </td>
		|        </tr>
		|      </table>
		|    </td>
		|  </tr>
		|</table>
		|</body>
		|</html>
	""".trimMargin()
}
