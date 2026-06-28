# 문의하기(inquiry) 도메인 — 생성 슬라이스 설계

## 배경 / 목적

meeple-frontend 고객센터 "문의하기" 화면(`src/domains/inquiry`)이 현재 로컬 mock(`BrowserInquiryRepository`)으로만 동작한다. 이 문의를 서버에 저장할 백엔드 `inquiry` 도메인을 헥사고날 + CQRS 패턴(notice 도메인 참조)으로 추가한다.

프론트 문의 폼(`InquiryDraft`) 필드:
- `category`: enum (`ACCOUNT`·`PAYMENT`·`MATCHING`·`REPORT`·`ETC`)
- `email`: 답변 받을 이메일
- `message`: 문의 내용 (10~1000자)

## 범위

- **문의 생성(command) 슬라이스만** 구현한다.
- 프론트에 목록/답변 화면이 아직 없으므로 조회(query)·운영자 답변 API는 만들지 않는다.
- 단, 엔티티에는 `status`/`answer`/`answeredAt` 컬럼을 미리 둬서 추후 운영자 답변·목록 조회를 무리 없이 얹을 수 있게 한다(스키마 선반영, API는 YAGNI).

## 결정 사항

- **작성자 연결**: 인증된 회원의 `userId`를 저장한다. `@LoginUser`로 주입받으므로 **문의 생성은 로그인 필수**다.
- **이메일 보관**: `userId`와 별개로 `email`을 저장한다(답변 수신 이메일이 가입 이메일과 다를 수 있음).
- **상태/답변 컬럼**: `status`(기본 `PENDING`), `answer`(nullable), `answeredAt`(nullable)을 둔다. 현재는 생성 시 `PENDING`·null로만 채워진다.
- **검증 위치**: 도메인 모델의 `validateInquiry(...)`를 불변식의 주 검증으로 둔다(이메일 형식, message 10~1000자). request에는 형식 수준(`@NotNull`/`@NotBlank`/`@Email`/`@Size`)을 둬서 HTTP 경계에서 조기 거절한다.

## 모듈별 구성

### meeple-common — `com.org.meeple.common.inquiry`

`InquiryCategory(val description: String)` — 프론트 `INQUIRY_CATEGORIES`와 1:1:
- `ACCOUNT("계정·로그인")`, `PAYMENT("결제·코인")`, `MATCHING("매칭·채팅")`, `REPORT("신고·이용 제한")`, `ETC("기타 문의")`

`InquiryStatus(val description: String)`:
- `PENDING("대기")`, `ANSWERED("답변완료")`

### meeple-infra — `com.org.meeple.infra.inquiry.command`

`entity/InquiryEntity.kt` — `BaseEntity` 상속(id·createdAt·updatedAt·deletedAt, soft delete `@SQLRestriction("deleted_at is null")`). 테이블 `inquiries`.

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| `user_id` | BIGINT | not null, `idx_user_id` 인덱스 | 문의한 회원 |
| `category` | varchar(50) | not null | `@Enumerated(STRING)` |
| `email` | varchar(255) | not null | 답변 받을 이메일 |
| `message` | varchar(1000) | not null | 문의 내용 |
| `status` | varchar(50) | not null | `@Enumerated(STRING)`, 기본 `PENDING` |
| `answer` | varchar(2000) | nullable | 운영자 답변 |
| `answered_at` | datetime | nullable | 답변 시각 |

- 문의 접수 시각은 별도 컬럼 없이 `BaseEntity.createdAt`으로 갈음한다(notice와 동일).
- `repository/InquiryJpaRepository.kt` — `JpaRepository<InquiryEntity, Long>`
- `mapper/InquiryMapper.kt` — `toDomain()` / `toEntity()` 확장함수
- `adapter/InquiryAdapter.kt` — `@Component`, `SaveInquiryPort` 구현(엔티티당 어댑터 하나)

### meeple-core — `com.org.meeple.core.inquiry.command`

`domain/Inquiry.kt`:
```
data class Inquiry(
  val id: Long = 0,
  val userId: Long,
  val category: InquiryCategory,
  val email: String,
  val message: String,
  val status: InquiryStatus = InquiryStatus.PENDING,
  val answer: String? = null,
  val answeredAt: LocalDateTime? = null,
)
```
- `create(userId, category, email, message)` 팩토리: `validateInquiry(email, message)` 호출 후 `status = PENDING`으로 생성.
- `validateInquiry(email, message)`: 이메일 형식 위반 → `INVALID_EMAIL`, message 길이 < 10 → `MESSAGE_TOO_SHORT`, > 1000 → `MESSAGE_TOO_LONG`. `BusinessException(InquiryErrorCode.*)` 던짐.

`InquiryErrorCode` (`com.org.meeple.core.inquiry`):
- `INVALID_EMAIL("INQ-001", "유효한 이메일 형식이 아닙니다.", BAD_REQUEST)`
- `MESSAGE_TOO_SHORT("INQ-002", "문의 내용은 최소 10자 이상이어야 합니다.", BAD_REQUEST)`
- `MESSAGE_TOO_LONG("INQ-003", "문의 내용은 1000자 이하여야 합니다.", BAD_REQUEST)`

포트:
- `application/port/in/CreateInquiryUseCase.kt` — `fun create(command: CreateInquiryCommand): Inquiry`
- `application/port/in/command/CreateInquiryCommand.kt` — `(userId, category, email, message)`
- `application/port/out/SaveInquiryPort.kt` — `fun save(inquiry: Inquiry): Inquiry`

서비스:
- `application/CreateInquiryService.kt` — `@Service @Transactional`, `SaveInquiryPort` 주입, `saveInquiryPort.save(Inquiry.create(...))`

### meeple-api — `com.org.meeple.api.inquiry`

- `InquiryController.kt` — `@RestController @RequestMapping("/inquiries/v1")`, `@PostMapping` + `@LoginUser user: AuthUser`, `ApiResponse.success(CreateInquiryResponse.of(...))` 반환. `createInquiryUseCase` in-port 주입.
- `request/CreateInquiryRequest.kt` — `category`(`@NotNull`, `InquiryCategory`), `email`(`@NotBlank @Email`), `message`(`@NotBlank @Size(min=10, max=1000)`). `toCommand(userId)` 로 변환.
- `response/CreateInquiryResponse.kt` — `inquiryId: Long`, `of(inquiry)`.

## 데이터 흐름

```
POST /inquiries/v1 (@LoginUser)
  → CreateInquiryRequest (@Valid)
  → request.toCommand(user.id) → CreateInquiryCommand
  → CreateInquiryUseCase.create
  → Inquiry.create(...) [validateInquiry]
  → SaveInquiryPort.save → InquiryAdapter → InquiryJpaRepository.save
  → CreateInquiryResponse(inquiryId)
  → ApiResponse.success
```

## 테스트

- **도메인 유닛(Kotest)**: `Inquiry.create` — 정상 생성 시 `status == PENDING`·`answer == null`; 무효 이메일 → `INVALID_EMAIL`; message 9자 → `MESSAGE_TOO_SHORT`; 1001자 → `MESSAGE_TOO_LONG`; 경계값(10자·1000자) 통과.
- **E2E (`meeple-api`)**: 인증 사용자로 `POST /inquiries/v1` 성공 → 응답 `inquiryId` 존재 + 저장 확인. (필요 시) 무효 입력 400.

## 프론트엔드 연동 안내 (백엔드는 수정하지 않음)

- 엔드포인트: `POST /inquiries/v1` (인증 필요)
- 요청 body: `{ "category": "ACCOUNT", "email": "...", "message": "..." }`
- 응답: `ApiResponse<{ "inquiryId": number }>`
- 대응: `src/domains/inquiry/data/datasources/remote`에 HttpClient 호출 데이터소스를 추가하고 `BrowserInquiryRepository`(로컬 mock)를 이를 사용하도록 교체. `InquiryCategory` 값은 프론트와 동일하므로 매핑 불필요.

## 비포함 (YAGNI)

- 문의 목록/상세 조회(query) API
- 운영자 답변 등록 API (`status`→`ANSWERED`, `answer` 채움)
- 답변 작성자(operator) 식별 컬럼
