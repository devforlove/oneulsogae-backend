# 어드민 회사 이미지 인증 승인/반려 API 설계

**작성일:** 2026-07-05
**대상 브랜치:** feat/meeple-admin-module

## 목표

어드민이 직장 서류 이미지 인증을 심사해 **승인** 또는 **반려**하는 API를 추가한다.
- **승인**: 어드민이 회사명을 기입하면, 해당 유저의 `user_details.companyName`을 그 값으로 바꾸고 인증 `status`를 `APPROVED`로 만든다.
- **반려**: 인증 `status`를 `REJECTED`로 만든다.

`meeple-admin`의 **첫 command 슬라이스**다(기존 조회 슬라이스 `companyverification/query`에 이어 `companyverification/command` 추가). admin은 core 비의존이므로 자체 도메인·command out-port를 두고 infra가 구현한다.

### 범위 (최소)

승인은 **회사명 확정 + 상태 변경**만 한다. 회사 이메일 인증(`VerifyCompanyEmailService`)이 부가로 수행하는 **가입 상태(ACTIVE) 전환·match_user 동기화·가입 코인 지급·첫 매칭 추천은 하지 않는다.** (따라서 온보딩 단계 유저는 이미지 인증이 승인돼도 자동으로 ACTIVE가 되지 않는다 — 의도된 최소 범위.) 반려는 상태 변경만 한다(알림 등 부수효과 없음).

## 엔드포인트

`/admin/v1/**`는 SecurityConfig의 `hasRole(ADMIN)`으로 이미 보호 → 보안 설정 변경 없음. 기존 `AdminCompanyVerificationController`에 메서드를 추가한다.

- `POST /admin/v1/company-image-verifications/{id}/approve`
  - body: `{ "companyName": "미플" }` (`@field:NotBlank` — 공백이면 400)
  - 효과: 인증 `status=APPROVED` + 해당 유저 `user_details.companyName = companyName`
  - 응답: `ApiResponse<Unit>` success (빈 본문)
- `POST /admin/v1/company-image-verifications/{id}/reject`
  - 효과: 인증 `status=REJECTED`
  - 응답: `ApiResponse<Unit>` success (빈 본문)
- 없거나 soft-delete된 id → 404 (`AdminException`, `AdminErrorCode.COMPANY_IMAGE_VERIFICATION_NOT_FOUND`, `COMPANY-IMAGE-001` — 상세조회에서 추가한 코드 재사용)

## 아키텍처

### meeple-admin — `companyverification/command/`

- `domain/AdminCompanyImageVerification`
  - `data class AdminCompanyImageVerification(id: Long, userId: Long, status: CompanyImageVerificationStatus)`
  - `fun approve(): AdminCompanyImageVerification = copy(status = CompanyImageVerificationStatus.APPROVED)`
  - `fun reject(): AdminCompanyImageVerification = copy(status = CompanyImageVerificationStatus.REJECTED)`
  - (상태 전이를 도메인에 캡슐화 — 서비스에 `status = ...` 리터럴을 두지 않는다)
- `application/port/in/ReviewCompanyImageVerificationUseCase`
  - `fun approve(id: Long, companyName: String)`
  - `fun reject(id: Long)`
- `application/ReviewCompanyImageVerificationService` (`@Service`, `@Transactional`)
  - `approve`: `getPort.findById(id) ?: throw AdminException(COMPANY_IMAGE_VERIFICATION_NOT_FOUND, "...: $id")` → `savePort.save(v.approve())` → `updateUserCompanyNamePort.updateCompanyName(v.userId, companyName)`
  - `reject`: `getPort.findById(id) ?: throw ...` → `savePort.save(v.reject())`
- `application/port/out/`
  - `GetCompanyImageVerificationPort.findById(id: Long): AdminCompanyImageVerification?` — 없거나 soft-delete면 null
  - `SaveCompanyImageVerificationPort.save(verification: AdminCompanyImageVerification): AdminCompanyImageVerification` — 상태만 반영(다른 필드 보존)
  - `UpdateUserCompanyNamePort.updateCompanyName(userId: Long, companyName: String)`

### meeple-infra (엔티티당 어댑터 하나 — 기존 어댑터 확장)

- `CompanyImageVerificationRepositoryAdapter` (기존, core `SaveCompanyImageVerificationPort` 구현 중)
  - admin `GetCompanyImageVerificationPort`·`SaveCompanyImageVerificationPort`를 **추가 구현**. core와 admin의 동명 포트는 **import alias**로 구분한다(예: admin 포트를 `SaveAdminCompanyImageVerificationPort`로 alias).
  - `findById(id)`: `companyImageVerificationJpaRepository.findById(id)`(@SQLRestriction으로 삭제 행 제외) → `AdminCompanyImageVerification(id, userId, status)` 매핑, 없으면 null.
  - admin `save(v)`: **기존 엔티티를 로드**해 `status`만 바꿔 저장한다(imageKey/userId 보존). `repo.findById(v.id)`로 로드 후 `entity.status = v.status`; `repo.save(entity)`; 결과를 도메인으로 반환.
- `UserDetailCoreAdapter` (기존, core `GetUserDetailPort`/`SaveUserDetailPort`/`AnonymizeUserDetailPort` 구현 중)
  - admin `UpdateUserCompanyNamePort`를 **추가 구현**.
  - `updateCompanyName(userId, companyName)`: `userDetailJpaRepository.findByUserId(userId)`로 로드 → `entity.companyName = companyName` → save. 행이 없으면 데이터 정합성 이상이므로 `IllegalStateException`을 던진다(정상 유저에겐 항상 user_details 행이 있어 실제로는 발생하지 않는다).

### meeple-api

- `request/AdminApproveCompanyVerificationRequest(companyName: String)` — `@field:NotBlank`.
- `AdminCompanyVerificationController`에 메서드 추가
  - `@PostMapping("/{id}/approve") fun approve(@PathVariable id: Long, @RequestBody @Valid request: AdminApproveCompanyVerificationRequest): ApiResponse<Unit>` → `reviewUseCase.approve(id, request.companyName)` → `ApiResponse.success()`
  - `@PostMapping("/{id}/reject") fun reject(@PathVariable id: Long): ApiResponse<Unit>` → `reviewUseCase.reject(id)` → `ApiResponse.success()`
  - `ReviewCompanyImageVerificationUseCase`를 생성자에 추가 주입(기존 `GetAdminCompanyVerificationsUseCase`와 함께).

### 테스트

- `AdminCompanyVerificationReviewE2ETest` (`AbstractIntegrationSupport` 상속)
  - **승인 200**: 유저 + `user_details`(companyName=null 등) + `company_image_verifications`(PENDING) persist → `POST /{id}/approve {"companyName":"미플"}` → 200. DB 재조회로 인증 `status=APPROVED`, `user_details.companyName == "미플"` 검증.
  - **반려 200**: 인증(PENDING) → `POST /{id}/reject` → 200. DB 재조회로 `status=REJECTED` 검증.
  - **없는 id 404**: `POST /999999/approve {"companyName":"x"}` → 404, `error.code == "COMPANY-IMAGE-001"`.
  - **공백 companyName 400**: `POST /{id}/approve {"companyName":""}` → 400.
  - DB 상태 재조회는 `IntegrationUtil`(QueryDSL) 사용. afterTest에서 company_image_verifications·user_details·users deleteAll.

## 결정 사항 (근거)

- **command를 meeple-admin에 배치**: 조회 슬라이스와 위치 일관. admin은 core 비의존이라 core `CompanyImageVerification` 도메인을 쓸 수 없어 자체 도메인(`AdminCompanyImageVerification`)·command out-port를 두고 infra가 구현한다.
- **상태 가드 없음**: 어드민 오버라이드이므로 현재 상태와 무관하게 approve/reject를 허용한다(PENDING 전제를 강제하지 않는다). 필요해지면 후속에 도메인 가드를 추가한다.
- **companyEmail 미변경**: 이미지 인증 경로엔 회사 이메일이 없다. companyName만 확정한다.
- **에러코드 재사용**: 상세조회에서 추가한 `COMPANY-IMAGE-001`을 승인/반려 not-found에도 쓴다.
- **import alias**: 한 어댑터가 core와 admin의 동명 `SaveCompanyImageVerificationPort`를 함께 구현하므로 alias로 구분한다(CLAUDE.md 영속성 어댑터 규칙).
- **응답 빈 본문**: command라 `ApiResponse.success()`만 반환한다. 프론트는 갱신 후 목록/상세를 재조회한다.

## 검증 기준

1. `POST .../{id}/approve`가 인증 status를 APPROVED로, user_details.companyName을 입력값으로 바꾼다. (E2E 통과)
2. `POST .../{id}/reject`가 status를 REJECTED로 바꾼다. (E2E 통과)
3. 없는 id → 404(COMPANY-IMAGE-001), 공백 companyName → 400. (E2E 통과)
4. admin 모듈 core import 0건 유지. 한 어댑터가 core/admin 포트를 alias로 함께 구현.
5. 전체 빌드·기존 테스트(조회 E2E 포함) 통과.
