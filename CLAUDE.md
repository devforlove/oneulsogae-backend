# CLAUDE.md

`oneulsogae-backend` — **헥사고날 아키텍처(Ports & Adapters)** 기반 Kotlin / Spring Boot 멀티모듈 백엔드.

- **언어/런타임**: Kotlin 2.2.21, JVM 21
- **프레임워크**: Spring Boot 4.0.6, Spring Data JPA, Spring Security + OAuth2 Client, JJWT
- **DB**: MySQL · **빌드**: Gradle 멀티모듈 + `buildSrc` convention plugin `oneulsogae.kotlin-conventions`

> 이 파일 = **항상 지킬 규칙 요약 인덱스**. 배경·예시·트리/표 상세는 `docs/architecture/` 참고.

## 응답 언어

- **항상 한국어.**

## 리포지토리 경계

- **`oneulsogae-backend`만 수정.** `oneulsogae-backend` 작업 중 `meeple-frontend` 손대지 않음. (백엔드 변경 딸린 프론트 변경도 자동 수행 금지)
- 프론트 변경 필요하면 직접 고치지 말고 **무엇을 어떻게 바꿀지 사용자에게 안내.** (예: 응답 필드 추가/리네임 시 대응 DTO·매퍼 위치와 변경 내용)

## 작업 원칙 (Karpathy 가이드라인)

LLM 흔한 코딩 실수 줄이는 지침. 프로젝트별 지침 있으면 병합.

트레이드오프: 속도보다 신중함 우선. 사소한 작업은 상황 판단.

### 1. 구현 전 사고 (Think Before Coding)

가정 금지. 모호함 숨기지 않기. 트레이드오프 명시.

구현 전:

- 가정 명시적으로 기술. 불확실하면 질문.
- 해석 여러 개면 임의 선택 말고 대안 제시.
- 더 간단한 접근 있으면 제안. 정당한 사유 있으면 사용자 요청에 반대 의견 제시.
- 불분명하면 중단. 혼란 지점 구체적으로 질문.

### 2. 단순성 우선 (Simplicity First)

- 문제 해결 최소 코드만. 추측 기반 코드 배제.
- 요청 안 된 기능 추가 금지.
- 일회성 코드에 추상화 계층 금지.
- 요청 안 된 유연성·설정 가능성 금지.
- 발생 불가 시나리오 예외 처리 금지.
- 200줄이 50줄로 줄면 재작성.
- "시니어 엔지니어가 보기에 과복잡?" 자문. 그렇다면 단순화.

### 3. 정밀한 수정 (Surgical Changes)

필요한 부분만 수정. 본인 코드 뒷정리만.

기존 코드 편집 시:

- 인접 코드·주석·포맷 임의 개선 금지.
- 안 망가진 부분 리팩토링 금지.
- 본인 스타일과 달라도 기존 스타일 따름.
- 무관한 데드 코드 발견 시 보고만, 직접 삭제 금지.

수정으로 불필요해진 요소:

- 본인 수정 탓 불필요해진 임포트·변수·함수는 제거.
- 기존 데드 코드는 요청 없으면 유지.
- 기준: 변경 라인 전부 사용자 요청과 직결.

### 4. 목표 중심 실행 (Goal-Driven Execution)

성공 기준 정의. 검증까지 반복. 작업을 검증 가능 목표로 변환:

- "유효성 검사 추가" → "잘못된 입력 테스트 작성 후 통과 확인"
- "버그 수정" → "버그 재현 테스트 작성 후 통과 확인"
- "X 리팩토링" → "리팩토링 전후 테스트 통과 확인"

다단계 작업은 간략 계획 수립:

1. [단계] → 검증: [확인 사항]
2. [단계] → 검증: [확인 사항]
3. [단계] → 검증: [확인 사항]

성공 기준 명확해야 독립 작업 가능. "작동하게 만들기" 같은 모호한 기준은 불필요한 재질의 야기.

지침 작동 확인: Diff 내 불필요 변경 감소, 복잡성 재작성 감소, 구현 전 질문으로 의사결정 명확화.

출처: https://americanopeople.tistory.com/514 [복세편살:티스토리]

## Git 커밋

- **세션마다 커밋.** 세션 작업 끝나면 커밋 후 마무리(미커밋 상태 금지).
- 논리적으로 별개 변경 섞였으면(기능·버그픽스·문서·설정 등) 별도 커밋으로 분리. 이전 세션의 무관한 미커밋 변경은 임의 합치지 말고 사용자 확인.
- **메시지 형식**: `<type>(<domain>): <설명>`. 괄호에 변경 속한 **도메인**(`match`·`user`·`coin`·`chat`·`alarm`·`scheduler` 등). 모호하거나 전역(빌드·CI·루트 문서 등)이면 괄호 생략. 예: `fix(match): 매치 코드 수정`, `feat(alarm): 팀 초대 알람 추가`, `docs: 커밋 컨벤션 추가`.
- **타입**:
  - `feat`: 새 기능
  - `fix`: 버그 수정
  - `docs`: 문서 수정
  - `style`: 코드 스타일 변경 (포매팅, 세미콜론 누락 등)
  - `design`: 사용자 UI 디자인 변경 (CSS 등)
  - `test`: 테스트 코드, 리팩토링 (Test Code)
  - `refactor`: 리팩토링 (Production Code)
  - `build`: 빌드 파일 수정
  - `ci`: CI 설정 파일 수정
  - `perf`: 성능 개선
  - `chore`: 자잘한 수정·빌드 업데이트
  - `rename`: 파일/폴더명 수정만
  - `remove`: 파일 삭제만

## 모듈 의존 방향  ([상세](docs/architecture/modules.md))

```
oneulsogae-api ──> common, core, infra, chatting, scheduler, auth   (유일한 @SpringBootApplication. Controller/인증 + 배치 스케줄 구동)
oneulsogae-chatting ──> common, auth        (WebSocket 자립 모듈. core에 의존하지 않음 — 자체 도메인/포트 보유)
oneulsogae-scheduler ──> common             (배치 로직 + 자체 포트. core에 의존하지 않는 라이브러리)
oneulsogae-core ──> common                  (도메인/유스케이스/포트 = 비즈니스 핵심. 인프라 세부에 비의존)
oneulsogae-infra ──> common, core, scheduler, chatting   (JPA 엔티티/Repository/Adapter. out-port 구현)
oneulsogae-auth ──> (spring-security, jjwt) (JWT 검증 커널: TokenProvider/PrincipalDetails. 발급/로그인은 api)
oneulsogae-common ──> (없음)                (공용 enum/상수)
```

- 영속성 구현: `oneulsogae-infra` Adapter가 core/scheduler/chatting의 **out-port 구현**.
- HTTP 경계(Controller / 요청·응답 DTO)는 `oneulsogae-api`에만, 공용 enum/상수는 `oneulsogae-common`.
- `oneulsogae-scheduler`(배치)·`oneulsogae-chatting`(WebSocket)은 **core 비의존 자립 모듈**. infra 어댑터가 core 도메인에 위임해 연결.
- 인증 검증(`TokenProvider`/`PrincipalDetails`)은 `oneulsogae-auth`, 발급·OAuth2 로그인·`SecurityConfig`는 `oneulsogae-api`.

## 헥사고날 레이어링  ([상세](docs/architecture/hexagonal-cqrs.md))

도메인 표준 구조: `<domain>/{domain, application/{<Verb><Noun>Service, port/{in(+command,+result), out}}}` + `<Domain>ErrorCode`.

- **Controller**는 Service 아닌 **in-port `UseCase` 인터페이스** 주입.
- **Service**(`@Service`+`@Transactional`)가 UseCase 구현, 포트 주입. **Adapter**(`@Component`, infra)가 out-port 구현, `toDomain()`/`toEntity()` 변환.
- **네이밍**: `<동사><명사>UseCase` / `<동사><명사>Service`. **에러**: 도메인별 `ErrorCode` enum + `BusinessException`.
- **`user` 도메인이 사용자 프로필(`UserDetail`/`UserDetailView` 등) 소유.** 여러 도메인이 읽는 공유 데이터 — match 등에 두지 않음.

## 도메인 간 참조  ([상세](docs/architecture/hexagonal-cqrs.md#도메인-간-참조-규칙))

- 타 도메인 데이터·동작 필요하면 **그 도메인 in-port `UseCase` 주입**. 타 도메인 out-port·Service 구현체 직접 주입 금지.
- 자기 도메인 영속성 접근은 **자기 도메인 out-port**.
- 표시용 프로필 조인은 infra 읽기 어댑터가 `UserDetailEntity` 조인해 자기 도메인 read model로 투영.

## 명령·조회 분리 (CQS / CQRS)  ([상세](docs/architecture/hexagonal-cqrs.md#명령조회-분리-cqs-command-query-separation))

- **명령(쓰기)·조회(읽기)를 메서드·포트·서비스·트랜잭션 단위로 분리.** 조회 경로 부수효과 없음(저장·상태변경 포트 호출 금지).
- 조회 서비스 `@Transactional(readOnly = true)` / 명령 서비스 `@Transactional`. 아웃포트도 `Get…Port`/`Save…Port` 한 포트에 안 섞음.
- 조회는 도메인 모델 대신 **전용 read model(DTO/프로젝션)** 반환. 명령은 도메인 모델.
- **CQRS 패키지 분리**: `chat`·`user`·`solomatch`·`teammatch`·`coin`·`scheduler`는 `command`/`query` 패키지로 한 단계 더 분리. (`matchuser`는 읽기 모델 동기화 전용 — `command`만)
  - command: `command/application`(chat은 `command/service`) + `port/{in,out}` + `command/domain`.
  - query: `query/{service(+port/in), dao(*Dao), dto(read model)}`. **query는 자기 dao에만 의존**, command 도메인·포트 참조 금지.
  - 같은 단건 조회가 command·query 양쪽 있어도 공유 없이 각자 구현. (예: coin command `GetCoinBalancePort`(잠금) ↔ query `CoinBalanceDao`)

## 영속성 어댑터  ([상세](docs/architecture/hexagonal-cqrs.md#영속성-어댑터-구성-엔티티-단위-querydsl-분리))

- **엔티티마다 어댑터 하나.** 여러 모듈(core·scheduler·chatting)이 같은 엔티티 써도 모듈별로 안 쪼갬 — 한 어댑터에서 각 out-port 함께 구현. (단순명 겹치면 import alias)
- CQRS 도메인(chat·solomatch·teammatch·coin): command out-port는 `command/adapter`의 `*Adapter`(Spring Data 메서드 쿼리), 조회 dao는 `query`의 `*DaoImpl`로 분리.
- 조회 구현 우선순위: ① Spring Data 파생 쿼리 → ② QueryDSL(`JPAQueryFactory`만 주입, 조인·동적 컬럼) → ③ `@Query`(JPQL).
- **쿼리 작성·수정 시 항상 인덱스 효율 고려.** 가장 선택적인 `where` 동등 조건·`order by` 컬럼이 인덱스로 받쳐지는지(풀스캔/filesort 아닌 seek) 확인. 없으면 복합 인덱스 추가 검토(동등 조건 컬럼 → 정렬 컬럼 순), 쓰기 비용·실DB DDL 반영까지 판단. (`<>`·`like '%…'` 등 non-sargable 조건은 인덱스 seek 불가 — 선택적 필터로 의존 금지)
- `@Query`(JPQL) 조인은 **콤마 암묵 조인 금지, `join … on` 명시 조인**(조인 조건은 `on`, 필터만 `where`).
- `entity`·`mapper`·`repository`는 `command` 아래, query daoImpl이 참조(infra 내부 query→command 참조 허용).

## 코딩 원칙  ([상세](docs/architecture/coding-conventions.md))

- **타입 명시**: 변수·반환 타입·람다 파라미터 타입 생략 금지(표현식 본문 함수 포함).
- **도메인 검증**: 서비스에 `if…throw` 나열 금지 — 도메인 모델 `validate<대상>(…)` 함수로 캡슐화(필요 입력·`now`는 파라미터 주입).
- **도메인 로직 캡슐화**: 도메인 규칙(컬렉션 순회·상태 판정·집계 등) 서비스에 인라인 금지. 서비스가 `members.values.none { … }`처럼 일급 컬렉션 `values` 들춰 직접 계산 금지. 의미 있는 이름의 도메인 모델/일급 컬렉션 메서드(예: `allInactiveAfterLeaving(...)`)로 캡슐화, 서비스는 결과만 사용.
- **현재 시각**: `LocalDateTime.now()` 직접 호출 금지. 애플리케이션/도메인은 `TimeGenerator`(core `common/time`, 모듈별 out-port도 존재) 주입받아 `now()`로 얻고 도메인엔 파라미터로 전달(테스트 시각 고정). 직접 호출은 `SystemTimeGenerator` 구현체·엔티티 픽스처만.
- **일급 컬렉션**: 원시 `List`/`Set` 대신 `Xs`(복수형) 래퍼 반환(`values` 보관 + 컬렉션 동작 응집).

## 테스트 전략  ([상세](docs/architecture/testing.md))

- **도메인 모델 → Kotest 유닛 테스트**(프레임워크·인프라 없이, 외부 의존 파라미터 주입). **`oneulsogae-api` → E2E 테스트**(실서버 + Testcontainers).
- **변경 시**: 변경 레이어 해당 테스트 함께 작성·갱신(도메인 모델→유닛, api 경계→E2E).
- E2E는 `AbstractIntegrationSupport` 상속 + `IntegrationUtil`/엔티티 픽스처(infra `testFixtures`) + `RestAssuredDsl` 사용. 리포지토리 직접 의존 금지.