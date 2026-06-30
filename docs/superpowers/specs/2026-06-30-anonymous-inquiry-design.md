# 비로그인 고객센터 문의 허용 (하이브리드) 설계

## 배경 / 문제

`meeple-frontend` 하단 푸터에 고객센터 링크가 추가되었다. 로그인 상태에서는 문제가 없으나,
**비로그인 상태**에서 고객센터를 통해 문의를 시도하면 토큰이 없어 실패한다.

현재 백엔드는 `POST /inquiries/v1` 문의 API가 `@LoginUser`로 **인증을 강제**한다:

- `SecurityConfig`에서 `/inquiries/v1`는 화이트리스트에 없어 `anyRequest().authenticated()`가 적용 → 토큰 없으면 **401**.
- `Inquiry.userId`(non-null), `inquiries.user_id`(NOT NULL) — 문의는 항상 회원에 귀속.

즉, 비로그인 문의 401은 설계상 의도된 동작이다.

## 정책 결정

- **하이브리드**: 로그인 사용자는 회원 귀속 문의, 비로그인은 익명 문의 모두 허용한다.
- **API 구조**: 기존 `POST /inquiries/v1` **단일 엔드포인트**를 그대로 사용한다. 토큰 유무를
  모두 수용한다(엔드포인트 분리 안 함 — 컨트롤러/문서 중복 회피).
- **남용 방어**: **두지 않는다(최소 오픈)**. 초기 트래픽이 적고 단일 인스턴스 전제이므로, 필요해지면
  IP 레이트리밋 등으로 추후 보강한다.

## 설계

### 1. 인증 경계 (meeple-api)

- `SecurityConfig`: `/inquiries/v1`를 `permitAll()` 화이트리스트에 추가한다.
  (기존 `/auth/v1/refresh`·`/ws/chat/**` 등과 동일 방식)
- `InquiryController`: 파라미터를 `@LoginUser user: AuthUser?`(nullable)로 받고
  `request.toCommand(user?.id)`를 호출한다.
  `AuthUserArgumentResolver`는 이미 비인증 시 `null`을 반환하므로 해석기 변경은 없다.

### 2. nullable userId 전파 (api → core)

- `CreateInquiryRequest.toCommand(userId: Long?)`
- `CreateInquiryCommand.userId: Long?`
- `Inquiry.userId: Long?` / `Inquiry.create(userId: Long?, ...)`
  - 검증 로직(이메일 형식·메시지 길이)은 그대로. `userId`는 검증 대상이 아니다.
- `CreateInquiryService`는 시그니처 변경 없이 `command.userId`(nullable)를 그대로 전달한다.

### 3. 영속성 (meeple-infra)

- `InquiryEntity.userId: Long?` + `@Column(name = "user_id", nullable = true)`.
- `InquiryMapper`: 타입만 nullable로 맞추면 현재 매핑 그대로 동작한다.
- `idx_user_id` 인덱스는 유지한다. 회원별 문의 조회 의미는 그대로다. (InnoDB 세컨더리 인덱스는 NULL 값도 색인하므로 익명 문의 행도 인덱스에 포함된다)

### 4. DDL 마이그레이션

- `docs/migration/inquiries_user_id_nullable.sql` 추가:
  ```sql
  ALTER TABLE inquiries MODIFY user_id BIGINT NULL;
  ```

## 테스트 계획

### 도메인 유닛 테스트 (`InquiryTest`)

- 익명 생성 케이스 추가: `Inquiry.create(userId = null, ...)`가 정상 생성되고 `userId`가 null인지 확인.
- 기존 검증(이메일 형식·메시지 길이) 케이스는 userId 유무와 무관하게 동작함을 유지 확인.

### E2E 테스트 (`InquiryCreateE2ETest`)

- 비로그인(토큰 없이) 문의 접수 성공: 정상 응답 + 저장된 문의의 `user_id`가 NULL.
- 로그인 사용자 접수(기존): `user_id`가 회원 ID로 채워지는지 유지 확인.

## 프론트엔드 (직접 수정하지 않음 — `meeple-frontend`에 안내)

`meeple-backend`만 수정한다. 프론트엔드는 아래만 직접 반영한다:

- **호출 자체는 변경 불필요**: 비로그인 상태에서도 동일하게 `POST /inquiries/v1`를 호출한다.
  쿠키/토큰이 없으면 백엔드가 익명 문의로 처리한다.
- **비로그인 차단 로직 제거**: 푸터 고객센터에서 로그인 여부와 무관하게 문의 폼으로 진입/제출되도록
  가드(로그인 리다이렉트, 401 사전 차단 등)를 푼다.
- **요청 바디 동일**: `category`, `email`, `message` 3개 필드. 비로그인 사용자는 회신용 `email`을
  직접 입력받는 것이 중요하다(회원 이메일이 없으므로).

## 범위 밖 (Out of scope)

- 스팸/남용 방어(레이트리밋·캡차) — 필요 시 별도 작업.
- 운영자 답변 기능(`status`/`answer`/`answeredAt`) — 기존대로 선반영 필드만 유지.
