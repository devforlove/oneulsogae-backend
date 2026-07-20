package com.org.oneulsogae.auth

/**
 * 컨트롤러 파라미터에 붙여 현재 로그인 사용자를 [AuthUser]로 주입받는다.
 *
 * ```
 * @GetMapping("/me")
 * fun me(@LoginUser user: AuthUser) = user.id
 * ```
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class LoginUser
