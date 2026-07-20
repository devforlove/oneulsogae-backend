# 어드민 회사 이미지 인증 상세조회 API 설계

**작성일:** 2026-07-05
**대상 브랜치:** feat/oneulsogae-admin-module

## 목표

어드민이 특정 직장 서류 인증 한 건을 상세 조회하는 API를 추가한다. 목록(`GET /admin/v1/company-image-verifications`) 항목 필드에, 사용자가 **주장한** 직장 정보(`companyName`·`companyEmail`·`job`)를 더해, 업로드된 서류와 대조해 심사할 수 있게 한다.

기존 목록 조회 슬라이스와 신고 상세(`GET /admin/v1/reports/{id}`)를 미러링한다. 승인/반려 등 심사 처리(command)는 이번 범위 밖(조회만).

## 엔드포인트

```
GET /admin/v1/company-image-verifications/{id}
```

- 없거나 soft-delete된 id면 404(`AdminException`).
- `/admin/v1/**`는 SecurityConfig의 `hasRole(ADMIN)`으로 이미 보호 → 보안 설정 변경 없음.

## 아키텍처 (기존 슬라이스 확장)

`oneulsogae-admin`은 core 비의존 유지. presign은 기존 `CompanyVerificationImageUrlPort`(admin out-port, infra가 S3Presigner로 구현)를 재사용한다.

### oneulsogae-admin — `admin/companyverification/query/`

- `dto/AdminCompanyVerificationDetailView` (신규)
  - 목록 필드: `id: Long, userId: Long, nickname: String?, email: String?, status: CompanyImageVerificationStatus, createdAt: LocalDateTime?, imageKey: String, imageUrl: String? = null`
  - 추가(user_details): `companyName: String?, companyEmail: String?, job: String?`
  - dao는 `imageKey`까지 채우고 `imageUrl`은 null. 서비스가 presign 결과로 `copy(imageUrl = ...)`.
  - QueryDSL `Projections.constructor`가 `imageUrl` 없이 투영하도록 **보조 생성자**(imageUrl 제외 10개 인자 → 본 생성자에 `imageUrl = null` 위임)를 둔다. 필드 순서: id, userId, nickname, email, status, createdAt, imageKey, companyName, companyEmail, job.
- `dao/GetAdminCompanyVerificationDao`에 메서드 추가
  - `findDetailById(id: Long): AdminCompanyVerificationDetailView?` — 없거나 soft-delete면 null.
- `service/port/in/GetAdminCompanyVerificationsUseCase`에 메서드 추가
  - `getVerification(id: Long): AdminCompanyVerificationDetailView`
- `service/GetAdminCompanyVerificationsService`에 구현 추가
  - dao로 상세 조회 → null이면 `AdminException(AdminErrorCode.COMPANY_IMAGE_VERIFICATION_NOT_FOUND, "직장 인증을 찾을 수 없습니다: $id")` → `imageKey`를 presign으로 `imageUrl` 변환해 반환.
- `common/error/AdminErrorCode`에 상수 추가
  - `COMPANY_IMAGE_VERIFICATION_NOT_FOUND("COMPANY-IMAGE-001", "직장 인증을 찾을 수 없습니다.", HttpStatus.NOT_FOUND)`

### oneulsogae-infra — `infra/user/query/`

- `GetAdminCompanyVerificationDaoImpl`에 `findDetailById` 구현
  - `company_image_verifications`(`v`) ⨝ `user_details`(`nickname`·`companyName`·`companyEmail`·`job`, leftJoin on `userId`) ⨝ `users`(`email`, leftJoin on `id`).
  - `where v.id = id`. `Projections.constructor`로 상세 보조 생성자에 투영. `fetchOne()`.

### oneulsogae-api — `api/admin/`

- `response/AdminCompanyVerificationDetailResponse` (신규)
  - `id, userId, status(name), statusLabel(description), createdAt, nickname, email, companyName, companyEmail, job, imageUrl`. **`imageKey` 미노출**.
- `AdminCompanyVerificationController`에 상세 엔드포인트 추가
  - `@GetMapping("/{id}") fun verification(@PathVariable id: Long): ApiResponse<AdminCompanyVerificationDetailResponse>`.

### 테스트

- `AdminCompanyVerificationDetailE2ETest`(`AbstractIntegrationSupport` 상속)
  - 사용자·`user_details`(nickname·companyName·companyEmail·job)·`company_image_verifications` 픽스처 persist.
  - 200: 상세 필드(status·statusLabel·imageUrl 페이크·companyName·companyEmail·job·nickname·email) 검증.
  - 404: 없는 id → `success=false`, `error.code == "COMPANY-IMAGE-001"`.
  - presign 페이크는 기존 `TestFileStorageConfig`의 `CompanyVerificationImageUrlPort` 페이크 재사용(`https://presigned.test/<imageKey>`).
  - afterTest에서 company_image_verifications·user_details·users deleteAll.

## 결정 사항 (근거)

- **신규 에러코드 `COMPANY-IMAGE-001`**: 신고의 `REPORT-001`과 동일한 `<도메인>-<순번>` 컨벤션. 클라이언트가 404 사유를 구분할 수 있게 목록/상세 공용이 아닌 전용 코드로 둔다.
- **상세도 presigned URL 포함**: 목록과 동일하게 서류 열람을 지원. status 필터는 단건 조회엔 무의미하므로 없음.
- **단일 dao/UseCase/Service에 메서드 추가**: 목록과 같은 도메인·조회라 별도 슬라이스를 만들지 않고 기존 파일에 상세 메서드를 더한다(신고 슬라이스가 목록·상세를 한 dao/UseCase에 둔 것과 동일).
- **presign 재호출**: 상세는 단건이라 presign 1회. N+1 없음.

## 검증 기준

1. `GET /admin/v1/company-image-verifications/{id}` 200 — 목록 필드 + companyName/companyEmail/job + imageUrl. (E2E 통과)
2. 없는 id → 404, `error.code == "COMPANY-IMAGE-001"`. (E2E 통과)
3. admin 모듈 core import 0건 유지.
4. 전체 빌드·기존 테스트(목록 E2E 포함) 통과.
