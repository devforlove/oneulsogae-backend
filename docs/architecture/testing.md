# 테스트 코드 전략

> CLAUDE.md의 테스트 요약에 대한 상세 레퍼런스 + 규약.

레이어별로 테스트 대상과 방식을 구분한다.

- **도메인 모델(`meeple-core`의 `domain`) → 유닛 테스트 (Kotest)**
  순수 비즈니스 로직(검증 함수, 상태 전이, 일급 컬렉션 동작 등)을 프레임워크·인프라 없이 검증한다.
  포트는 손수 만든 fake/stub으로 대체하고, 시각 등 외부 의존은 파라미터로 주입한다. (예: `Match.respond`, `User.validateRegistered`, `ChatRoom`)
- **`meeple-api` → E2E 테스트**
  실제 서버를 띄우고 HTTP 엔드포인트를 호출해 컨트롤러~서비스~영속성~외부연동까지 전 구간을 검증한다.

**변경 시 규칙**: 코드에 새로운 변경 사항이 생기면, 위 레이어 중 **변경된 지점에 해당하는 테스트 코드를 함께 작성한다.**
(도메인 모델이 바뀌면 그 도메인 유닛 테스트를, api 경계(엔드포인트/요청·응답)가 바뀌면 그 엔드포인트 E2E 테스트를 추가·갱신한다.)

## 도메인 유닛 테스트 작성 규약

- **프레임워크**: Kotest [`DescribeSpec`]. E2E와 동일하게 `describe`/`it` BDD 스타일로 작성한다. (예: `ChatRoomTest`)
  순수 도메인 검증이라 Spring 컨텍스트(`SpringExtension`)는 띄우지 않는다. 베이스 클래스 없이 `class XxxTest : DescribeSpec({ ... })`로 둔다.
- **어서션**: JUnit/`kotlin-test`가 아니라 Kotest 매처(`io.kotest.matchers.shouldBe`)와 `io.kotest.assertions.throwables.shouldThrow`를 쓴다.
  도메인 예외는 `val ex = shouldThrow<BusinessException> { ... }; ex.errorCode shouldBe XxxErrorCode.YYY` 형태로 검증한다.
- **외부 의존**: 시각 등은 테스트에서 고정값(`LocalDateTime.of(...)`)을 만들어 도메인 함수에 파라미터로 주입한다. (`TimeGenerator` 등 인프라를 띄우지 않는다)
- **테스트 의존성 배치**: Kotest(`kotest-runner-junit5`, `kotest-assertions-core`)를 `meeple-core`의 `testImplementation`에 둔다.
  E2E와 같은 버전(5.9.x)으로 고정하고, Spring 연동(`kotest-extensions-spring`)은 추가하지 않는다.

## E2E 테스트 작성 규약

- **프레임워크**: Kotest [`DescribeSpec`] + Spring(`SpringExtension`). 공통 베이스 `AbstractIntegrationSupport`를 상속한다.
  주입 빈을 셋업에 써야 하면 `@Autowired` 필드 + `init { describe { ... } }` 스타일로 작성한다.
  (Kotest 생성자 람다 안에서는 하위 클래스 필드에 접근할 수 없다.)
- **베이스(`AbstractIntegrationSupport`)가 제공하는 것**: `@SpringBootTest(RANDOM_PORT)` 실서버 + 컨텍스트 캐싱, Testcontainers
  MySQL/Redis(`@ServiceConnection`), WireMock 빈, RestAssured 포트 연결, 인증 토큰 발급(`accessTokenFor(userId)`).
- **데이터 준비/조회/정리**: 리포지토리에 직접 의존하지 말고 `IntegrationUtil`(infra `testFixtures`)을 쓴다.
  저장은 `persist(entity)`, 조회는 `getQuery()`(QueryDSL), 정리는 `deleteAll(QXxxEntity.xxx)`.
- **엔티티 준비**: 합리적 기본값을 가진 엔티티 픽스처(`XxxEntityFixture.create(...)`, infra `testFixtures`)를 사용한다.
  엔티티는 infra 소유이므로 픽스처도 infra `testFixtures`에 둔다. (core는 infra에 의존할 수 없다)
- **HTTP 호출/검증**: RestAssured 보일러플레이트 대신 `post(path) { ... } expect { ... }` Kotlin DSL(`RestAssuredDsl`)을 쓴다.
- **테스트 의존성 배치**: 통합/E2E 관련 의존성(Testcontainers, WireMock, Kotest, RestAssured)은 `meeple-api`에 둔다.
  `IntegrationUtil`·픽스처는 `meeple-infra`의 `testFixtures`로 제공하고, api가 `testImplementation(testFixtures(project(":meeple-infra")))`로 가져온다.
