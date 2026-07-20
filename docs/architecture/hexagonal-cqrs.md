# 헥사고날 레이어링 · CQRS · 영속성

> CLAUDE.md의 아키텍처 규칙 요약에 대한 상세 레퍼런스.

## 도메인 레이어링 (헥사고날)

도메인별 표준 구조 (예: `oneulsogae-core/.../user/`):

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

> CQRS를 적용한 도메인(user/match/coin/chat/scheduler)은 위 `application`+`domain`을 `command`/`query` 패키지로 한 번 더 나눈다. 아래 [CQRS 패키지 분리](#cqrs-패키지-분리) 참고.

- **Controller**는 Service가 아니라 **in-port `UseCase` 인터페이스**를 주입한다. (예: `MatchController` → `GetMatchesUseCase`)
- **Service**는 `@Service` + `@Transactional`로 UseCase를 구현하고, 필요한 포트들을 주입받는다.
- **Adapter**(`@Component`, `oneulsogae-infra`)가 out-port를 구현하고 JPA Repository에 위임하며, `toDomain()`/`toEntity()`로 도메인 ↔ 엔티티를 변환한다.
- **네이밍**: `<동사><명사>UseCase` / `<동사><명사>Service` (Get/Register/Recommend/Acquire …).
- **에러 처리**: 도메인별 `ErrorCode` enum + `BusinessException` 사용. (예: `MatchErrorCode.MATCH_NOT_FOUND`)
- **`user` 도메인이 사용자 프로필을 소유한다.** 계정/식별(`User`, 회사 이메일 인증)뿐 아니라 **프로필(command 도메인 `UserDetail`·query read model `UserDetailView`/`UserWithDetailView`, 닉네임·프로필이미지·성별·나이 등)도 `user` 도메인**에 둔다. (엔티티 `com.org.oneulsogae.infra.user.command.entity.UserDetailEntity`, 에러코드 `UserErrorCode.USER_DETAIL_NOT_FOUND` 등) 프로필은 매칭 산출물이 아니라 여러 도메인이 읽는 공유 사용자 데이터이므로 특정 도메인(match 등)에 두지 않는다.
  - match·chat·alarm 등 **다른 도메인이 프로필을 필요로 하면**: core에서는 user의 in-port(`GetUserDetailUseCase`/`GetUserWithDetailUseCase`)로 참조하고, 목록·상세의 **표시용 프로필 조인**은 infra 읽기 어댑터가 `UserDetailEntity`를 조인해 자기 도메인 read model로 투영한다. (예: `ChatParticipantDaoImpl` → `ChatParticipant`, `MatchWithPartnerDaoImpl` → 상대 프로필)

## 도메인 간 참조 규칙

**다른 도메인의 데이터·동작이 필요하면, 그 도메인의 in-port `UseCase`를 주입해 호출한다.** 다른 도메인의 out-port나 Service 구현체를 직접 주입하지 않는다.

```kotlin
// ✅ 권장: 다른 도메인은 in-port UseCase로 참조
@Service
class GetMatchesService(
    private val getUserWithDetailUseCase: GetUserWithDetailUseCase, // user 도메인의 in-port
    private val matchWithPartnerDao: MatchWithPartnerDao, // 자기 도메인의 조회 dao
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
- **아웃포트**: `Get…Port`(조회)와 `Save…Port`(명령)를 **하나의 포트에 섞지 않는다**. (예: `GetUserPort` / `SaveUserPort`. chat·match는 여기서 더 나아가 조회를 `query/dao`로 분리한다 — 아래 [CQRS 패키지 분리](#cqrs-패키지-분리) 참고)
- **어댑터**: 아래 [영속성 어댑터 구성](#영속성-어댑터-구성-엔티티-단위-querydsl-분리) 규칙과 맞물린다. 저장·단건은 Spring Data 어댑터, 조인·동적 조회는 QueryDSL 어댑터(`JPAQueryFactory`만 주입)로 나눈다.
- **읽기 모델 분리**: 조회는 도메인 모델 대신 **전용 read model(DTO/프로젝션)**을 반환할 수 있다. 명령은 도메인 모델을 다룬다.
  - 조회 read model 예: `ChatRoomSummary`, `ChatParticipant`(+ 일급 컬렉션 `ChatParticipants`). 명령이 다루는 도메인: `ChatRoom`, `ChatRoomMember`.
- **검증(읽기)과 표시(읽기)의 비용 분리**: 같은 조회라도 필요 데이터가 다르면 쿼리를 나눈다. 접근 검증만 필요하면 단건 존재 조회로 가볍게, 화면 표시가 필요하면 조인 조회로 가져온다.
  - 선례: `GetChatRoomDetailService`는 첫 페이지에선 참가자 프로필 조인(`findByChatRoomId`)으로 검증 겸 표시하고, 이후 커서 페이지에선 표시 데이터 없이 단건 존재(`existsParticipant`)로만 검증한다.

```kotlin
// ✅ 조회: 부수효과 없음, read model 반환, readOnly 트랜잭션
@Service
@Transactional(readOnly = true)
class GetChatRoomDetailService(
    private val chatParticipantDao: ChatParticipantDao,   // 조회 전용 dao (query는 dao에만 의존)
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

## CQRS 패키지 분리

`chat` 도메인은 위 CQS를 **패키지 수준의 CQRS**로 한 단계 더 분리한 첫 사례다. 명령(쓰기)과 조회(읽기)를 `command`/`query` 패키지로 나누고, 각 측의 영속성 구현 기법(command `*Adapter` ↔ query `*DaoImpl`)까지 구분한다. **이 `command`/`query` 패키지 분리는 이후 `user`·`match`·`coin`·`scheduler` 도메인에도 동일하게 적용했다.** (아래 구조는 chat 예시이며, 다른 도메인도 같은 골격을 따른다. 단 조회 구현 기법은 도메인마다 다를 수 있다 — 조인이면 QueryDSL `*DaoImpl`, 전용 리포지토리 위임이면 그 기법을 따른다)

### core (`oneulsogae-core/.../chat/`)

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
    ├── dao/                         # 조회 out-port 인터페이스 (*Dao)
    └── dto/                         # 읽기 모델 (read model / 일급 컬렉션)
```

- 명령·조회 서비스 모두 `application` 대신 **`service`** 패키지명을 쓴다(`command/service`, `query/service`). (chat 한정 — user/match/coin은 `command/application`, `query/service`를 쓴다)
- 조회 측은 `port/out` 대신 **`dao`(조회 인터페이스, `*Dao`) + `dto`(읽기 모델)**로 둔다. dao는 도메인이 아니라 read model만 반환한다.
- read model(`query/dto`): `ChatRoomSummary`(목록), `ChatRoomDetail`(상세), `ChatRoomView`(상세 헤더용 방 상태), `ChatParticipant`(+`ChatParticipants`), `ChatMessageView`(+ 일급 컬렉션 `ChatMessageViews`). 일급 컬렉션은 감싸는 read model에 맞춰 명명한다(`ChatMessageViews` ⊃ `ChatMessageView`).
- `<Domain>ErrorCode`는 command 도메인과 query read model 양쪽이 쓰므로 **도메인 루트**(예 `com.org.oneulsogae.core.chat.ChatErrorCode`, `com.org.oneulsogae.core.coin.CoinErrorCode`)에 둔다. (command에 두면 query→command 결합이 생긴다)

### infra (`oneulsogae-infra/.../chat/`)

```
chat/
├── command/
│   ├── adapter/      # out-port 구현체 *Adapter (Spring Data JPA 메서드 쿼리) — core·chatting 어댑터
│   ├── entity/       # JPA 엔티티
│   ├── mapper/       # toDomain/toEntity (+ chatting용 toChattingDomain/toChattingEntity)
│   └── repository/   # Spring Data JpaRepository
└── query/            # dao 구현체 *DaoImpl (QueryDSL 또는 전용 리포지토리 위임)
```

- **out-port 구현체 = `*Adapter`**, **Spring Data JPA 메서드 쿼리**로 구현한다.
- **dao 구현체 = `*DaoImpl`**(인터페이스명 + `Impl`). 자기 도메인 조인은 **QueryDSL**, 단순 조회는 전용 리포지토리에 위임한다. (별도 QueryDSL 어댑터(`<Entity>Query...Adapter`) 대신 이 `*DaoImpl`을 쓴다)
- **같은 단건 조회가 command·query 양쪽에 있어도 공유하지 않고 각자 구현**한다. command adapter는 메서드 쿼리로, query daoImpl은 QueryDSL(또는 위임)로 둔다.
  - 예: 방 단건 — command `GetChatRoomPort.findById`(메서드 쿼리) ↔ query `ChatRoomDao.findById`→`ChatRoomView`(QueryDSL). 참가자 존재 — command `GetChatRoomMemberPort` ↔ query `ChatRoomMemberDao.existsByChatRoomIdAndUserId`(QueryDSL).
- `entity`·`mapper`·`repository`는 `command` 아래 두고 query daoImpl이 이를 참조한다(infra 내부 query→command 참조는 허용).
- **command 어댑터는 엔티티별로 하나만 둔다.** 같은 테이블을 쓰는 여러 모듈(core·chatting, core·scheduler)의 out-port를 한 어댑터에서 함께 구현한다. ([영속성 어댑터 구성](#영속성-어댑터-구성-엔티티-단위-querydsl-분리) 참고)

#### chat 인터페이스 → 구현체 매핑

엔티티별 command 어댑터는 **core·chatting 두 모듈의 out-port를 한 클래스에서 함께 구현**한다(모듈별로 쪼개지 않는다). 단순명이 겹치는 포트·도메인(`SaveChatMessagePort`, `GetChatRoomMemberPort`, `ChatMessage`)은 chatting 쪽을 import alias로 구분한다.

| 구분 | 인터페이스 (core / chatting) | 구현체 (infra) | 기법 |
|---|---|---|---|
| command | core `SaveChatRoomPort`·`GetChatRoomPort` / chatting `UpdateChatRoomPort` | `ChatRoomAdapter` | Spring Data 메서드 쿼리 |
| command | core `SaveChatMessagePort` / chatting `SaveChatMessagePort` | `ChatMessageAdapter` | Spring Data 메서드 쿼리 |
| command | core `GetChatRoomMemberPort`·`SaveChatRoomMemberPort` / chatting `GetChatRoomMemberPort`·`IncreaseUnreadCountPort` | `ChatRoomMemberAdapter` | Spring Data 메서드 쿼리 |
| query | `ChatRoomDao` | `ChatRoomDaoImpl` | QueryDSL |
| query | `ChatMessageDao` | `ChatMessageDaoImpl` | QueryDSL |
| query | `ChatParticipantDao` | `ChatParticipantDaoImpl` | QueryDSL |
| query | `ChatRoomMemberDao` | `ChatRoomMemberDaoImpl` | QueryDSL |

### 의존 규칙 & 트레이드오프

- **query는 자기 dao에만 의존한다.** command의 out-port·도메인 모델을 참조하지 않는다(조회 서비스가 command 포트를 주입하지 않는다). 그래서 query는 명령 도메인(`ChatRoom`/`ChatMessage`) 대신 **자체 read model**(`ChatRoomView`/`ChatMessageView`)을 쓴다.
- 같은 데이터를 command 도메인과 query read model로 **이중으로 모델링**하는 비용을 감수하고 command↔query 결합을 끊는다. (CQRS를 적용한 모든 도메인 공통. 예: coin은 command `CoinBalance` ↔ query `CoinBalanceResult`, command `GetCoinBalancePort`(잠금 조회) ↔ query `CoinBalanceDao`)
- 컨트롤러(api)는 command in-port(`SaveChatRoom`/`LeaveChatRoom`/`MarkChatRoomAsReadUseCase`)와 query in-port(`GetChatRooms`/`GetChatRoomDetailUseCase`)를 그대로 주입한다.

## 영속성 어댑터 구성 (엔티티 단위, QueryDSL 분리)

영속성 어댑터는 **엔티티마다 하나씩** 둔다. 같은 엔티티를 **여러 모듈(core, scheduler 등)이 써도 모듈별로 쪼개지 않고**, 한 어댑터에서 각 모듈의 out-port(+ scheduler dao)를 **함께 구현**한다. (chat의 엔티티별 단일 어댑터 규칙과 동일)

- **단, QueryDSL 조회는 별도 어댑터로 분리한다.** Spring Data(파생 쿼리·단건 조회·저장)와 QueryDSL을 한 어댑터에 섞지 않는다.
- 네이밍: `<Entity>Adapter`(Spring Data) / `<Entity>...DaoImpl`·`<Entity>Query...Adapter`(QueryDSL).
- **scheduler out-port도 같은 엔티티 어댑터가 함께 구현한다**(scheduler는 core에 의존하지 않고 자기 포트·dao만 보유). 실제 동작은 core 도메인/엔티티에 위임하며, 둘을 아는 infra가 한 어댑터에서 잇는다. 단순명이 겹치는 포트는 import alias로 구분한다.
- QueryDSL 조회 어댑터에는 `JPAQueryFactory`만 주입한다.
- `chat`·`match`·`coin` 도메인은 이 규칙을 **CQRS 패키지 분리**로 확장했다: command out-port 구현은 `command/adapter`의 `*Adapter`(Spring Data 메서드 쿼리), 조회 dao 구현은 `query`의 `*DaoImpl`로 나눈다(자기 도메인 조인은 QueryDSL, 전용 리포지토리에 위임하면 그 기법을 따른다). (예: command `MatchAdapter`(core `GetMatchPort`·`SaveMatchPort` + scheduler `SaveMatchRecordPort`) ↔ query `MatchWithPartnerDaoImpl`(core `MatchWithPartnerDao`)·`MatchRecordDaoImpl`(scheduler `MatchRecordDao`))

```kotlin
// ✅ command/adapter Spring Data: 한 엔티티 어댑터가 core·scheduler의 command out-port를 함께 구현 (단건·저장)
@Component
class MatchAdapter(
    private val matchJpaRepository: MatchJpaRepository,
    private val matchMemberJpaRepository: MatchMemberJpaRepository,
) : GetMatchPort, SaveMatchPort, SaveMatchRecordPort { ... }

// ✅ query/*DaoImpl: 조회 dao 구현은 query 패키지로 분리한다. (조인은 QueryDSL, JPAQueryFactory만 주입)
@Component
class MatchWithPartnerDaoImpl(
    private val queryFactory: JPAQueryFactory,
) : MatchWithPartnerDao { ... }

// ✅ query/*DaoImpl: 전용 리포지토리에 위임하는 조회 dao도 query 패키지에 둔다. (query→command 리포지토리 참조는 허용)
@Component
class MatchRecordDaoImpl(
    private val matchJpaRepository: MatchJpaRepository,
    private val matchMemberJpaRepository: MatchMemberJpaRepository,
) : MatchRecordDao { ... }
```

### 조회 구현 우선순위 (단순할수록 위쪽)

1. **Spring Data 파생 쿼리(메서드 쿼리)** — 단건/존재/단순 조건 조회. `existsByMaleUserIdAndIntroducedDate(...)`처럼 메서드 이름으로 표현되면 `@Query`(JPQL)를 쓰지 않는다.
2. **QueryDSL** — 동적 컬럼 선택, 엔티티 조인, DTO 프로젝션 등 메서드 쿼리로 표현하기 어려운 복잡 조회. 어댑터에 `JPAQueryFactory`를 주입해 작성한다.
   - 성별 등으로 **조회/상대 컬럼이 갈리면 `if`로 컬럼(`NumberPath` 등)을 동적으로 골라 단일 컬럼 조건**으로 둔다. (`CASE`/`OR` 없이 인덱스 활용)
   - 연관 매핑이 없는 엔티티는 `.join(QOther).on(...)`(명시적 조인)으로 묶는다. (1+N 방지)
3. **`@Query`(JPQL)** — 1·2로 깔끔히 표현되지 않을 때만 쓰고, 이때는 아래 **JPQL 조인** 규칙(명시적 `join ... on`)을 따른다.

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
