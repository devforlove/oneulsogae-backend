package com.org.oneulsogae.common.integration

import com.github.tomakehurst.wiremock.WireMockServer
import com.org.oneulsogae.OneulsogaeApiApplication
import com.org.oneulsogae.auth.PrincipalDetails
import com.org.oneulsogae.auth.jwt.TokenProvider
import com.org.oneulsogae.common.config.TestDatabaseContainersConfig
import com.org.oneulsogae.common.config.TestFileStorageConfig
import com.org.oneulsogae.common.config.FakePaymentGateway
import com.org.oneulsogae.common.config.TestKcpConfig
import com.org.oneulsogae.common.config.TestPaymentGatewayConfig
import com.org.oneulsogae.common.config.TestRedisContainersConfig
import com.org.oneulsogae.common.config.TestRegionShufflerConfig
import com.org.oneulsogae.common.config.TestWireMockConfig
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import io.restassured.RestAssured
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority

/**
 * 통합테스트 공통 베이스 (Kotest [DescribeSpec] 기반).
 *
 * - **컨텍스트 재사용**: `@SpringBootTest(classes = [OneulsogaeApiApplication])` 설정을 베이스에 고정해,
 *   Spring의 컨텍스트 캐싱이 동일 설정의 컨텍스트를 1회만 생성·재사용한다.
 * - **인프라 컨테이너**: MySQL/Redis 컨테이너를 `@ServiceConnection` 빈([TestDatabaseContainersConfig],
 *   [TestRedisContainersConfig])으로 `@Import` 한다. `spring-boot-testcontainers`가 컨텍스트 기동 시
 *   컨테이너를 자동 start/stop 하고 접속 정보를 datasource/redis 프로퍼티로 연결한다.
 * - **WireMock**: [TestWireMockConfig]의 [WireMockServer] 빈을 주입받아 사용하고, 테스트마다 자동 리셋한다.
 * - **DB 정리**: infra testFixtures의 [IntegrationUtil]을 `@Import`로 등록한다. 정리는 각 테스트에서
 *   `IntegrationUtil.deleteAll(QXxxEntity.xxx)`로 명시적으로 수행한다.
 * - **E2E**: `webEnvironment = RANDOM_PORT`로 실제 서버를 띄우고, 주입받은 [LocalServerPort]를 RestAssured에
 *   연결한다. 테스트는 RestAssured DSL(`given()/when()/then()`)로 실 HTTP 엔드포인트를 호출한다.
 * - **Kotest 연동**: [SpringExtension]을 등록해 스펙 인스턴스에 Spring 빈이 주입되도록 한다.
 *
 * 사용 예:
 * - 주입 빈이 필요 없으면 생성자 람다로 간단히 작성할 수 있다.
 *   ```
 *   class FooE2ETest : AbstractIntegrationSupport({
 *       describe("GET /foo") { it("200") { RestAssured.given().get("/foo").then().statusCode(200) } }
 *   })
 *   ```
 * - 리포지토리 등 주입 빈을 셋업에 써야 하면 `@Autowired` 필드 + `init { describe { ... } }` 스타일로 작성한다.
 *   (Kotest 생성자 람다 안에서는 하위 클래스 필드에 접근할 수 없으므로 `init` 블록을 쓴다.)
 *   ```
 *   class FooE2ETest : AbstractIntegrationSupport() {
 *       @Autowired private lateinit var repo: FooRepository
 *       init { describe("...") { it("...") { repo.save(...) } } }
 *   }
 *   ```
 */
@SpringBootTest(
	classes = [OneulsogaeApiApplication::class],
	webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
	properties = ["spring.profiles.active=test"],
)
@Import(
	TestDatabaseContainersConfig::class,
	TestRedisContainersConfig::class,
	TestWireMockConfig::class,
	TestRegionShufflerConfig::class,
	TestFileStorageConfig::class,
	TestKcpConfig::class,
	TestPaymentGatewayConfig::class,
	IntegrationUtil::class,
)
abstract class AbstractIntegrationSupport(
	// 스펙 본문 람다의 리시버를 이 베이스로 둬, 생성자 람다 안에서 describe/afterTest 같은 DSL뿐 아니라
	// accessTokenFor 등 베이스 헬퍼도 바로 쓸 수 있게 한다. (생성자 주입 + 람다 스타일 통합테스트 작성용)
	body: AbstractIntegrationSupport.() -> Unit = {},
) : DescribeSpec() {

	@Autowired
	protected lateinit var wireMock: WireMockServer

	@Autowired
	protected lateinit var tokenProvider: TokenProvider

	@LocalServerPort
	protected var port: Int = 0

	init {
		extension(SpringExtension)
		// 실제 기동된 랜덤 포트를 RestAssured에 연결한다.
		beforeEach { RestAssured.port = port }
		// 테스트 사이 WireMock 스텁/요청 기록 초기화. (DB 정리는 각 테스트의 afterTest에서 IntegrationUtil.deleteAll로 수행)
		afterEach { wireMock.resetAll() }
		// 테스트 사이 PG 페이크 결과를 기본(승인)으로 되돌린다. 실패 케이스를 세팅한 테스트가 다음 테스트로 새지 않게 한다.
		afterEach { FakePaymentGateway.result = FakePaymentGateway.APPROVED }
		// 생성자 람다 본문(describe/afterTest 등)을 이 인스턴스를 리시버로 실행한다.
		body()
	}

	/**
	 * 인증된 요청에 쓸 accessToken을 발급한다. (모든 E2E 공통)
	 * [TokenProvider] 빈에 의존하므로 정적 DSL이 아니라 이 베이스에 둔다. RestAssured DSL과 함께
	 * `bearer(accessTokenFor(userId))` 형태로 사용한다.
	 */
	// internal: 생성자 람다(리시버=베이스)에서도 호출할 수 있도록 protected가 아닌 internal로 둔다. (테스트 모듈 한정)
	internal fun accessTokenFor(userId: Long, email: String = "user$userId@test.com"): String =
		tokenFor(userId, email, "ROLE_USER")

	/** ROLE_ADMIN 권한의 accessToken을 발급한다. (어드민 인가 규칙 검증용) */
	internal fun adminAccessTokenFor(userId: Long, email: String = "admin$userId@test.com"): String =
		tokenFor(userId, email, "ROLE_ADMIN")

	private fun tokenFor(userId: Long, email: String, role: String): String {
		val authorities: List<SimpleGrantedAuthority> = listOf(SimpleGrantedAuthority(role))
		val principal = PrincipalDetails(email = email, id = userId, authorities = authorities)
		return tokenProvider.generateAccessToken(UsernamePasswordAuthenticationToken(principal, "", authorities))
	}
}
