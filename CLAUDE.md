# CLAUDE.md

`meeple-backend` — **헥사고날 아키텍처(Ports & Adapters)** 기반 Kotlin / Spring Boot 멀티모듈 백엔드.

- **언어/런타임**: Kotlin 2.2.21, JVM 21
- **프레임워크**: Spring Boot 4.0.6, Spring Data JPA, Spring Security + OAuth2 Client, JJWT
- **DB**: MySQL · **빌드**: Gradle 멀티모듈 + `buildSrc` convention plugin `meeple.kotlin-conventions`

> 이 파일은 **항상 지켜야 할 규칙의 요약 인덱스**다. 배경·예시·트리/표 등 상세는 `docs/architecture/`로 분리했다. 규칙을 적용할 때 해당 링크를 참고한다.

## 모듈 의존 방향  ([상세](docs/architecture/modules.md))

```
meeple-api ──> common, core, infra, chatting, scheduler, auth   (유일한 @SpringBootApplication. Controller/인증 + 배치 스케줄 구동)
meeple-chatting ──> common, auth        (WebSocket 자립 모듈. core에 의존하지 않음 — 자체 도메인/포트 보유)
meeple-scheduler ──> common             (배치 로직 + 자체 포트. core에 의존하지 않는 라이브러리)
meeple-core ──> common                  (도메인/유스케이스/포트 = 비즈니스 핵심. 인프라 세부에 비의존)
meeple-infra ──> common, core, scheduler, chatting   (JPA 엔티티/Repository/Adapter. out-port 구현)
meeple-auth ──> (spring-security, jjwt) (JWT 검증 커널: TokenProvider/PrincipalDetails. 발급/로그인은 api)
meeple-common ──> (없음)                (공용 enum/상수)
```

- 영속성 구현은 `meeple-infra`의 Adapter가 core/scheduler/chatting의 **out-port를 구현**한다.
- HTTP 경계(Controller / 요청·응답 DTO)는 `meeple-api`에만, 공용 enum/상수는 `meeple-common`에 둔다.
- `meeple-scheduler`(배치)·`meeple-chatting`(WebSocket)은 **core에 의존하지 않는 자립 모듈**이다. infra 어댑터가 core 도메인에 위임해 잇는다.
- 인증 검증(`TokenProvider`/`PrincipalDetails`)은 `meeple-auth`, 발급·OAuth2 로그인·`SecurityConfig`는 `meeple-api`.

## 헥사고날 레이어링  ([상세](docs/architecture/hexagonal-cqrs.md))

도메인 표준 구조: `<domain>/{domain, application/{<Verb><Noun>Service, port/{in(+command,+result), out}}}` + `<Domain>ErrorCode`.

- **Controller**는 Service가 아니라 **in-port `UseCase` 인터페이스**를 주입한다.
- **Service**(`@Service`+`@Transactional`)가 UseCase를 구현하고 포트를 주입받는다. **Adapter**(`@Component`, infra)가 out-port를 구현하고 `toDomain()`/`toEntity()`로 변환한다.
- **네이밍**: `<동사><명사>UseCase` / `<동사><명사>Service`. **에러**: 도메인별 `ErrorCode` enum + `BusinessException`.
- **`user` 도메인이 사용자 프로필(`UserDetail`/`UserDetailView` 등)을 소유한다.** 여러 도메인이 읽는 공유 데이터이므로 match 등에 두지 않는다.

## 도메인 간 참조  ([상세](docs/architecture/hexagonal-cqrs.md#도메인-간-참조-규칙))

- 다른 도메인의 데이터·동작이 필요하면 **그 도메인의 in-port `UseCase`를 주입**한다. 다른 도메인의 out-port·Service 구현체를 직접 주입하지 않는다.
- 자기 도메인 내부 영속성 접근은 **자기 도메인의 out-port**를 쓴다.
- 표시용 프로필 조인은 infra 읽기 어댑터가 `UserDetailEntity`를 조인해 자기 도메인 read model로 투영한다.

## 명령·조회 분리 (CQS / CQRS)  ([상세](docs/architecture/hexagonal-cqrs.md#명령조회-분리-cqs-command-query-separation))

- **명령(쓰기)과 조회(읽기)를 메서드·포트·서비스·트랜잭션 단위로 분리**한다. 조회 경로는 부수효과가 없다(저장·상태변경 포트 호출 금지).
- 조회 서비스 `@Transactional(readOnly = true)` / 명령 서비스 `@Transactional`. 아웃포트도 `Get…Port`/`Save…Port`를 한 포트에 섞지 않는다.
- 조회는 도메인 모델 대신 **전용 read model(DTO/프로젝션)**을 반환한다. 명령은 도메인 모델을 다룬다.
- **CQRS 패키지 분리**: `chat`·`user`·`match`·`coin`·`scheduler`는 `command`/`query` 패키지로 한 단계 더 나눈다.
  - command: `command/application`(chat은 `command/service`) + `port/{in,out}` + `command/domain`.
  - query: `query/{service(+port/in), dao(*Dao), dto(read model)}`. **query는 자기 dao에만 의존**하고 command 도메인·포트를 참조하지 않는다.
  - 같은 단건 조회가 command·query 양쪽에 있어도 공유하지 않고 각자 구현한다. (예: coin command `GetCoinBalancePort`(잠금) ↔ query `CoinBalanceDao`)

## 영속성 어댑터  ([상세](docs/architecture/hexagonal-cqrs.md#영속성-어댑터-구성-엔티티-단위-querydsl-분리))

- **엔티티마다 어댑터 하나.** 여러 모듈(core·scheduler·chatting)이 같은 엔티티를 써도 모듈별로 쪼개지 않고 한 어댑터에서 각 out-port를 함께 구현한다. (단순명 겹치면 import alias)
- CQRS 도메인(chat·match·coin): command out-port는 `command/adapter`의 `*Adapter`(Spring Data 메서드 쿼리), 조회 dao는 `query`의 `*DaoImpl`로 분리한다.
- 조회 구현 우선순위: ① Spring Data 파생 쿼리 → ② QueryDSL(`JPAQueryFactory`만 주입, 조인·동적 컬럼) → ③ `@Query`(JPQL).
- `@Query`(JPQL) 조인은 **콤마 암묵 조인 금지, `join … on` 명시 조인**(조인 조건은 `on`, 필터만 `where`).
- `entity`·`mapper`·`repository`는 `command` 아래 두고 query daoImpl이 참조한다(infra 내부 query→command 참조 허용).

## 코딩 원칙  ([상세](docs/architecture/coding-conventions.md))

- **타입 명시**: 변수·반환 타입·람다 파라미터 타입을 생략하지 않는다(표현식 본문 함수 포함).
- **도메인 검증**: 서비스에 `if…throw`를 나열하지 말고 도메인 모델의 `validate<대상>(…)` 함수로 캡슐화한다(필요 입력·`now`는 파라미터 주입).
- **현재 시각**: `LocalDateTime.now()`를 직접 호출하지 않는다. 애플리케이션/도메인은 `TimeGenerator`(core `common/time`, 모듈별 out-port도 존재)를 주입받아 `now()`로 얻고 도메인엔 파라미터로 넘긴다(테스트에서 시각 고정). 직접 호출은 `SystemTimeGenerator` 구현체·엔티티 픽스처에 한정한다.
- **일급 컬렉션**: 컬렉션은 원시 `List`/`Set` 대신 `Xs`(복수형) 래퍼로 반환한다(`values` 보관 + 컬렉션 동작 응집).

## 테스트 전략  ([상세](docs/architecture/testing.md))

- **도메인 모델 → Kotest 유닛 테스트**(프레임워크·인프라 없이, 외부 의존은 파라미터 주입). **`meeple-api` → E2E 테스트**(실서버 + Testcontainers).
- **변경 시**: 변경된 레이어에 해당하는 테스트를 함께 작성·갱신한다(도메인 모델→유닛, api 경계→E2E).
- E2E는 `AbstractIntegrationSupport` 상속 + `IntegrationUtil`/엔티티 픽스처(infra `testFixtures`) + `RestAssuredDsl` 사용. 리포지토리 직접 의존 금지.
