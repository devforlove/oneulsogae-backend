# 어드민 공지(Notice) API 설계

## 배경 / 목적

어드민이 공지사항을 관리할 수 있는 API 3종을 추가한다.

1. 공지 목록 페이징 조회 (`GET /admin/v1/notices`)
2. 공지 상세 조회 (`GET /admin/v1/notices/{id}`)
3. 공지 추가 (`POST /admin/v1/notices`)

기존에 유저용 `notice` 도메인(`meeple-core` + `/notices/v1`)이 이미 존재하며 **생성 + 목록 페이징 조회**가 구현돼 있다(상세 조회 없음). 엔티티는 `notices` 테이블(`title`, `description`, `created_at`).

## 결정 사항

- **데이터**: 어드민 공지는 기존 `notices` 테이블/`NoticeEntity`/`QNoticeEntity`를 **공유·재사용**한다. 어드민이 쓰고 유저가 `/notices/v1`로 읽는 동일 데이터.
- **필드**: 현재 필드만 사용 (`title`, `description`, `created_at`). 추가 컬럼 없음.
- **기존 `/notices/v1` POST**: 그대로 둔다. 어드민에는 **별도 add**를 신설한다.
- **read model 필드**:
  - 목록 행: `id`, `title`, `createdAt` (본문 `description` 제외 — 가벼운 목록)
  - 상세: `id`, `title`, `description`, `createdAt`

## 아키텍처

`meeple-admin`의 self-contained 헥사고날 패턴을 따른다(기존 `companyverification` 서브도메인을 미러링). 어드민 모듈은 core에 의존하지 않고, 영속성은 `meeple-infra`의 DaoImpl/Adapter가 어드민 out-port를 구현한다.

- 어드민 경로 `/admin/**`는 `SecurityConfig`에서 `hasRole("ADMIN")`로 자동 보호된다. 메서드 레벨 어노테이션 불필요.
- 컨트롤러는 core의 `ApiResponse`를 반환한다(기존 어드민 컨트롤러 규약과 동일).
- 어드민 예외는 `AdminException` + `AdminErrorCode` → `AdminExceptionHandler`가 처리.

### API 명세

| 메서드 | 경로 | 요청 | 응답 |
|---|---|---|---|
| `GET` | `/admin/v1/notices?page=0&size=20` | query param `page`, `size` | `ApiResponse<AdminNoticePageResponse>` |
| `GET` | `/admin/v1/notices/{id}` | path `id` | `ApiResponse<AdminNoticeDetailResponse>` |
| `POST` | `/admin/v1/notices` | body `{title, description}` | `ApiResponse<Unit>` (성공만) |

- 페이징: `page`(default 0, `coerceAtLeast(0)`), `size`(default 20, `coerceIn(1, 100)`). offset/limit 직접 계산(Spring Data `Pageable` 미사용, 기존 프로젝트 규약).
- 정렬: `created_at desc, id desc`.
- 상세 404: `findDetailById(id)`가 null이면 `AdminException(AdminErrorCode.NOTICE_NOT_FOUND)`.
- 페이징 응답 규약: `content, page, size, totalElements, totalPages, hasNext` (`totalPages`/`hasNext`는 파생값).
- 추가 API 응답: 기존 규약상 어드민 `ApiResponse.success()`(본문 없는 성공)를 반환한다. 생성 id 반환은 하지 않는다(기존 core `CreateNotice`가 별도 응답 없이 성공 반환하는 것과 일치, YAGNI).

## 컴포넌트 (파일 구성)

### meeple-admin (`com.org.meeple.admin.notice`)

**Query (조회)**
- `query/service/port/in/GetAdminNoticesUseCase.kt`
  - `fun getNotices(page: Int, size: Int): AdminNoticePage`
  - `fun getNotice(id: Long): AdminNoticeDetailView`
- `query/service/GetAdminNoticesService.kt` — `@Service @Transactional(readOnly = true)`, UseCase 구현. offset/limit 계산 후 dao 호출, 상세 null → `NOTICE_NOT_FOUND`.
- `query/dao/GetAdminNoticeDao.kt` (out-port)
  - `fun findPage(offset: Long, limit: Int): AdminNoticeViews`
  - `fun count(): Long`
  - `fun findDetailById(id: Long): AdminNoticeDetailView?`
- `query/dto/AdminNoticeView.kt` — 목록 행: `id: Long, title: String, createdAt: LocalDateTime`
- `query/dto/AdminNoticeViews.kt` — 일급 컬렉션 (`values: List<AdminNoticeView>`)
- `query/dto/AdminNoticeDetailView.kt` — `id, title, description, createdAt`
- `query/dto/AdminNoticePage.kt` — `views: AdminNoticeViews, page: Int, size: Int, totalElements: Long` + 파생 `totalPages`, `hasNext`

**Command (추가)**
- `command/application/port/in/CreateAdminNoticeUseCase.kt` — `fun create(command: CreateAdminNoticeCommand)`
- `command/application/port/in/command/CreateAdminNoticeCommand.kt` — `title: String, description: String`
- `command/application/CreateAdminNoticeService.kt` — `@Service @Transactional`, 도메인 생성 후 `SaveAdminNoticePort.save()`
- `command/application/port/out/SaveAdminNoticePort.kt` — `fun save(notice: AdminNotice)`
- `command/domain/AdminNotice.kt` — 도메인 모델. `companion object { fun create(title, description): AdminNotice }` + `validateNotice(...)`(title/description 공백·길이 검증, 위반 시 `AdminException(AdminErrorCode.INVALID_NOTICE)`). 길이 규칙은 엔티티 제약(`title` 200, `description` 2000)에 맞춘다.

**Common**
- `common/error/AdminErrorCode.kt`에 `NOTICE_NOT_FOUND`(404), `INVALID_NOTICE`(400) 추가.

### meeple-infra

- `infra/notice/query/GetAdminNoticeDaoImpl.kt` — `@Repository`, `GetAdminNoticeDao` 구현. `JPAQueryFactory` + `QNoticeEntity` 재사용.
  - `findPage`: `Projections.constructor(AdminNoticeView::class.java, id, title, createdAt)` + `orderBy(createdAt.desc(), id.desc())` + offset/limit.
  - `count`: `select(notice.count())`.
  - `findDetailById`: `Projections.constructor(AdminNoticeDetailView::class.java, ...)` `where id.eq(...)` `fetchOne()`.
- 기존 `infra/notice/command/adapter/NoticeAdapter.kt`가 `SaveAdminNoticePort`도 함께 구현한다(엔티티당 어댑터 하나 규칙). `AdminNotice → NoticeEntity` 변환은 매퍼(`NoticeMapper`에 확장함수 추가 또는 `AdminNoticeMapper` 신설 — 기존 매퍼 스타일 따름).

> infra는 이미 `meeple-admin`에 의존한다(기존 `GetAdminCompanyVerificationDaoImpl`이 어드민 포트를 구현). 별도 의존성 추가 불필요.

### meeple-api (`com.org.meeple.api.admin`)

- `AdminNoticeController.kt` — `@RestController @RequestMapping("/admin/v1/notices")`. `GetAdminNoticesUseCase`, `CreateAdminNoticeUseCase` 주입.
  - `GET` 목록, `GET /{id}` 상세, `POST` 추가.
- `request/CreateAdminNoticeRequest.kt` — `title: String, description: String` + `toCommand()`.
- `response/AdminNoticePageResponse.kt` — `of(AdminNoticePage)` 팩터리. `content: List<AdminNoticeResponse>` 등.
- `response/AdminNoticeResponse.kt` — 목록 행 (`id, title, createdAt`).
- `response/AdminNoticeDetailResponse.kt` — 상세 (`id, title, description, createdAt`). `of(AdminNoticeDetailView)`.

## 데이터 흐름

**목록**: Controller(page,size) → `GetAdminNoticesUseCase.getNotices` → offset/limit 계산 → `GetAdminNoticeDao.findPage`/`count` (QueryDSL) → `AdminNoticePage` → `AdminNoticePageResponse.of` → `ApiResponse`.

**상세**: Controller(id) → `getNotice(id)` → `findDetailById` → null이면 `NOTICE_NOT_FOUND` throw → `AdminNoticeDetailView` → `AdminNoticeDetailResponse.of`.

**추가**: Controller(request) → `toCommand()` → `CreateAdminNoticeUseCase.create` → `AdminNotice.create()`(검증) → `SaveAdminNoticePort.save` → `NoticeAdapter` → `NoticeEntity` 저장.

## 에러 처리

- 상세 조회 대상 없음: `AdminErrorCode.NOTICE_NOT_FOUND` (404).
- 추가 입력 검증: `AdminNotice.validateNotice`에서 title/description 공백·길이 위반 시 `AdminException(AdminErrorCode.INVALID_NOTICE)` (400).
- 인가: `/admin/**` 경로로 `hasRole("ADMIN")` 자동 처리(설계 대상 아님).

## 인덱스 고려

- 정렬/페이징 기준은 `created_at desc, id desc`. 목록은 전체 조회(필터 없음)라 `created_at`(또는 PK) 순 스캔. 데이터량이 크지 않은 공지 특성상 별도 인덱스 추가는 하지 않는다. (필터 조건이 없어 복합 인덱스 이득 낮음. 향후 대량화 시 `created_at` 인덱스 재검토.)

## 테스트

- **E2E** (`meeple-api`, `AbstractIntegrationSupport` 상속): `AdminNoticeE2ETest`
  - 목록 페이징(정렬·페이지 필드), 상세 조회, 추가 후 재조회, 존재하지 않는 id 404, 비어드민 접근 차단(권한).
  - 픽스처는 infra `testFixtures`의 `NoticeEntity` 픽스처 사용(없으면 추가).
- **도메인 유닛** (Kotest): `AdminNotice` — `create` 정상, 공백/길이 초과 검증 실패.

## 범위 밖 (하지 않음)

- 유저용 `/notices/v1` 상세 조회 추가.
- 공지 수정/삭제 API.
- 추가 필드(상단고정·게시기간 등) 및 스키마 변경.
- 유저용 `/notices/v1` POST 제거·이관.
