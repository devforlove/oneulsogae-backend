# CLAUDE.md

## 프로젝트 개요

`meeple-backend`는 **헥사고날 아키텍처(Ports & Adapters)** 를 따르는 Kotlin / Spring Boot 멀티모듈 백엔드다.

- **언어/런타임**: Kotlin 2.2.21, JVM 21
- **프레임워크**: Spring Boot 4.0.6, Spring Data JPA, Spring Security + OAuth2 Client, JJWT
- **DB**: MySQL
- **빌드**: Gradle 멀티모듈 + `buildSrc`의 convention plugin `meeple.kotlin-conventions`

## 모듈 구성 및 의존 방향

```
meeple-api ──> meeple-common, meeple-core, meeple-infra, meeple-chatting, meeple-scheduler, meeple-auth  (유일한 Spring Boot 진입점, Controller/인증 + 배치 스케줄 구동)
meeple-chatting ──> meeple-common, meeple-auth                             (WebSocket. core에 의존하지 않는 자립 모듈 — 자체 도메인/서비스/포트 보유)
meeple-scheduler ──> meeple-common                                         (배치 로직 + 자체 포트. core에 의존하지 않는 라이브러리)
meeple-core ──> meeple-common                                              (도메인/유스케이스/포트 = 비즈니스 핵심)
meeple-infra ──> meeple-common, meeple-core, meeple-scheduler, meeple-chatting  (JPA 엔티티/Repository/Adapter. core·scheduler·chatting의 out-port를 구현)
meeple-auth ──> (spring-security, jjwt)                                     (인증 검증 커널: TokenProvider/PrincipalDetails. 발급/로그인은 api)
meeple-common ──> (없음)                                                    (공용 enum/상수)
```

- `meeple-core`는 **순수 비즈니스 로직**이다. JPA·웹 프레임워크 등 인프라 세부에 의존하지 않는다.
- 영속성 구현은 `meeple-infra`의 Adapter가 `meeple-core`/`meeple-scheduler`/`meeple-chatting`의 **out-port를 구현**하는 방식으로 둔다.
- HTTP 경계(Controller / 요청·응답 DTO)는 `meeple-api`에만 둔다.
- 공용 enum/상수는 `meeple-common`에 둔다.
- **인증 검증 커널 분리**: JWT 검증·파싱과 인증 주체는 `meeple-auth`에 둔다(`com.org.meeple.auth.jwt.TokenProvider`, `com.org.meeple.auth.PrincipalDetails`).
  `meeple-auth`는 **발급/로그인/쿠키/SecurityConfig를 모르는** 순수 검증 커널이라, 토큰을 검증만 하면 되는 모듈(예: `meeple-chatting`)이 api에 의존하지 않고 재사용할 수 있다.
  발급·OAuth2 로그인·재발급·쿠키·`SecurityConfig`·MVC 바인딩 등 **인증 서버/게이트웨이 책임은 `meeple-api`**에 남는다(이들이 `meeple-auth`의 `TokenProvider`를 호출한다).
- `meeple-api`가 **유일한 `@SpringBootApplication`**이며(`meeple-scheduler`는 실행 가능한 부트 앱이 아닌 라이브러리),
  기준 패키지가 `com.org.meeple`라 core 서비스·scheduler 배치 로직·infra 어댑터를 함께 스캔한다.
  (`@EnableScheduling`으로 스케줄러 빈도 활성화)
- **배치/스케줄 모듈 분리**: 배치 알고리즘·유스케이스·전용 포트/도메인은 `meeple-scheduler`에 둔다
  (예: `com.org.meeple.scheduler.match.application.RunDailyMatchBatchService`,
  `...port.out.{GetMatchBatchTargetPort, MatchPoolPort, MatchRecordPort, TimeGenerator}`,
  `...match.domain.{ActiveUser, MatchPoolGroup, MatchPoolByGender}`).
  `meeple-scheduler`는 **`meeple-core`에 의존하지 않으므로**, 배치가 쓰는 매칭 이력/저장·시각 등 공유 동작도
  scheduler가 **자기 out-port**(`MatchRecordPort`, `TimeGenerator`)로 정의하고, **infra 어댑터가 core 도메인에 위임해 구현**한다
  (예: `MatchRecordAdapter` → core `Match.propose`/`GetMatchPort`/`SaveMatchPort`). 시각 구현은 scheduler가 직접 제공한다
  (`SystemBatchTimeGenerator`; core의 `SystemTimeGenerator`와 빈 이름이 겹치지 않게 클래스명을 구분).
- `@Scheduled` **크론 트리거**는 `meeple-api`의 `scheduler` 패키지(`com.org.meeple.scheduler.match.MatchBatchScheduler`)에
  `@Component`로 두고, scheduler 모듈의 실행 로직(`MatchBatchJob` → `RunDailyMatchBatchUseCase`)을 호출한다.
  스케줄러는 "언제 실행할지(@Scheduled)"만, 모듈은 "무엇을 실행할지(배치 로직)"만 책임진다.
  초기 단계라 별도 스케줄러 인스턴스 없이 api 프로세스에서 함께 구동한다.
  단, **api를 스케일아웃하면 배치가 인스턴스마다 중복 실행**되므로, 다중 인스턴스 시점엔 ShedLock 등 분산 락이나
  별도 스케줄러 앱 분리가 필요하다.
- **채팅(WebSocket) 모듈 자립**: `meeple-chatting`은 **`meeple-core`에 의존하지 않는다.** scheduler와 같은 자립 구조로,
  채팅에 필요한 도메인·유스케이스/서비스·전용 포트·에러를 **모두 자체 보유**하며, **패키지 구조도 core와 동일한 클린 아키텍처**를 따른다
  (`chat/` 도메인 루트 아래 `domain/` + `application/`(서비스·`<Domain>ErrorCode`·`port/{in(+command, result), out}`) + 인바운드 어댑터 `adapter/web/`(+ `request/`·`response/`), 공용 예외는 `common/error/`,
  WebSocket/STOMP 설정류는 모듈 루트 `config/`).
  예: `com.org.meeple.chatting.chat.domain.{ChatMessage, ChatRoom, ChatRoomMember(s)}`,
  `...chat.application.SendChatMessageService`/`VerifyChatRoomParticipantService`,
  `...chat.application.port.in.{SendChatMessageUseCase, command.SendChatMessageCommand, result.SentChatMessageResult}`,
  `...chat.application.port.out.{GetChatRoomPort, SaveChatRoomPort, GetChatRoomMemberPort, SaveChatRoomMemberPort, SaveChatMessagePort, TimeGenerator}`,
  WebSocket/STOMP 인바운드 어댑터는 `...chat.adapter.web.{ChatMessageController, AuthChannelInterceptor}` + 요청/응답 DTO `...adapter.web.request.ChatMessageSendRequest`·`...adapter.web.response.ChatMessageDto`,
  설정/세션 이벤트는 `...config.{WebSocketConfig, SessionEventListener}`.
  - **어댑터는 도메인을 직접 다루지 않는다**: 유스케이스는 도메인 모델이 아니라 **결과 리드 모델(`port/in/result`, 예 `SentChatMessageResult`)**을 반환하고, 컨트롤러는 입력 `command`·결과 `result`·표현 `DTO`만 다룬다. (도메인 모델은 application 안쪽에 머문다)
  에러는 `...chat.application.ChatErrorCode`(도메인 에러 enum)와 `...common.error.ChatException`(자체 예외)로 두며, core의 `BusinessException`/`ErrorCode`를 쓰지 않는다.
  (core가 도메인 `<Domain>ErrorCode`는 application에, 공용 `BusinessException`은 `common.error`에 두는 것과 같은 배치)
  - 영속성은 chatting이 **자기 out-port**로 정의하고, **infra 어댑터가 직접 구현**한다(scheduler는 core 도메인에 위임하지만, chatting은 같은 JPA 엔티티를 **자체 도메인으로 직접 매핑**해 구현한다). 매퍼는 core 매퍼와 이름을 구분한다(`toChattingDomain`/`toChattingEntity`).
    단, chat의 infra 어댑터는 **엔티티별 단일 어댑터**(`ChatRoomAdapter`/`ChatMessageAdapter`/`ChatRoomMemberAdapter`)가 core out-port와 chatting out-port를 함께 구현한다. (아래 [chat 도메인 CQRS 패키지 분리](#chat-도메인-cqrs-패키지-분리) 참고)
  - 시각도 chatting이 자체 제공한다(`SystemChatTimeGenerator`; core·scheduler의 TimeGenerator 빈과 클래스명을 구분).
  - **트레이드오프**: 같은 chat 테이블을 core(HTTP 조회/방 생성)와 chatting(WS 발송)이 **각자 도메인으로 다루므로 일부 규칙이 이중화**된다. 자립(별도 인스턴스 분리 용이)의 대가로 수용한다.
  - 토큰 검증은 `meeple-auth`의 `TokenProvider`로 하고(STOMP CONNECT 프레임), 핸드셰이크 경로(`/ws/chat/**`)는 `SecurityConfig`에서 permitAll로 열어 둔다.

## 도메인 레이어링 (헥사고날)

도메인별 표준 구조 (예: `meeple-core/.../user/`):

```
<domain>/
├── domain/                         # 순수 도메인 모델 (User, UserDetail, UserWithDetail …)
└── application/
    ├── <Verb><Noun>Service.kt      # UseCase 구현, @Service + @Transactional
    ├── <Domain>ErrorCode.kt        # 도메인별 ErrorCode enum
    └── port/
        ├── in/
        │   ├── <Verb><Noun>UseCase.kt   # 인바운드 포트(인터페이스)
        │   ├── command/                 # 상태 변경 입력 DTO
        │   └── result/                  # 결과/리드 모델
        └── out/
            └── <Get|Save|...>Port.kt    # 아웃바운드 포트(인터페이스)
```

- **Controller**는 Service가 아니라 **in-port `UseCase` 인터페이스**를 주입한다. (예: `MatchController` → `GetMatchesUseCase`)
- **Service**는 `@Service` + `@Transactional`로 UseCase를 구현하고, 필요한 포트들을 주입받는다.
- **Adapter**(`@Component`, `meeple-infra`)가 out-port를 구현하고 JPA Repository에 위임하며, `toDomain()`/`toEntity()`로 도메인 ↔ 엔티티를 변환한다.
- **네이밍**: `<동사><명사>UseCase` / `<동사><명사>Service` (Get/Register/Recommend/Acquire …).
- **에러 처리**: 도메인별 `ErrorCode` enum + `BusinessException` 사용. (예: `MatchErrorCode.MATCH_NOT_FOUND`)
- **`user` 도메인이 사용자 프로필을 소유한다.** 계정/식별(`User`, 회사 이메일 인증)뿐 아니라 **프로필(command 도메인 `UserDetail`·query read model `UserDetailView`/`UserWithDetailView`, 닉네임·프로필이미지·성별·나이 등)도 `user` 도메인**에 둔다. (엔티티 `com.org.meeple.infra.user.command.entity.UserDetailEntity`, 에러코드 `UserErrorCode.USER_DETAIL_NOT_FOUND` 등) 프로필은 매칭 산출물이 아니라 여러 도메인이 읽는 공유 사용자 데이터이므로 특정 도메인(match 등)에 두지 않는다.
  - match·chat·alarm 등 **다른 도메인이 프로필을 필요로 하면**: core에서는 user의 in-port(`GetUserDetailUseCase`/`GetUserWithDetailUseCase`)로 참조하고, 목록·상세의 **표시용 프로필 조인**은 infra 읽기 어댑터가 `UserDetailEntity`를 조인해 자기 도메인 read model로 투영한다. (예: `ChatParticipantQueryDaoImpl` → `ChatParticipant`, `MatchWithPartnerQueryDaoImpl` → 상대 프로필)

## 도메인 간 참조 규칙

**다른 도메인의 데이터·동작이 필요하면, 그 도메인의 in-port `UseCase`를 주입해 호출한다.** 다른 도메인의 out-port나 Service 구현체를 직접 주입하지 않는다.

```kotlin
// ✅ 권장: 다른 도메인은 in-port UseCase로 참조
@Service
class GetMatchesService(
    private val getUserWithDetailUseCase: GetUserWithDetailUseCase, // user 도메인의 in-port
    private val matchWithPartnerQueryDao: MatchWithPartnerQueryDao, // 자기 도메인의 조회 dao
) : GetMatchesUseCase { ... }

// ❌ 지양: 다른 도메인의 out-port 직접 주입
@Service
class GetMatchesService(
    private val getUserWithDetailPort: GetUserWithDetailPort,       // user 도메인 내부 포트에 결합됨
)
```

- **자기 도메인 내부**의 영속성 접근은 그대로 **자기 도메인의 out-port**를 사용한다.
- 선례: 프로필을 `user`로 이전하면서 `MatchEventHandler`·`GetMatchesService`는 프로필을 user의 query in-port(`GetUserDetailUseCase`/`GetUserWithDetailUseCase`)로 참조하도록 정리했다. (단, `nullable` 조회가 필요한 곳을 위해 `GetUserDetailUseCase.findByUserId(): UserDetailView?`도 둔다)
- 참고: 일부 기존 코드는 아직 과거 패턴(다른 도메인 out-port 직접 주입)을 쓸 수 있다. 본 규칙은 **신규/수정 코드**에 적용하며, 기존 코드를 일괄 변경하지는 않는다.

## 명령·조회 분리 (CQS, Command Query Separation)

**상태를 바꾸는 명령(command)과 데이터를 읽는 조회(query)를 메서드·포트·서비스·트랜잭션 단위로 분리한다.** 한 연산이 "쓰면서 읽어 돌려주는" 혼합이 되지 않게 하고, 조회 경로는 부수효과를 갖지 않는다.

- **메서드**: 명령은 상태만 바꾸고 결과 반환을 최소화한다(저장된 식별자/엔티티 정도). 조회는 **부수효과 없이** 값만 반환한다. 조회 경로에서 저장·상태 변경 포트를 호출하지 않는다.
- **UseCase/Service**: `Get…UseCase`(조회)와 `Save/Register/Respond/Acquire…UseCase`(명령)를 나눈다.
  - 조회 서비스는 `@Transactional(readOnly = true)`, 명령 서비스는 `@Transactional`. (예: `GetChatRoomDetailService` ↔ `SaveChatRoomService`)
- **아웃포트**: `Get…Port`(조회)와 `Save…Port`(명령)를 **하나의 포트에 섞지 않는다**. (예: `GetUserPort` / `SaveUserPort`. chat·match는 여기서 더 나아가 조회를 `query/dao`로 분리한다 — 아래 [chat 도메인 CQRS 패키지 분리](#chat-도메인-cqrs-패키지-분리) 참고)
- **어댑터**: 위 [영속성 어댑터 구성](#영속성-어댑터-구성-엔티티--모듈-querydsl-분리) 규칙과 맞물린다. 저장·단건은 Spring Data 어댑터, 조인·동적 조회는 QueryDSL 어댑터(`JPAQueryFactory`만 주입)로 나눈다.
- **읽기 모델 분리**: 조회는 도메인 모델 대신 **전용 read model(DTO/프로젝션)**을 반환할 수 있다. 명령은 도메인 모델을 다룬다.
  - 조회 read model 예: `ChatRoomSummary`, `ChatParticipant`(+ 일급 컬렉션 `ChatParticipants`). 명령이 다루는 도메인: `ChatRoom`, `ChatRoomMember`.
- **검증(읽기)과 표시(읽기)의 비용 분리**: 같은 조회라도 필요 데이터가 다르면 쿼리를 나눈다. 접근 검증만 필요하면 단건 존재 조회로 가볍게, 화면 표시가 필요하면 조인 조회로 가져온다.
  - 선례: `GetChatRoomDetailService`는 첫 페이지에선 참가자 프로필 조인(`findByChatRoomId`)으로 검증 겸 표시하고, 이후 커서 페이지에선 표시 데이터 없이 단건 존재(`existsParticipant`)로만 검증한다.

```kotlin
// ✅ 조회: 부수효과 없음, read model 반환, readOnly 트랜잭션
@Service
@Transactional(readOnly = true)
class GetChatRoomDetailService(
    private val chatParticipantQueryDao: ChatParticipantQueryDao,   // 조회 전용 dao (query는 dao에만 의존)
) : GetChatRoomDetailUseCase { ... }

// ✅ 명령: 상태 변경, 도메인 모델, 쓰기 트랜잭션
@Service
@Transactional
class SaveChatRoomService(
    private val saveChatRoomPort: SaveChatRoomPort,               // 저장 전용 포트
    private val saveChatRoomMemberPort: SaveChatRoomMemberPort,
) : SaveChatRoomUseCase { ... }

// ❌ 지양: 조회 메서드가 저장(상태 변경)을 함께 수행
fun getChatRoom(id: Long): ChatRoom {
    val room = getChatRoomPort.findById(id) ?: throw ...
    saveChatRoomPort.save(room.touch())  // 조회가 부수효과를 가짐 → 금지
    return room
}
```

## chat 도메인 CQRS 패키지 분리

`chat` 도메인은 위 [CQS](#명령조회-분리-cqs-command-query-separation)를 **패키지 수준의 CQRS**로 한 단계 더 분리한 사례다. 명령(쓰기)과 조회(읽기)를 `command`/`query` 패키지로 나누고, 각 측의 영속성 구현 기법까지 구분한다. **이 구조는 chat 한정**이며, 다른 도메인(user/match/coin 등)은 표준 헥사고날(`application` + `domain`) + CQS를 유지한다.

### core (`meeple-core/.../chat/`)

```
chat/
├── ChatErrorCode.kt                 # command·query 공유라 chat 루트에 둔다
├── command/                         # 상태 변경
│   ├── service/                     # <Verb>Service + port/in(+command) + port/out
│   │   ├── SaveChatRoomService / LeaveChatRoomService / MarkChatRoomAsReadService
│   │   └── port/{in(+command), out}
│   └── domain/                      # 행위를 가진 도메인 모델: ChatRoom, ChatRoomMember(s), ChatMessage
└── query/                           # 조회
    ├── service/                     # 조회 서비스 + port/in (UseCase)
    │   ├── GetChatRoomsService / GetChatRoomDetailService
    │   └── port/in
    ├── dao/                         # 조회 out-port 인터페이스 (*QueryDao)
    └── dto/                         # 읽기 모델 (read model / 일급 컬렉션)
```

- 명령·조회 서비스 모두 `application` 대신 **`service`** 패키지명을 쓴다(`command/service`, `query/service`).
- 조회 측은 `port/out` 대신 **`dao`(조회 인터페이스, `*QueryDao`) + `dto`(읽기 모델)**로 둔다. dao는 도메인이 아니라 read model만 반환한다.
- read model(`query/dto`): `ChatRoomSummary`(목록), `ChatRoomDetail`(상세), `ChatRoomView`(상세 헤더용 방 상태), `ChatParticipant`(+`ChatParticipants`), `ChatMessageView`(+ 일급 컬렉션 `ChatMessageViews`). 일급 컬렉션은 감싸는 read model에 맞춰 명명한다(`ChatMessageViews` ⊃ `ChatMessageView`).
- `ChatErrorCode`는 command 도메인과 query read model 양쪽이 쓰므로 **chat 루트**(`com.org.meeple.core.chat.ChatErrorCode`)에 둔다. (command에 두면 query→command 결합이 생긴다)

### infra (`meeple-infra/.../chat/`)

```
chat/
├── command/
│   ├── adapter/      # out-port 구현체 *Adapter (Spring Data JPA 메서드 쿼리) — core·chatting 어댑터
│   ├── entity/       # JPA 엔티티
│   ├── mapper/       # toDomain/toEntity (+ chatting용 toChattingDomain/toChattingEntity)
│   └── repository/   # Spring Data JpaRepository
└── query/            # dao 구현체 *DaoImpl (QueryDSL)
```

- **out-port 구현체 = `*Adapter`**, **Spring Data JPA 메서드 쿼리**로 구현한다.
- **dao 구현체 = `*DaoImpl`**(인터페이스명 + `Impl`), **QueryDSL**로 구현한다. (chat은 [영속성 어댑터 구성](#영속성-어댑터-구성-엔티티--모듈-querydsl-분리)의 `*QueryCoreAdapter` 대신 이 `*DaoImpl`을 쓴다)
- **같은 단건 조회가 command·query 양쪽에 있어도 공유하지 않고 각자 구현**한다. command adapter는 메서드 쿼리로, query daoImpl은 QueryDSL로 둔다.
  - 예: 방 단건 — command `GetChatRoomPort.findById`(메서드 쿼리) ↔ query `ChatRoomQueryDao.findById`→`ChatRoomView`(QueryDSL). 참가자 존재 — command `GetChatRoomMemberPort` ↔ query `ChatRoomMemberQueryDao.existsByChatRoomIdAndUserId`(QueryDSL).
- `entity`·`mapper`·`repository`는 `command` 아래 두고 query daoImpl이 이를 참조한다(infra 내부 query→command 참조는 허용).
- **command 어댑터는 엔티티별로 하나만 둔다.** 같은 chat 테이블을 쓰는 core·chatting 두 모듈의 out-port를 한 어댑터(`ChatRoomAdapter`/`ChatMessageAdapter`/`ChatRoomMemberAdapter`)에서 함께 구현한다. ([영속성 어댑터 구성](#영속성-어댑터-구성-엔티티--모듈-querydsl-분리)의 "모듈별 분리" 기본 규칙과 달리, chat은 모듈이 아닌 **엔티티 단위**로 묶는다)

### 인터페이스 → 구현체 매핑

엔티티별 command 어댑터는 **core·chatting 두 모듈의 out-port를 한 클래스에서 함께 구현**한다(모듈별로 쪼개지 않는다). 단순명이 겹치는 포트·도메인(`SaveChatMessagePort`, `GetChatRoomMemberPort`, `ChatMessage`)은 chatting 쪽을 import alias로 구분한다.

| 구분 | 인터페이스 (core / chatting) | 구현체 (infra) | 기법 |
|---|---|---|---|
| command | core `SaveChatRoomPort`·`GetChatRoomPort` / chatting `UpdateChatRoomPort` | `ChatRoomAdapter` | Spring Data 메서드 쿼리 |
| command | core `SaveChatMessagePort` / chatting `SaveChatMessagePort` | `ChatMessageAdapter` | Spring Data 메서드 쿼리 |
| command | core `GetChatRoomMemberPort`·`SaveChatRoomMemberPort` / chatting `GetChatRoomMemberPort`·`IncreaseUnreadCountPort` | `ChatRoomMemberAdapter` | Spring Data 메서드 쿼리 |
| query | `ChatRoomQueryDao` | `ChatRoomQueryDaoImpl` | QueryDSL |
| query | `ChatMessageQueryDao` | `ChatMessageQueryDaoImpl` | QueryDSL |
| query | `ChatParticipantQueryDao` | `ChatParticipantQueryDaoImpl` | QueryDSL |
| query | `ChatRoomMemberQueryDao` | `ChatRoomMemberQueryDaoImpl` | QueryDSL |

### 의존 규칙 & 트레이드오프

- **query는 자기 dao에만 의존한다.** command의 out-port·도메인 모델을 참조하지 않는다(조회 서비스가 command 포트를 주입하지 않는다). 그래서 query는 명령 도메인(`ChatRoom`/`ChatMessage`) 대신 **자체 read model**(`ChatRoomView`/`ChatMessageView`)을 쓴다.
- 같은 데이터를 command 도메인과 query read model로 **이중으로 모델링**하는 비용을 감수하고 command↔query 결합을 끊는다. (chat 한정 적용)
- 컨트롤러(api)는 command in-port(`SaveChatRoom`/`LeaveChatRoom`/`MarkChatRoomAsReadUseCase`)와 query in-port(`GetChatRooms`/`GetChatRoomDetailUseCase`)를 그대로 주입한다.

## 코딩 원칙

### 타입 명시 (Explicit Types)

타입을 생략하지 말고 항상 명시적으로 기입한다. 타입 추론(type inference)에 의존하지 않는다.

- 변수/프로퍼티 선언 시 타입을 명시한다.
  ```kotlin
  // ❌ 지양
  val count = 0
  val name = user.getName()

  // ✅ 지향
  val count: Int = 0
  val name: String = user.getName()
  ```
- 함수의 반환 타입을 명시한다. 표현식 본문(expression body) 함수도 반환 타입을 기입한다.
  ```kotlin
  // ❌ 지양
  fun findUser(id: Long) = repository.findById(id)

  // ✅ 지향
  fun findUser(id: Long): User = repository.findById(id)
  ```
- 람다/콜백의 파라미터 타입도 가능한 한 명시한다.

### JPQL 조인 (Explicit Joins)

`@Query`(JPQL)에서 여러 엔티티를 조인할 때 **콤마(`from A a, B b where ...`)로 묶는 암묵적 조인을 쓰지 않고, `join ... on`을 쓴 명시적 조인**으로 작성한다.

- 연관관계(`@ManyToOne` 등) 매핑이 없는 엔티티끼리도 `join B b on b.x = a.y` 형태(연관 없는 엔티티 간 명시적 조인)를 사용한다.
- 조인 조건은 `where`가 아니라 `on`에 둔다. (필터 조건만 `where`에 남긴다)

```kotlin
// ❌ 지양: 암묵적(콤마) 조인 + 조인 조건이 where에 섞임
"""
select m, d
from MatchEntity m, UserDetailEntity d
where m.maleUserId = :userId and d.userId = m.femaleUserId
"""

// ✅ 지향: 명시적 join ... on (조인 조건은 on, 필터는 where)
"""
select m, d
from MatchEntity m
join UserDetailEntity d on d.userId = m.femaleUserId
where m.maleUserId = :userId
"""
```

### 영속성 어댑터 구성 (엔티티 × 모듈, QueryDSL 분리)

영속성 어댑터는 **(엔티티 × 사용 모듈)마다 하나씩** 둔다. 그 엔티티를 **여러 모듈(core, scheduler 등)이 쓰면 모듈별로** 어댑터를 나눈다.

- **단, QueryDSL 조회는 별도 어댑터로 분리한다.** Spring Data(파생 쿼리·단건 조회·저장)와 QueryDSL을 한 어댑터에 섞지 않는다.
- 네이밍: `<Entity><Module>Adapter`(Spring Data) / `<Entity>Query<Module>Adapter`(QueryDSL). 즉 한 엔티티가 core·scheduler 두 모듈에서 QueryDSL까지 쓰면 다음 4종까지 나올 수 있다.
  - core(미이전 도메인) → `<Entity>CoreAdapter`(Spring Data, 저장·단건) + `<Entity>Query<Module>Adapter`(QueryDSL 조회)
  - scheduler → `<Entity>SchedulerAdapter`(scheduler out-port; QueryDSL이 있으면 `<Entity>QuerySchedulerAdapter`)
  - 실제로는 **해당 모듈에 구현할 포트가 있는 어댑터만** 만든다.
- **scheduler용 어댑터**는 scheduler 자신의 out-port를 구현하되(scheduler는 core에 의존하지 않는다), 실제 동작은 core 도메인/포트에 위임한다. core 도메인을 아는 infra가 둘을 잇는다.
- QueryDSL 조회 어댑터에는 `JPAQueryFactory`만 주입한다.
- `chat`·`match` 도메인은 이 규칙을 **CQRS 패키지 분리**로 확장했다: out-port 구현은 `command/adapter`의 `*Adapter`(Spring Data 메서드 쿼리), 조회 dao 구현은 `query`의 `*DaoImpl`(QueryDSL)로 나눈다. (예: `MatchCoreAdapter`(command, `GetMatchPort`·`SaveMatchPort`) + `MatchWithPartnerQueryDaoImpl`(query, `MatchWithPartnerQueryDao`). 아래 [chat 도메인 CQRS 패키지 분리](#chat-도메인-cqrs-패키지-분리) 참고)

```kotlin
// ✅ core / Spring Data: 단건·존재 조회 + 저장
@Component
class MatchCoreAdapter(
    private val matchJpaRepository: MatchJpaRepository,
) : GetMatchPort, SaveMatchPort { ... }

// ✅ QueryDSL: 조인·동적 컬럼 조회 (JPAQueryFactory만 주입)
//    CQRS 도메인(chat·match)은 이 조회 구현을 query의 *DaoImpl로 둔다.
@Component
class MatchWithPartnerQueryDaoImpl(
    private val queryFactory: JPAQueryFactory,
) : MatchWithPartnerQueryDao { ... }

// ✅ scheduler / Spring Data: scheduler 포트를 core 포트에 위임
@Component
class MatchSchedulerAdapter(
    private val getMatchPort: GetMatchPort,
    private val saveMatchPort: SaveMatchPort,
) : MatchRecordPort { ... }
```

조회 구현은 다음 우선순위로 고른다. (단순할수록 위쪽)

1. **Spring Data 파생 쿼리(메서드 쿼리)** — 단건/존재/단순 조건 조회. `existsByMaleUserIdAndIntroducedDate(...)`처럼 메서드 이름으로 표현되면 `@Query`(JPQL)를 쓰지 않는다.
2. **QueryDSL** — 동적 컬럼 선택, 엔티티 조인, DTO 프로젝션 등 메서드 쿼리로 표현하기 어려운 복잡 조회. 어댑터에 `JPAQueryFactory`를 주입해 작성한다.
   - 성별 등으로 **조회/상대 컬럼이 갈리면 `if`로 컬럼(`NumberPath` 등)을 동적으로 골라 단일 컬럼 조건**으로 둔다. (`CASE`/`OR` 없이 인덱스 활용)
   - 연관 매핑이 없는 엔티티는 `.join(QOther).on(...)`(명시적 조인)으로 묶는다. (1+N 방지)
3. **`@Query`(JPQL)** — 1·2로 깔끔히 표현되지 않을 때만 쓰고, 이때는 위 **JPQL 조인** 규칙(명시적 `join ... on`)을 따른다.

### 도메인 검증 (Validation in Domain)

도메인 상태에 대한 검증은 **서비스에 `if ... throw`를 나열하지 말고, 도메인 모델의 검증 함수로 캡슐화**한다. 서비스는 그 함수를 한 번 호출한다.

- 검증 함수는 `validate<대상>(...)` 형태로 도메인 모델에 두고, 위반 시 도메인별 `ErrorCode` + `BusinessException`을 던진다.
- 검증에 필요한 입력(예: 사용자가 입력한 코드, 현재 시각 `now`)은 파라미터로 받는다. (도메인은 시계/인프라에 의존하지 않는다)
- 참고 선례: `User.validateRegistered()`, `CompanyEmailVerification.validate(code, now)`.

```kotlin
// ❌ 지양: 서비스에 검증 분기를 나열
if (verification.code != code) throw BusinessException(UserErrorCode.VERIFICATION_CODE_MISMATCH)
if (verification.isVerified) throw BusinessException(UserErrorCode.VERIFICATION_ALREADY_VERIFIED)
if (verification.isExpired(now)) throw BusinessException(UserErrorCode.VERIFICATION_EXPIRED)

// ✅ 지향: 도메인 모델에 검증을 위임
verification.validate(code, now)

// CompanyEmailVerification (domain)
fun validate(code: String, now: LocalDateTime) {
    if (this.code != code) throw BusinessException(UserErrorCode.VERIFICATION_CODE_MISMATCH)
    if (isVerified) throw BusinessException(UserErrorCode.VERIFICATION_ALREADY_VERIFIED)
    if (isExpired(now)) throw BusinessException(UserErrorCode.VERIFICATION_EXPIRED)
}
```

### 일급 컬렉션 (First-Class Collection)

도메인 모델의 **컬렉션은 원시 `List`/`Set`을 그대로 노출하지 말고, 이를 감싼 일급 컬렉션 도메인 모델로 반환**한다. 컬렉션에 대한 동작(개수·필터·합계 등)을 한곳에 응집시키고, 시그니처를 안정적인 도메인 타입으로 고정하기 위함이다.

- 포트(out/in)나 도메인 함수가 여러 건을 반환할 때 `List<X>`가 아니라 `Xs`(복수형) 형태의 래퍼를 반환한다.
- 래퍼는 원시 리스트를 `values` 프로퍼티로 보관하고, 컬렉션 관련 동작을 메서드로 제공한다. (서비스/어댑터에 흩어진 `map`/`filter`/`sum`을 도메인으로 모은다)
- 참고 선례: `CoinItems`(코인 상품 목록 일급 컬렉션, `GetCoinItemPort.findAll(): CoinItems`).

```kotlin
// ❌ 지양: 원시 List를 그대로 반환
interface GetCoinItemPort {
    fun findAll(): List<CoinItem>
}

// ✅ 지향: 일급 컬렉션으로 감싸 반환
interface GetCoinItemPort {
    fun findAll(): CoinItems
}

// CoinItems (domain) — 컬렉션 동작을 응집
data class CoinItems(
    val values: List<CoinItem>,
) {
    val size: Int get() = values.size
    fun isEmpty(): Boolean = values.isEmpty()
}
```

## 테스트 코드 전략

레이어별로 테스트 대상과 방식을 구분한다.

- **도메인 모델(`meeple-core`의 `domain`) → 유닛 테스트 (Kotest)**
  순수 비즈니스 로직(검증 함수, 상태 전이, 일급 컬렉션 동작 등)을 프레임워크·인프라 없이 검증한다.
  포트는 손수 만든 fake/stub으로 대체하고, 시각 등 외부 의존은 파라미터로 주입한다. (예: `Match.respond`, `User.validateRegistered`, `ChatRoom`)
- **`meeple-api` → E2E 테스트**
  실제 서버를 띄우고 HTTP 엔드포인트를 호출해 컨트롤러~서비스~영속성~외부연동까지 전 구간을 검증한다.

**변경 시 규칙**: 코드에 새로운 변경 사항이 생기면, 위 레이어 중 **변경된 지점에 해당하는 테스트 코드를 함께 작성한다.**
(도메인 모델이 바뀌면 그 도메인 유닛 테스트를, api 경계(엔드포인트/요청·응답)가 바뀌면 그 엔드포인트 E2E 테스트를 추가·갱신한다.)

### 도메인 유닛 테스트 작성 규약

- **프레임워크**: Kotest [`DescribeSpec`]. E2E와 동일하게 `describe`/`it` BDD 스타일로 작성한다. (예: `ChatRoomTest`)
  순수 도메인 검증이라 Spring 컨텍스트(`SpringExtension`)는 띄우지 않는다. 베이스 클래스 없이 `class XxxTest : DescribeSpec({ ... })`로 둔다.
- **어서션**: JUnit/`kotlin-test`가 아니라 Kotest 매처(`io.kotest.matchers.shouldBe`)와 `io.kotest.assertions.throwables.shouldThrow`를 쓴다.
  도메인 예외는 `val ex = shouldThrow<BusinessException> { ... }; ex.errorCode shouldBe XxxErrorCode.YYY` 형태로 검증한다.
- **외부 의존**: 시각 등은 테스트에서 고정값(`LocalDateTime.of(...)`)을 만들어 도메인 함수에 파라미터로 주입한다. (`TimeGenerator` 등 인프라를 띄우지 않는다)
- **테스트 의존성 배치**: Kotest(`kotest-runner-junit5`, `kotest-assertions-core`)를 `meeple-core`의 `testImplementation`에 둔다.
  E2E와 같은 버전(5.9.x)으로 고정하고, Spring 연동(`kotest-extensions-spring`)은 추가하지 않는다.

### E2E 테스트 작성 규약

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
