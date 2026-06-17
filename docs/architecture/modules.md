# 모듈 구성 및 의존 방향

> CLAUDE.md의 "모듈 구성" 요약에 대한 상세 레퍼런스.

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

## 인증 검증 커널 분리 (meeple-auth)

- JWT 검증·파싱과 인증 주체는 `meeple-auth`에 둔다(`com.org.meeple.auth.jwt.TokenProvider`, `com.org.meeple.auth.PrincipalDetails`).
  `meeple-auth`는 **발급/로그인/쿠키/SecurityConfig를 모르는** 순수 검증 커널이라, 토큰을 검증만 하면 되는 모듈(예: `meeple-chatting`)이 api에 의존하지 않고 재사용할 수 있다.
- 발급·OAuth2 로그인·재발급·쿠키·`SecurityConfig`·MVC 바인딩 등 **인증 서버/게이트웨이 책임은 `meeple-api`**에 남는다(이들이 `meeple-auth`의 `TokenProvider`를 호출한다).

## 단일 부트 앱 (meeple-api)

- `meeple-api`가 **유일한 `@SpringBootApplication`**이며(`meeple-scheduler`는 실행 가능한 부트 앱이 아닌 라이브러리),
  기준 패키지가 `com.org.meeple`라 core 서비스·scheduler 배치 로직·infra 어댑터를 함께 스캔한다.
  (`@EnableScheduling`으로 스케줄러 빈도 활성화)

## 배치/스케줄 모듈 분리 (meeple-scheduler)

- 배치 알고리즘·유스케이스·전용 포트/도메인은 `meeple-scheduler`에 둔다.
  scheduler도 [CQRS 패키지 분리](hexagonal-cqrs.md)처럼 **`command`/`query` 패키지로 분리**한다
  (예: command `com.org.meeple.scheduler.match.command.application.RunDailyMatchBatchService`,
  command `...command.application.port.out.{SaveMatchPoolPort, MatchPoolPort, SaveMatchRecordPort, TimeGenerator}`,
  command `...command.domain.{MatchBatchResult, MatchPoolGroup, MatchPoolByGender}`,
  query `...query.dao.{ActiveUserDao, MatchBatchTargetDao, MatchRecordDao}`,
  query `...query.dto.{ActiveUser, MatchBatchTarget, MatchBatchCursor, MatchedUserIds}`).
- `meeple-scheduler`는 **`meeple-core`에 의존하지 않으므로**, 배치가 쓰는 매칭 이력 기록/조회·시각 등 공유 동작도
  scheduler가 **자기 out-port·dao**(`SaveMatchRecordPort`·`MatchRecordDao`, `TimeGenerator`)로 정의하고, **infra가 core 도메인에 위임해 구현**한다
  (command out-port는 `command/adapter`의 `MatchAdapter` → core `Match.propose`/`GetMatchPort`/`SaveMatchPort`, 조회 dao는 `query`의 `MatchRecordDaoImpl`로 분리). 시각 구현은 scheduler가 직접 제공한다
  (`SystemBatchTimeGenerator`; core의 `SystemTimeGenerator`와 빈 이름이 겹치지 않게 클래스명을 구분).
- `@Scheduled` **크론 트리거**는 `meeple-api`의 `scheduler` 패키지(`com.org.meeple.scheduler.match.MatchBatchScheduler`)에
  `@Component`로 두고, scheduler 모듈의 실행 로직(`MatchBatchJob` → `RunDailyMatchBatchUseCase`)을 호출한다.
  스케줄러는 "언제 실행할지(@Scheduled)"만, 모듈은 "무엇을 실행할지(배치 로직)"만 책임진다.
  초기 단계라 별도 스케줄러 인스턴스 없이 api 프로세스에서 함께 구동한다.
  단, **api를 스케일아웃하면 배치가 인스턴스마다 중복 실행**되므로, 다중 인스턴스 시점엔 ShedLock 등 분산 락이나
  별도 스케줄러 앱 분리가 필요하다.

## 채팅(WebSocket) 모듈 자립 (meeple-chatting)

- `meeple-chatting`은 **`meeple-core`에 의존하지 않는다.** scheduler와 같은 자립 구조로,
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
- 에러는 `...chat.application.ChatErrorCode`(도메인 에러 enum)와 `...common.error.ChatException`(자체 예외)로 두며, core의 `BusinessException`/`ErrorCode`를 쓰지 않는다.
  (core가 도메인 `<Domain>ErrorCode`는 application에, 공용 `BusinessException`은 `common.error`에 두는 것과 같은 배치)
- 영속성은 chatting이 **자기 out-port**로 정의하고, **infra 어댑터가 직접 구현**한다(scheduler는 core 도메인에 위임하지만, chatting은 같은 JPA 엔티티를 **자체 도메인으로 직접 매핑**해 구현한다). 매퍼는 core 매퍼와 이름을 구분한다(`toChattingDomain`/`toChattingEntity`).
  단, chat의 infra 어댑터는 **엔티티별 단일 어댑터**(`ChatRoomAdapter`/`ChatMessageAdapter`/`ChatRoomMemberAdapter`)가 core out-port와 chatting out-port를 함께 구현한다. ([CQRS 패키지 분리](hexagonal-cqrs.md) 참고)
- 시각도 chatting이 자체 제공한다(`SystemChatTimeGenerator`; core·scheduler의 TimeGenerator 빈과 클래스명을 구분).
- **트레이드오프**: 같은 chat 테이블을 core(HTTP 조회/방 생성)와 chatting(WS 발송)이 **각자 도메인으로 다루므로 일부 규칙이 이중화**된다. 자립(별도 인스턴스 분리 용이)의 대가로 수용한다.
- 토큰 검증은 `meeple-auth`의 `TokenProvider`로 하고(STOMP CONNECT 프레임), 핸드셰이크 경로(`/ws/chat/**`)는 `SecurityConfig`에서 permitAll로 열어 둔다.
