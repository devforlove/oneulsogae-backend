# 어드민 회사 이미지 인증 목록 조회 API 설계

**작성일:** 2026-07-05
**대상 브랜치:** feat/oneulsogae-admin-module

## 목표

어드민이 사용자가 제출한 직장 서류 이미지 인증(`company_image_verifications`)을 **페이징으로 목록 조회**하고, 각 항목에서 **서류 이미지를 열람**(S3 presigned GET URL)할 수 있는 API를 추가한다. 심사 상태(`PENDING`/`APPROVED`/`REJECTED`)로 필터링할 수 있다.

승인/반려 등 **심사 처리(command)는 이번 범위 밖**이다. 조회(목록 페이징)만 추가한다.

## 배경 / 기존 자산

- 도메인: `CompanyImageVerification`(core) — `userId`, `imageKey`(S3 오브젝트 키), `status`(제출 시 `PENDING`). 파일은 S3에 비공개 저장, DB엔 키만 보관.
- 엔티티: `CompanyImageVerificationEntity`(infra) — `idx_user_id` 인덱스 보유, soft delete(`@SQLRestriction`).
- 상태 enum: `com.org.oneulsogae.common.user.CompanyImageVerificationStatus`(common) — `description` 한글 라벨 보유.
- 미러 대상 패턴: `GET /admin/v1/reports`(어드민 신고 조회) — admin query 슬라이스 + infra QueryDSL daoImpl + api 컨트롤러/응답 DTO.
- S3: `S3Config`(S3Client 빈), `S3Properties`(`app.s3.*`), `S3FileStorageAdapter`(업로드). 이 어댑터 주석이 *"어드민 조회가 필요해지면 presigned GET URL을 발급하는 조회 포트를 추가한다"*고 이미 예고.
- 테스트: E2E는 실제 S3를 띄우지 않고 `TestFileStorageConfig`가 `FileStoragePort`를 인메모리 페이크로 대체.

## 아키텍처 (기존 신고 조회 슬라이스 미러링)

`oneulsogae-admin`은 core에 의존하지 않는 자립 모듈 규칙을 유지한다. presign은 admin이 out-port 인터페이스만 소유하고 infra가 S3로 구현한다.

### 엔드포인트

```
GET /admin/v1/company-image-verifications?page=0&size=20&status=PENDING
```

- `page`(기본 0), `size`(기본 20, 최대 100), `status`(옵션 — 생략 시 전체).
- 최신순(`id desc`) offset 페이징.
- `/admin/v1/**`는 SecurityConfig의 `hasRole(ADMIN)`으로 이미 보호 → **보안 설정 변경 없음**.

### oneulsogae-admin — `admin/companyverification/query/`

- `dto/AdminCompanyVerificationView`
  - 필드: `id: Long, userId: Long, nickname: String?, email: String?, status: CompanyImageVerificationStatus, createdAt: LocalDateTime?, imageKey: String, imageUrl: String? = null`
  - dao는 `imageKey`까지 채우고 `imageUrl`은 null. 서비스가 presign 결과로 `copy(imageUrl = ...)`.
  - QueryDSL `Projections.constructor`가 7-arg 투영을 바인딩할 수 있도록, `imageUrl`을 제외한 **보조 생성자**(7개 인자 → 본 생성자에 `imageUrl = null` 위임)를 둔다.
- `dto/AdminCompanyVerificationViews` — 일급 컬렉션(`values: List<AdminCompanyVerificationView>`, `empty()`).
- `dto/AdminCompanyVerificationPage` — `content: AdminCompanyVerificationViews, page, size, totalElements`. `totalPages`/`hasNext`는 파생값(신고 `AdminReportPage`와 동일 계산).
- `dao/GetAdminCompanyVerificationDao` (query out-port)
  - `findPage(offset: Long, limit: Int, status: CompanyImageVerificationStatus?): AdminCompanyVerificationViews`
  - `count(status: CompanyImageVerificationStatus?): Long`
- `service/GetAdminCompanyVerificationsService` (`@Service`, `@Transactional(readOnly = true)`)
  - dao 조회 → 각 행의 `imageKey`를 `CompanyVerificationImageUrlPort`로 presigned URL 변환 → Page 조립.
  - `MAX_PAGE_SIZE = 100`, `page.coerceAtLeast(0)`, `size.coerceIn(1, MAX_PAGE_SIZE)`.
- `service/port/in/GetAdminCompanyVerificationsUseCase`
  - `getVerifications(page: Int, size: Int, status: CompanyImageVerificationStatus?): AdminCompanyVerificationPage`
- `service/port/out/CompanyVerificationImageUrlPort` (query out-port)
  - `fun presignedGetUrl(imageKey: String): String` — admin은 인터페이스만 소유.

### oneulsogae-infra — `infra/user/query/`

- `GetAdminCompanyVerificationDaoImpl` (`@Component`, QueryDSL)
  - `company_image_verifications`(`v`) ⨝ `user_details`(`nickname`, leftJoin on `userId`) ⨝ `users`(`email`, leftJoin on `id`).
  - `status` 파라미터가 있으면 `v.status.eq(status)` 동등 필터.
  - `order by v.id.desc()`, `offset`/`limit`.
  - `count`도 동일 필터.
- `S3CompanyVerificationImageUrlAdapter` (`@Component`) implements `CompanyVerificationImageUrlPort`
  - `S3Presigner`로 `GetObjectPresignRequest`(버킷 `S3Properties.bucket`, 서명 만료 프로퍼티) → presigned GET URL 문자열.
- `S3Config`: `S3Presigner` 빈 추가(s3Client와 동일하게 region·forcePathStyle·credentials·endpointOverride 구성).
- `S3Properties`: presigned URL 만료 프로퍼티 추가(예: `presignedGetExpiryMinutes: Long = 10`).

### oneulsogae-api — `api/admin/`

- `AdminCompanyVerificationController` (`@RequestMapping("/admin/v1/company-image-verifications")`)
  - `GetAdminCompanyVerificationsUseCase` 주입. `status`는 `CompanyImageVerificationStatus?` 파라미터(스프링이 enum 변환, 잘못된 값 400).
- `response/AdminCompanyVerificationResponse`
  - `id, userId, status(name), statusLabel(description), createdAt, nickname, email, imageUrl`. **`imageKey`는 노출하지 않고 열람용 `imageUrl`만 노출**.
- `response/AdminCompanyVerificationPageResponse` — `content, page, size, totalElements, totalPages, hasNext`(신고 Page 응답과 동일 형태).

### 테스트

- `TestFileStorageConfig`에 `CompanyVerificationImageUrlPort` 페이크 빈(`@Primary`) 추가 — presign을 결정적 URL(예: `https://presigned.test/<imageKey>`)로 대체. (E2E는 실제 S3를 띄우지 않으므로)
- `AdminCompanyVerificationListE2ETest`(`AbstractIntegrationSupport` 상속)
  - 사용자·`user_details`·`company_image_verifications`(다양한 status) 픽스처 persist.
  - 최신순 페이징, 조인된 nickname/email, `imageUrl`(페이크), `status`/`statusLabel` 검증.
  - `?status=PENDING` 필터가 해당 상태만 반환하는지.
  - `size`로 페이지 크기 제한.
  - `adminAccessTokenFor(9901L)`(ROLE_ADMIN) 사용. 인가(401/403)는 기존 `AdminAccessE2ETest`가 `/admin/**` 공통 검증.

## 결정 사항 (근거)

- **인덱스 추가 안 함**: `order by id desc`는 PK 클러스터 인덱스로 정렬을 커버하고 `status`는 스캔 중 잔여 필터로 적용된다(filesort 미발생). 어드민 저트래픽 + 소규모 테이블이라 `(status, id)` 복합 인덱스의 쓰기 비용/DDL이 정당화되지 않는다. 볼륨이 커지면 `(status, id)` 추가를 재검토한다.
- **presign은 행별 로컬 서명**: `S3Presigner.presignGetObject`는 네트워크 왕복 없이 로컬에서 URL을 서명한다. 페이지 크기만큼 반복해도 N+1 네트워크 문제가 없다.
- **단일 read model + 보조 생성자**: dao 투영과 서비스 enrich(url)를 한 DTO로 합쳐 중복을 줄인다. QueryDSL 투영을 위해 `imageUrl` 제외 보조 생성자를 둔다.
- **command(승인/반려) 미포함**: 사용자 요청은 "리스트 페이징 조회"에 한정. YAGNI.

## 검증 기준

1. `GET /admin/v1/company-image-verifications` 200 — 최신순 페이징, 조인 필드·imageUrl 포함. (E2E 통과)
2. `?status=PENDING` 등 필터가 해당 상태만 반환. (E2E 통과)
3. `size`/`page` 페이징 메타데이터(totalElements/totalPages/hasNext) 정확. (E2E 통과)
4. admin 모듈 core import 0건, `project(":oneulsogae-core")` 미추가 유지.
5. 전체 빌드·기존 테스트 통과.
