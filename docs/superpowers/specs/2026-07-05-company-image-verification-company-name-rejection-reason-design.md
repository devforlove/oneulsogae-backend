# 회사 이미지 인증: 제출 희망 회사명 + 거절사유 저장 설계

**작성일:** 2026-07-05
**대상 브랜치:** feat/meeple-admin-module

## 목표

직장 서류 이미지 인증 레코드(`company_image_verifications`)에 두 정보를 저장한다.
- **제출 희망 회사명(`company_name`)**: 유저가 서류를 업로드할 때 어느 회사로 인증받고 싶은지 기입한다. 어드민 심사의 근거가 된다.
- **거절사유(`rejection_reason`)**: 어드민이 반려할 때 사유를 남긴다.

관련 흐름(유저 제출 command, 어드민 심사, 어드민 상세 조회)을 배선한다. 어드민 승인(`approve`)의 회사명 확정 동작은 직전 기능 그대로 유지한다(유저 제출 회사명은 정보용으로 저장·표시만).

## 스키마

`company_image_verifications`에 컬럼 2개 추가. 둘 다 **NULL 허용**(기존 행 호환).

- `company_name VARCHAR(100) NULL` — 유저 제출 희망 회사명. (기존 행은 NULL. 앱은 신규 제출 시 도메인 검증으로 필수 강제)
- `rejection_reason VARCHAR(500) NULL` — 어드민 반려 사유. (반려 시에만 채워짐)

**마이그레이션(운영)**: `docs/migration/company_image_verifications_company_name_rejection_reason.sql`에 `ALTER TABLE ... ADD COLUMN` 2건을 둔다(주석 포함). 테스트는 `ddl-auto: create-drop`으로 엔티티에서 자동 생성되므로 스크립트 실행 불필요.

## Part A — 유저 제출 희망 회사명 (meeple-core / user 제출)

- **엔티티** `CompanyImageVerificationEntity`: `var companyName: String?`(@Column length=100, nullable)·`var rejectionReason: String?`(@Column length=500, nullable) 추가. (두 컬럼 모두 여기서 선언)
- **도메인** `CompanyImageVerification`: `companyName: String?`·`rejectionReason: String? = null` 필드 추가.
  - `create(userId: Long, imageKey: String, companyName: String): CompanyImageVerification` — 시그니처에 companyName 추가. 생성 전 `companyName` 검증: 공백이거나 50자 초과면 `BusinessException(UserErrorCode.INVALID_COMPANY_NAME)`.
  - 생성 결과: `status = PENDING`, `rejectionReason = null`.
- **에러코드** `UserErrorCode.INVALID_COMPANY_NAME("USER-023", "회사명을 입력해 주세요. (최대 50자)", HttpStatus.BAD_REQUEST)` 추가.
- **매퍼** `CompanyImageVerificationMapper`: `toDomain`/`toEntity`에 companyName·rejectionReason 반영.
- **command** `SubmitCompanyImageVerificationCommand`: `companyName: String` 추가.
- **서비스** `SubmitCompanyImageVerificationService`: `CompanyImageVerification.create(userId, key, command.companyName)`.
- **컨트롤러** `UserCompanyImageVerificationController`: 멀티파트에 `@RequestParam("companyName") companyName: String` 추가 → command에 전달.
- **응답** `CompanyImageVerificationResponse`: 변경 없음(verificationId·status 유지).
- **테스트/픽스처**:
  - 유닛 `CompanyImageVerificationTest`: `create` 호출에 companyName 추가, companyName 저장·공백/50자 초과 → INVALID_COMPANY_NAME 케이스 추가.
  - `CompanyImageVerificationEntityFixture.create`: `companyName: String? = "테스트회사"`·`rejectionReason: String? = null` 파라미터 추가.
  - E2E `SubmitCompanyImageVerificationE2ETest`: 멀티파트에 `companyName` 파트 추가, 저장 검증. (누락/공백 시 400도 1건)

## Part B — 거절사유 (meeple-admin reject)

- **도메인** `AdminCompanyImageVerification`: `rejectionReason: String?` 필드 추가.
  - `approve(): copy(status = APPROVED, rejectionReason = null)` (승인 시 stale 사유 제거)
  - `reject(reason: String?): copy(status = REJECTED, rejectionReason = reason)`
- **in-port** `ReviewCompanyImageVerificationUseCase.reject(id: Long, reason: String?)` — 시그니처에 reason 추가. `approve(id, companyName)`는 불변.
- **서비스** `ReviewCompanyImageVerificationService.reject(id, reason)`: 로드 → `save(verification.reject(reason))`.
- **out-port**:
  - `GetCompanyImageVerificationPort.findById`: 반환 도메인이 rejectionReason 포함.
  - `SaveCompanyImageVerificationPort.save`: rejectionReason도 반영.
- **infra** `CompanyImageVerificationRepositoryAdapter`(admin 구현부): `toAdminDomain`이 rejectionReason 매핑, admin `save`가 기존 행 로드 후 `status`·`rejectionReason` 반영(imageKey/userId/companyName 보존).
- **api**: `AdminRejectCompanyVerificationRequest(reason: String?)` — `@field:Size(max = 500)`(선택). 컨트롤러 `reject`가 `@RequestBody(required = false) request: AdminRejectCompanyVerificationRequest?`를 받아 `reviewUseCase.reject(id, request?.reason)`.

## Part C — 어드민 상세 노출

유저 제출 회사명·반려 사유를 어드민이 상세에서 확인할 수 있게 한다.

- **read model** `AdminCompanyVerificationDetailView`: `requestedCompanyName: String?`(= verification.companyName, 기존 `companyName`(user_details 프로필)과 구분)·`rejectionReason: String?` 추가. QueryDSL 투영용 보조 생성자를 확장(imageUrl 제외).
- **daoImpl** `GetAdminCompanyVerificationDaoImpl.findDetailById`: 투영에 `verification.companyName`·`verification.rejectionReason` 추가.
- **응답** `AdminCompanyVerificationDetailResponse`: `requestedCompanyName`·`rejectionReason` 추가.
- **테스트** 어드민 상세 E2E: 반려 후 조회 시 `requestedCompanyName`·`rejectionReason` 노출 검증(또는 승인/반려 E2E에서 상세 재조회).

## 데이터 흐름

1. 유저 제출: 멀티파트(image + companyName) → command → 도메인 검증 → S3 업로드 → `company_image_verifications`(company_name, status=PENDING) 저장.
2. 어드민 상세: 대상 인증의 `requestedCompanyName`(유저 희망)·기존 프로필 companyName·상태·이전 rejectionReason을 함께 조회.
3. 어드민 승인: (불변) approve(id, companyName) → status=APPROVED, user_details.companyName 확정, rejectionReason=null.
4. 어드민 반려: reject(id, reason) → status=REJECTED, rejection_reason 저장.

## 결정 사항 (근거)

- **컬럼 nullable**: 기존 행 호환. 신규 제출은 도메인 검증(`create`)으로 companyName 필수 강제, 컬럼 제약은 관대.
- **거절사유 선택(nullable)·body required=false**: 어드민이 사유 없이도 반려 가능. 기존 무-body reject 호출과 호환.
- **approve가 rejectionReason 초기화**: 상태 가드 없는 override라, 반려됐던 인증을 승인하면 stale 사유를 지운다.
- **companyName 검증은 도메인**: 멀티파트 `@RequestParam`이라 도메인 `create`에서 검증(도메인→유닛 테스트 전략 부합). 상한 50자는 `ResolveCompanyNameRequest`·직전 승인 DTO 관례와 일치.
- **상세만 노출, 목록 미변경**: 어드민 심사 워크플로엔 상세로 충분. YAGNI.
- **`requestedCompanyName` 네이밍**: 상세에 이미 `companyName`(user_details 프로필)이 있어 충돌을 피하고 의미(유저가 신청한 회사명)를 드러낸다.

## 검증 기준

1. 유저 제출이 companyName을 저장한다(공백/50자 초과 400 INVALID_COMPANY_NAME). (유닛 + E2E)
2. 어드민 reject(reason)가 rejection_reason을 저장한다. (E2E)
3. 어드민 상세가 requestedCompanyName·rejectionReason을 노출한다. (E2E)
4. approve는 직전 동작 유지(companyName 확정) + rejectionReason 초기화. (E2E)
5. admin 모듈 core import 0건 유지. 전체 빌드·기존 테스트 통과. 운영 마이그레이션 스크립트 존재.
