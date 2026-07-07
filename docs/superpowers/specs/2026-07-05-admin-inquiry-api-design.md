# 어드민 문의(Inquiry) API 설계

## 배경 / 목적

어드민이 사용자 문의를 관리할 수 있는 API 3종을 추가한다.

1. 문의 목록 페이징 조회 (`GET /admin/v1/inquiries`) — 선택적 `status` 필터
2. 문의 상세 조회 (`GET /admin/v1/inquiries/{id}`)
3. 문의 답변 (`POST /admin/v1/inquiries/{id}/answer`)

기존에 유저용 `inquiry` 도메인(`meeple-core` + `/inquiries/v1`)이 **생성(접수)만** 구현돼 있다(로그인·비로그인 모두 접수). 엔티티 `inquiries` 테이블에는 어드민 답변용 `status`(`PENDING`/`ANSWERED`), `answer`, `answered_at` 컬럼이 **이미 선반영**되어 있다.

## 결정 사항

- **데이터**: 어드민 문의는 기존 `inquiries` 테이블/`InquiryEntity`/`QInquiryEntity`를 **공유·재사용**한다. 새 테이블·컬럼 없음.
- **답변 알림/이메일**: 이번 범위에서 **발송하지 않는다**. `answer`/`answered_at` 저장 + `status=ANSWERED` 전환만 수행.
- **목록 필터**: `status`(`PENDING`/`ANSWERED`) **선택적** 필터만. 미지정 시 전체. 카테고리 필터는 범위 밖.
- **재답변**: **불허**. 이미 `ANSWERED`인 문의에 답변 시도하면 에러(`INQUIRY_ALREADY_ANSWERED`). 답변은 `PENDING`에서 1회만.
- **read model 필드**:
  - 목록 행: `id`, `category`, `status`, `email`, `createdAt` (본문 `message` 제외 — 가벼운 목록, 공지 패턴)
  - 상세: `id`, `userId`, `category`, `email`, `message`, `status`, `answer`, `answeredAt`, `createdAt`
- **기존 `/inquiries/v1` POST**: 그대로 둔다.

## 아키텍처

`meeple-admin`의 self-contained 헥사고날 패턴을 따른다(조회는 `notice`/`report` query, 답변은 `companyverification` command 미러링). 어드민 모듈은 core에 의존하지 않고, 공용 enum `InquiryCategory`/`InquiryStatus`(`meeple-common`)만 사용한다. 영속성은 `meeple-infra`의 DaoImpl/Adapter가 어드민 out-port를 구현한다.

- 어드민 경로 `/admin/**`는 `SecurityConfig`에서 `hasRole("ADMIN")`로 자동 보호된다. 메서드 레벨 어노테이션 불필요.
- 컨트롤러는 core의 `ApiResponse`를 반환한다(기존 어드민 컨트롤러 규약과 동일).
- 어드민 예외는 `AdminException` + `AdminErrorCode` → `AdminExceptionHandler`가 처리.

### API 명세

| 메서드 | 경로 | 요청 | 응답 |
|---|---|---|---|
| `GET` | `/admin/v1/inquiries?page=0&size=20&status=PENDING` | query `page`, `size`, `status`(optional) | `ApiResponse<AdminInquiryPageResponse>` |
| `GET` | `/admin/v1/inquiries/{id}` | path `id` | `ApiResponse<AdminInquiryDetailResponse>` |
| `POST` | `/admin/v1/inquiries/{id}/answer` | path `id`, body `{answer}` | `ApiResponse<Unit>` (성공만) |

- 페이징: `page`(default 0, `coerceAtLeast(0)`), `size`(default 20, `coerceIn(1, 100)`). offset/limit 직접 계산(기존 프로젝트 규약, Spring Data `Pageable` 미사용).
- 정렬: `created_at desc, id desc`.
- `status`: `InquiryStatus?`(nullable). null이면 전체, 값 있으면 `where status = ?`. 잘못된 enum 문자열은 Spring이 400 반환.
- 상세 404: `findDetailById(id)`가 null이면 `AdminException(AdminErrorCode.INQUIRY_NOT_FOUND)`.
- 페이징 응답 규약: `content, page, size, totalElements, totalPages, hasNext`(`totalPages`/`hasNext`는 파생값).
- 답변 API 응답: 어드민 규약상 `ApiResponse.success()`(본문 없는 성공).

## 컴포넌트 (파일 구성)

### meeple-admin (`com.org.meeple.admin.inquiry`)

**Query (조회)**
- `query/service/port/in/GetAdminInquiriesUseCase.kt`
  - `fun getInquiries(page: Int, size: Int, status: InquiryStatus?): AdminInquiryPage`
  - `fun getInquiry(id: Long): AdminInquiryDetailView`
- `query/service/GetAdminInquiriesService.kt` — `@Service @Transactional(readOnly = true)`, UseCase 구현. offset/limit 계산 후 dao 호출(status 전달), 상세 null → `INQUIRY_NOT_FOUND`.
- `query/dao/GetAdminInquiryDao.kt` (out-port)
  - `fun findPage(offset: Long, limit: Int, status: InquiryStatus?): AdminInquiryViews`
  - `fun count(status: InquiryStatus?): Long`
  - `fun findDetailById(id: Long): AdminInquiryDetailView?`
- `query/dto/AdminInquiryView.kt` — 목록 행: `id: Long, category: InquiryCategory, status: InquiryStatus, email: String, createdAt: LocalDateTime`
- `query/dto/AdminInquiryViews.kt` — 일급 컬렉션 (`values: List<AdminInquiryView>`)
- `query/dto/AdminInquiryDetailView.kt` — `id, userId: Long?, category, email, message, status, answer: String?, answeredAt: LocalDateTime?, createdAt`
- `query/dto/AdminInquiryPage.kt` — `views: AdminInquiryViews, page: Int, size: Int, totalElements: Long` + 파생 `totalPages`, `hasNext`

**Command (답변)**
- `command/application/port/in/AnswerInquiryUseCase.kt` — `fun answer(command: AnswerInquiryCommand)`
- `command/application/port/in/command/AnswerInquiryCommand.kt` — `inquiryId: Long, answer: String`
- `command/application/AnswerInquiryService.kt` — `@Service @Transactional`, `AnswerInquiryUseCase` 구현.
  - `GetAdminInquiryPort.findById(id)` → null이면 `INQUIRY_NOT_FOUND`.
  - `TimeGenerator.now()`로 `answeredAt` 확보.
  - 도메인 `AdminInquiry.answer(content, now)` 호출(상태 검증 캡슐화) → 결과를 `AnswerAdminInquiryPort.answer(...)`로 저장.
- `command/application/port/out/GetAdminInquiryPort.kt` — `fun findById(id: Long): AdminInquiry?` (없거나 soft-delete면 null)
- `command/application/port/out/AnswerAdminInquiryPort.kt` — `fun answer(id: Long, answer: String, answeredAt: LocalDateTime)` (`answer`/`answered_at` 저장 + `status=ANSWERED`)
- `command/domain/AdminInquiry.kt` — 최소 도메인 모델 `AdminInquiry(id: Long, status: InquiryStatus)`.
  - `fun answer(content: String, now: LocalDateTime): AnsweredInquiry` — `status != PENDING`이면 `AdminException(INQUIRY_ALREADY_ANSWERED)` throw. 통과 시 답변 값(`id, content, answeredAt=now`)을 담은 결과 반환. (서비스에 `if…throw` 나열 대신 도메인이 상태 전이 규칙을 캡슐화)
  - core `Inquiry`에 의존하지 않는 admin 자체 도메인(답변 검증에 필요한 `id`/`status`만 보유).

**Common**
- `common/error/AdminErrorCode.kt`에 `INQUIRY_NOT_FOUND`(404), `INQUIRY_ALREADY_ANSWERED`(409) 추가.

### meeple-infra

- `infra/inquiry/query/GetAdminInquiryDaoImpl.kt` — `@Repository`, `GetAdminInquiryDao` 구현. `JPAQueryFactory` + `QInquiryEntity` 재사용.
  - `findPage`: `Projections.constructor(AdminInquiryView::class.java, id, category, status, email, createdAt)` + status 동적 필터(BooleanBuilder 또는 nullable `where`) + `orderBy(createdAt.desc(), id.desc())` + offset/limit.
  - `count`: `select(inquiry.count())` + 동일 status 동적 필터.
  - `findDetailById`: `Projections.constructor(AdminInquiryDetailView::class.java, ...)` `where id.eq(...)` `fetchOne()`.
  - soft delete는 엔티티 `@SQLRestriction("deleted_at is null")`이 자동 제외.
- 기존 `infra/inquiry/command/adapter/InquiryAdapter.kt`가 `GetAdminInquiryPort` + `AnswerAdminInquiryPort`도 함께 구현한다(엔티티당 어댑터 하나 규칙).
  - `findById`: `InquiryJpaRepository.findById(id)` → `AdminInquiry(id, status)` 변환(없으면 null).
  - `answer`: `findById(id)` 로드 → `entity.answer = answer; entity.answeredAt = answeredAt; entity.status = ANSWERED` → `save`. (companyverification의 load→필드수정→save 패턴과 동일)

> infra는 이미 `meeple-admin`에 의존한다. 별도 의존성 추가 불필요.

### meeple-api (`com.org.meeple.api.admin`)

- `AdminInquiryController.kt` — `@RestController @RequestMapping("/admin/v1/inquiries")`. `GetAdminInquiriesUseCase`, `AnswerInquiryUseCase` 주입.
  - `GET` 목록(`page`, `size`, `status`), `GET /{id}` 상세, `POST /{id}/answer` 답변.
- `request/AnswerInquiryRequest.kt` — `answer: String` + `toCommand(inquiryId: Long)`. `@field:NotBlank`, `@field:Size(max = 2000)`(컬럼 길이 일치).
- `response/AdminInquiryPageResponse.kt` — `of(AdminInquiryPage)` 팩터리. `content: List<AdminInquiryResponse>` + 페이지 메타.
- `response/AdminInquiryResponse.kt` — 목록 행 (`id, category, status, email, createdAt`).
- `response/AdminInquiryDetailResponse.kt` — 상세. `of(AdminInquiryDetailView)`.

## 데이터 흐름

**목록**: Controller(page, size, status) → `GetAdminInquiriesUseCase.getInquiries` → offset/limit 계산 → `GetAdminInquiryDao.findPage`/`count`(QueryDSL, status 동적 필터) → `AdminInquiryPage` → `AdminInquiryPageResponse.of` → `ApiResponse`.

**상세**: Controller(id) → `getInquiry(id)` → `findDetailById` → null이면 `INQUIRY_NOT_FOUND` throw → `AdminInquiryDetailView` → `AdminInquiryDetailResponse.of`.

**답변**: Controller(id, request) → `toCommand(id)` → `AnswerInquiryUseCase.answer` → `GetAdminInquiryPort.findById`(null → `INQUIRY_NOT_FOUND`) → `AdminInquiry.answer(content, now)`(status ≠ PENDING → `INQUIRY_ALREADY_ANSWERED`) → `AnswerAdminInquiryPort.answer` → `InquiryAdapter`가 엔티티 갱신 저장.

## 에러 처리

- 상세/답변 대상 없음: `AdminErrorCode.INQUIRY_NOT_FOUND` (404).
- 이미 답변된 문의 재답변: `AdminErrorCode.INQUIRY_ALREADY_ANSWERED` (409).
- 답변 입력 검증: 요청 DTO `AnswerInquiryRequest`의 `@NotBlank`/`@Size(max = 2000)`로 처리. 위반 시 전역 검증 핸들러가 400 반환.
- 잘못된 `status` enum 문자열: Spring 파라미터 바인딩이 400 반환.
- 인가: `/admin/**` 경로로 `hasRole("ADMIN")` 자동 처리(설계 대상 아님).

## 인덱스

- 목록 필터/정렬 기준은 `where status = ? order by created_at desc, id desc`. 현재 `inquiries`엔 `idx_user_id`만 있어 status 필터 시 filesort 발생 가능.
- **복합 인덱스 `idx_inquiries_status_created_at (status, created_at, id)`를 추가**한다(`@Table` `indexes`에 선언). 동등 조건(`status`) → 정렬 컬럼(`created_at`, `id`) 순서로 seek 가능.
  - status 지정 경로: `(status, created_at, id)`로 seek + 정렬 회피.
  - status 미지정(전체) 경로: 이 인덱스의 `status` prefix를 못 타 filesort 가능하나, 어드민 저볼륨이라 허용. (전체 정렬 최적화가 필요해지면 별도 `(created_at, id)` 인덱스 재검토.)
- 실DB DDL 반영이 필요하다(엔티티 `indexes` 선언 + 운영 DB 인덱스 추가).

## 테스트

- **E2E** (`meeple-api`, `AbstractIntegrationSupport` 상속): `AdminInquiryE2ETest`
  - 목록 페이징(정렬·페이지 필드), `status` 필터(PENDING/ANSWERED/미지정), 상세 조회, 답변 성공 후 상세 재조회(status=ANSWERED·answer·answeredAt 확인), 이미 답변된 문의 재답변 409, 존재하지 않는 id 404, 잘못된 답변 입력 400.
  - 픽스처는 infra `testFixtures`의 문의 엔티티 픽스처 사용(없으면 신설 — `InquiryEntityFixture`).
- **도메인 유닛** (Kotest):
  - `AdminInquiry` — `answer()` 상태 전이/검증(PENDING → 답변 성공, ANSWERED → 예외).
  - `AdminInquiryPage` — 페이징 메타(`totalPages`/`hasNext`) 계산.

## 범위 밖 (하지 않음)

- 답변 시 문의자 알림/이메일 발송.
- 카테고리 필터, 검색(이메일/본문), 정렬 옵션.
- 답변 수정/재답변, 문의 삭제·상태 수동 변경.
- 유저용 문의 조회(내 문의 목록/상세) API.
- 추가 컬럼·스키마 변경(답변자 식별 등).
