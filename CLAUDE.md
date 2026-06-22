# CLAUDE.md

`meeple-backend` — **헥사고날 아키텍처(Ports & Adapters)** 기반 Kotlin / Spring Boot 멀티모듈 백엔드.

- **언어/런타임**: Kotlin 2.2.21, JVM 21
- **프레임워크**: Spring Boot 4.0.6, Spring Data JPA, Spring Security + OAuth2 Client, JJWT
- **DB**: MySQL · **빌드**: Gradle 멀티모듈 + `buildSrc` convention plugin `meeple.kotlin-conventions`

> 이 파일은 **항상 지켜야 할 규칙의 요약 인덱스**다. 배경·예시·트리/표 등 상세는 `docs/architecture/`로 분리했다. 규칙을 적용할 때 해당 링크를 참고한다.

## 작업 원칙 (Karpathy 가이드라인)

LLM의 일반적인 코딩 실수를 줄이기 위한 행동 지침이다. 프로젝트별 지침이 있을 경우 본 가이드라인과 병합하여 사용한다.

트레이드오프: 본 지침은 속도보다 신중함에 우선순위를 둔다. 사소한 작업은 상황에 맞게 판단한다.

### 1. 구현 전 사고 (Think Before Coding)

가정하지 않는다. 모호함을 숨기지 않는다. 트레이드오프를 명확히 밝힌다.

구현을 시작하기 전 다음을 준수한다:

- 자신의 가정을 명시적으로 기술한다. 불확실한 경우 질문한다.
- 해석의 여지가 여러 가지라면 임의로 선택하지 말고 대안들을 제시한다.
- 더 간단한 접근 방식이 있다면 제안한다. 정당한 사유가 있다면 사용자의 요청에 반대 의견을 제시한다.
- 불분명한 부분이 있다면 작업을 중단한다. 혼란스러운 부분을 구체적으로 언급하며 질문한다.

### 2. 단순성 우선 (Simplicity First)

- 문제를 해결하는 최소한의 코드만 작성한다. 추측에 기반한 코드는 배제한다.
- 요청되지 않은 기능은 추가하지 않는다.
- 일회성 코드를 위해 추상화 계층을 만들지 않는다.
- 요청되지 않은 유연성이나 설정 가능성을 고려하지 않는다.
- 발생 불가능한 시나리오에 대한 예외 처리를 하지 않는다.
- 200줄의 코드를 50줄로 줄일 수 있다면 코드를 다시 작성한다.
- "시니어 엔지니어가 보기에 이 코드가 지나치게 복잡한가?"라고 자문한다. 그렇다면 단순화한다.

### 3. 정밀한 수정 (Surgical Changes)

필요한 부분만 수정한다. 본인이 만든 코드의 뒷정리만 수행한다.

기존 코드를 편집할 때 다음을 준수한다:

- 인접한 코드, 주석, 포맷을 임의로 개선하지 않는다.
- 망가지지 않은 부분을 리팩토링하지 않는다.
- 본인의 스타일과 다르더라도 기존 스타일을 따른다.
- 작업과 무관한 데드 코드를 발견하면 보고하되 직접 삭제하지 않는다.

수정으로 인해 사용되지 않게 된 요소가 발생할 경우:

- 본인의 수정으로 인해 불필요해진 임포트, 변수, 함수는 제거한다.
- 기존에 존재하던 데드 코드는 요청이 없는 한 그대로 둔다.
- 테스트 기준: 변경된 모든 라인은 사용자의 요청사항과 직접적으로 연결되어야 한다.

### 4. 목표 중심 실행 (Goal-Driven Execution)

성공 기준을 정의한다. 검증될 때까지 반복한다. 작업을 검증 가능한 목표로 변환한다:

- "유효성 검사 추가" → "잘못된 입력에 대한 테스트 작성 후 통과 확인"
- "버그 수정" → "버그를 재현하는 테스트 작성 후 통과 확인"
- "X 리팩토링" → "리팩토링 전후의 테스트 통과 확인"

다단계 작업의 경우 간략한 계획을 수립한다:

1. [단계] → 검증: [확인 사항]
2. [단계] → 검증: [확인 사항]
3. [단계] → 검증: [확인 사항]

성공 기준이 명확해야 독립적인 작업이 가능하다. "작동하게 만들기"와 같은 모호한 기준은 불필요한 재질의를 야기한다.

지침 작동 확인: Diff 내 불필요한 변경 감소, 복잡성으로 인한 재작성 빈도 감소, 구현 전 질문을 통한 명확한 의사결정 증대.

출처: https://americanopeople.tistory.com/514 [복세편살:티스토리]

## Git 커밋

- **세션마다 커밋한다.** 한 세션에서 작업이 끝나면 그 세션의 변경을 커밋하고 마무리한다(미커밋 상태로 두지 않는다).
- 한 세션이 논리적으로 별개인 변경을 섞었다면(기능·버그픽스·문서·설정 등) 각각 별도 커밋으로 나눈다. 이전 세션에서 남은(이번 세션과 무관한) 미커밋 변경은 임의로 합치지 말고 사용자에게 확인한다.

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
- **쿼리를 작성·수정할 때는 항상 인덱스 효율을 고려한다.** 가장 선택적인 `where` 동등 조건과 `order by` 컬럼이 인덱스로 받쳐지는지(테이블 풀스캔/filesort가 아닌 seek) 확인한다. 받쳐줄 인덱스가 없으면 복합 인덱스 추가를 검토하고(동등 조건 컬럼 → 정렬 컬럼 순서), 쓰기 비용·실DB DDL 반영까지 함께 판단한다. (`<>`·`like '%…'` 등 non-sargable 조건은 인덱스 seek에 쓰이지 않으므로 선택적 필터로 의존하지 않는다)
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
