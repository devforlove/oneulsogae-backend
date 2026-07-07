# meeple-admin 모듈 분리 설계

- 날짜: 2026-07-05
- 상태: 승인 대기
- 승인된 접근: **A안 — meeple-admin 자립 모듈(core 미의존)**

## 배경 / 목적

어드민 전용으로 추가된 UseCase·Service·구현체가 운영(일반 사용자) 코드와 같은 모듈(`meeple-core`)에 섞여 있다. 운영 core와 어드민 비즈니스 코드를 물리적으로 분리하기 위해 새 Gradle 모듈 `meeple-admin`을 만든다.

사용자가 정한 원칙:

- `meeple-api`, `meeple-infra`는 운영 코드와 **동일하게 공유**한다(어드민 컨트롤러·어댑터는 그대로 이 모듈들에 둔다).
- `meeple-core`는 **운영 전용**으로 유지한다.
- **어드민은 `meeple-core`를 의존하지 않는다.** 어드민의 "core 역할"(도메인/유스케이스/서비스/포트)은 새 `meeple-admin` 모듈이 맡는다. `meeple-common`만 의존한다. (`meeple-chatting`·`meeple-scheduler`와 동일한 자립-모듈 컨벤션.)

## 이동 대상 (현재 → 이후)

### meeple-core에서 meeple-admin으로 이동 (repackage)

대시보드 (기존 `core.admin.query.*`) → `com.org.meeple.admin.dashboard.query.*`

| 현재 | 이후 |
|---|---|
| `core.admin.query.service.port.in.GetAdminDashboardUseCase` | `admin.dashboard.query.service.port.in.GetAdminDashboardUseCase` |
| `core.admin.query.service.GetAdminDashboardService` | `admin.dashboard.query.service.GetAdminDashboardService` |
| `core.admin.query.dao.GetAdminDashboardDao` | `admin.dashboard.query.dao.GetAdminDashboardDao` |
| `core.admin.query.dto.AdminDashboardView` | `admin.dashboard.query.dto.AdminDashboardView` |

신고 어드민 조회 (기존 `core.report.query.*` 중 Admin 계열) → `com.org.meeple.admin.report.query.*`

| 현재 | 이후 |
|---|---|
| `core.report.query.service.port.in.GetAdminReportsUseCase` | `admin.report.query.service.port.in.GetAdminReportsUseCase` |
| `core.report.query.service.GetAdminReportsService` | `admin.report.query.service.GetAdminReportsService` |
| `core.report.query.dao.GetAdminReportDao` | `admin.report.query.dao.GetAdminReportDao` |
| `core.report.query.dto.AdminReportDetailView` | `admin.report.query.dto.AdminReportDetailView` |
| `core.report.query.dto.AdminReportSummaryView` | `admin.report.query.dto.AdminReportSummaryView` |
| `core.report.query.dto.AdminReportSummaryViews` | `admin.report.query.dto.AdminReportSummaryViews` |
| `core.report.query.dto.AdminReportPage` | `admin.report.query.dto.AdminReportPage` |

> 이 이동 후 `core/report/query` 패키지에는 어드민 코드만 있었으므로 **비게 된다**. `core.report`에는 `command`(운영: 신고 생성)와 공유 자원(`ReportEntity`는 infra, `ReportErrorCode`는 core)만 남는다. 빈 디렉터리는 삭제한다.

### 클래스명 유지

`Admin*` 접두어는 all-admin 모듈에선 다소 중복이지만, 이름을 바꾸면 이를 참조하는 api 응답 DTO(`AdminReportDetailResponse` 등)까지 파급된다. **surgical change 원칙에 따라 클래스명은 그대로 두고 패키지만 옮긴다.**

## meeple-admin이 신설·소유할 core 대체 primitive

core 미의존 제약을 지키기 위해, 어드민 서비스가 쓰던 core 공유 타입 2가지를 admin 자체 것으로 대체한다.

### 1) 시간 추상화 — `admin.common.time`

- `TimeGenerator` (in-module 포트): `now(): LocalDateTime`, `today(): LocalDate = now().toLocalDate()`. core `TimeGenerator`와 동일 시그니처.
- `SystemAdminTimeGenerator` (`@Component`, `LocalDateTime.now()` 직접 호출): scheduler `SystemBatchTimeGenerator`·chatting `SystemChatTimeGenerator`와 동일하게 **모듈 내부에 impl을 둔다**(infra 불필요). CLAUDE.md의 "직접 호출은 SystemTimeGenerator 구현체에 한정" 규칙에 부합.
- `GetAdminDashboardService`는 이 admin `TimeGenerator`를 주입받는다(현재 로직 `timeGenerator.today().atStartOfDay()` 그대로).

### 2) 에러 추상화 — `admin.common.error`

core `GlobalExceptionHandler`(@RestControllerAdvice)는 core `BusinessException`만 잡으므로, admin이 core를 의존하지 않으려면 자체 예외와 핸들러가 필요하다. core의 `ErrorCode`/`BusinessException` 패턴을 축소 복제한다.

- `AdminErrorCode` (enum): core `ErrorCode` 계약(`code`, `message`, `status: HttpStatus`)을 그대로 본떠 admin 자체 정의. 현재 유일 항목 `REPORT_NOT_FOUND("REPORT-001", "신고를 찾을 수 없습니다.", HttpStatus.NOT_FOUND)`. **코드 문자열 "REPORT-001"은 기존 `ReportErrorCode.REPORT_NOT_FOUND`와 동일하게 유지한다** — `AdminReportDetailE2ETest`가 `body("error.code", "REPORT-001")`을 검증하므로 응답 계약을 그대로 보존한다.
- `AdminException(errorCode: AdminErrorCode, message)`: core `BusinessException` 축소 복제.
- `GetAdminReportsService.getReport`의 `throw BusinessException(ReportErrorCode.REPORT_NOT_FOUND, …)`를 `throw AdminException(AdminErrorCode.REPORT_NOT_FOUND, …)`로 교체.
- `HttpStatus` 사용을 위해 meeple-admin은 `spring-web`을 의존한다(core도 동일). 이는 spring 라이브러리 의존일 뿐 meeple-core 의존이 아니다.

### meeple-api에 추가

- `AdminExceptionHandler` (`@RestControllerAdvice`): `AdminException`을 잡아 `errorCode.status` + 본문으로 응답. api의 기존 `MultipartExceptionHandler`·`NoResourceFoundExceptionHandler` 선례와 동일한 위치·스타일(`api/common` 또는 `api/admin`). 응답 포맷은 core `GlobalExceptionHandler`가 `BusinessException`을 변환하던 포맷과 동일하게 맞춘다.

## 모듈 의존 방향 (변경 후)

```
meeple-admin ──> common, (spring-context, spring-tx, spring-web)   [어드민 core 역할. meeple-core 미의존]
meeple-api   ──> ..., meeple-admin        [어드민 컨트롤러가 admin UseCase 주입. 신규 의존 추가]
meeple-infra ──> ..., meeple-admin        [어드민 DAO 구현이 admin out-port 구현. 신규 의존 추가]
```

- `settings.gradle.kts`에 `include("meeple-admin")` 추가.
- `meeple-admin/build.gradle.kts`: `implementation(project(":meeple-common"))` + `spring-context` + `spring-tx` + `spring-web`. (scheduler build를 기준으로 tx·web 추가)
- `meeple-api/build.gradle.kts`, `meeple-infra/build.gradle.kts`에 `implementation(project(":meeple-admin"))` 추가.
- 컴포넌트 스캔: `@SpringBootApplication`이 `com.org.meeple`에 있어 `com.org.meeple.admin.*`의 `@Service`/`@Component`가 자동 등록된다(별도 스캔 설정 불필요).

## meeple-api / meeple-infra의 import 재지정 (파일 이동 없음)

- `api/admin/AdminDashboardController`, `api/admin/AdminReportController`: UseCase import를 `core.*` → `admin.*`로 변경. `ApiResponse`(core.common.response)는 그대로(api는 core 의존).
- `api/admin/response/*` 응답 DTO: View import를 `core.report.query.dto.*` → `admin.report.query.dto.*`로 변경.
- `infra/admin/query/GetAdminDashboardDaoImpl`: dao/dto import를 `core.admin.query.*` → `admin.dashboard.query.*`로 변경. 참조 엔티티(Q*)는 전부 infra 소유라 무변경.
- `infra/report/query/GetAdminReportDaoImpl`: dao/dto import를 `core.report.query.*` → `admin.report.query.*`로 변경. 참조 엔티티(Q*)는 infra 소유라 무변경.

## 이동 제외 (그대로 유지)

- `meeple-common`의 `Role`(USER/ADMIN), `ReportStatus`/`ReportType` enum — 공유 유지.
- `SecurityConfig`의 `.requestMatchers("/admin/**").hasRole("ADMIN")`, OAuth2 로그인 오리진 분기(`OAuth2SuccessHandler`/`LoginOriginCookieFactory`/`LoginOriginCookieFilter`/`OAuth2Properties`) — api/auth가 소유한 **공유 설정**이므로 이번 분리 대상 아님.
- core의 `TimeGenerator`/`BusinessException`/`ErrorCode`/`ReportErrorCode`/`GlobalExceptionHandler` — 운영 core가 계속 사용하므로 **무변경**(admin은 자체 복제본 사용).

## 테스트 영향

- E2E(`meeple-api/src/test`의 `AdminDashboardE2ETest`, `AdminReportDetailE2ETest`, `AdminReportListE2ETest`, `api/auth/AdminAccessE2ETest`): 실서버 HTTP를 두드리므로 **동작 불변**. core admin 타입을 직접 import하는 곳이 있으면 import만 `admin.*`로 재지정.
- 신규 admin primitive(`AdminErrorCode`/`AdminException`/`TimeGenerator`)는 로직이 거의 없으므로 별도 유닛 테스트를 강제하지 않는다. `AdminException` → 404 매핑은 기존 `AdminReportDetailE2ETest`의 "없는 신고 id면 404"·"팀 신고 id면 404" 케이스(둘 다 `error.code == "REPORT-001"` 검증)로 커버된다. 신규 테스트 불필요.

## 성공 기준 (검증)

1. `./gradlew build`(또는 전체 컴파일) 성공 — 모든 import 재지정·신규 모듈 컴파일 통과.
2. `meeple-admin`이 `meeple-core`를 의존하지 않음 — build.gradle에 core 없음 + admin 소스에 `com.org.meeple.core.*` import 0건.
3. 어드민 E2E 전부 통과(대시보드 조회, 신고 목록/상세, 신고 단건 404, 어드민 접근 제어).
4. 운영 core 파일 무변경(diff에 `meeple-core`의 비-admin 파일 변경 없음), `core/report/query`·`core/admin` 빈 패키지 정리됨.

## 리스크 / 미결

- `AdminException` 응답 포맷을 core `GlobalExceptionHandler`의 `BusinessException` 응답과 **동일하게** 맞추지 않으면 어드민 프론트가 파싱하는 에러 응답 스키마가 달라질 수 있다 → api의 `AdminExceptionHandler`가 core의 `ApiResponse.error(ErrorResponse.of(...))`를 그대로 재사용해 본문 구조를 동일하게 유지한다(api는 core 의존).
