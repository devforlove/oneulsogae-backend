# image_template 참조 도메인 + popup 연동 설계

## 목적

팝업 이미지를 **배포 없이 언제든 변경**할 수 있게, 이미지 메타데이터(URL·width·height)를 코드(`code`)로 조회하는 **재사용 가능한 참조 테이블** `image_templates`로 분리한다. 소비처는 이미지를 복사(스냅샷)하지 않고 **코드로 참조**하며, 조회 시점에 현재 이미지를 해석한다(테이블 행을 고치면 기존에 노출 중인 항목에도 즉시 반영).

popup이 첫 소비처이며, 이후 다른 도메인도 동일하게 코드로 이미지를 재사용한다.

## 범위

- 신규 `image` 참조 도메인(읽기 전용, `region` 도메인과 동일한 query 전용 구조).
- popup의 이미지 저장 방식을 inline 컬럼 → `image_templates` 코드 참조로 **전면 이전**.
- prod 반영용 수기 마이그레이션 SQL(`docs/migration/`).
- DB 직접 관리(앱은 읽기만). 관리자 CRUD API는 범위 밖.

## image 도메인 (재사용 read 도메인)

읽기 전용이라 `region`처럼 **query 전용**으로 둔다(command 없음). 엔티티는 `infra.image`에 flat하게 둔다(`infra.region` 선례).

### 테이블 `image_templates`

| 컬럼 | 타입 | 비고 |
|---|---|---|
| `id` | bigint PK auto | |
| `code` | varchar(100) **UNIQUE NOT NULL** | 소비처가 참조하는 고유 키 |
| `image_url` | varchar(500) NOT NULL | |
| `image_width` | int NOT NULL | px |
| `image_height` | int NOT NULL | px |
| `created_at`/`updated_at` | datetime | `BaseEntity` |

### core
- `core/image/query/dto/ImageTemplateView.kt` — `(code: String, imageUrl: String, imageWidth: Int, imageHeight: Int)`.
- `core/image/query/dao/GetImageTemplateDao.kt` — `findByCode(code: String): ImageTemplateView?` (query out-port 인터페이스).
- `core/image/query/service/GetImageTemplateService.kt` (`@Transactional(readOnly = true)`) — `GetImageTemplateUseCase` 구현, dao 위임.
- `core/image/query/service/port/in/GetImageTemplateUseCase.kt` — `fun getByCode(code: String): ImageTemplateView?`. **재사용 공개 계약**(서비스/명령 시점 단건 조회용). 없으면 null.

### infra
- `infra/image/ImageTemplateEntity.kt` — `@Entity @Table("image_templates")`, `code` unique 인덱스.
- `infra/image/ImageTemplateJpaRepository.kt`.
- `infra/image/GetImageTemplateDaoImpl.kt` — `GetImageTemplateDao` 구현(Spring Data 파생 쿼리 `findByCode`).

> popup 목록 조회는 N+1을 피하려 in-port가 아니라 read 경로에서 직접 **LEFT JOIN**으로 해석한다(아래). in-port는 단건/서비스 시점 소비처용. (matchuser 선례: in-port + query dao join 병존)

## popup 연동 (이미지 전면 이전)

### command
- `Popup`(도메인): `imageUrl`/`imageWidth`/`imageHeight` 제거, `imageCode: String? = null` 추가.
  - companion 상수: `MATCH_FAILED_REFUND_IMAGE_CODE = "POPUP_MATCH_FAILED_REFUND"`, `MEETING_FAILED_REFUND_IMAGE_CODE = "POPUP_MEETING_FAILED_REFUND"`.
  - `matchFailedRefund(...)`는 `imageCode = MATCH_FAILED_REFUND_IMAGE_CODE`, `meetingFailedRefund(...)`는 `imageCode = MEETING_FAILED_REFUND_IMAGE_CODE` 설정.
- `PopupEntity`: `image_url`/`image_width`/`image_height` 컬럼 제거, `image_code varchar(100)` nullable 추가. 기존 노출 인덱스 `(user_id, exposed_from, exposed_to)` 유지.
- `PopupMapper`: `imageCode` 매핑(toDomain/toEntity).
- **FK 제약 없음**(loose reference): 코드에 해당하는 템플릿이 없으면 조회 시 이미지가 null. 팝업 생성이 템플릿 존재에 묶이지 않고, 이미지 교체가 즉시 반영된다.

### query (read model 출처만 변경, 계약 유지)
- `PopupView`: 필드 **그대로 유지**(`imageUrl`/`imageWidth`/`imageHeight` 포함). 출처가 popup 행 → join된 `image_templates`로 바뀔 뿐.
- `GetPrivatePopupDaoImpl`·`GetPublicPopupDaoImpl`: `QImageTemplateEntity`를 `template.code = popup.imageCode`로 **LEFT JOIN**하고 `template.imageUrl/imageWidth/imageHeight`를 `PopupView`에 투영.
- `PopupResponse`·`PopupController`·`GetPopupsService`: **변경 없음**(PopupView 계약 불변).

## 마이그레이션 (`docs/migration/`)

테스트는 `ddl-auto: create-drop`로 엔티티에서 스키마를 만들므로 대상은 prod 수기 반영용.

1. `image_templates.sql` — 테이블 생성 + 환불 템플릿 2건 seed(placeholder URL).
   - `POPUP_MATCH_FAILED_REFUND`, `POPUP_MEETING_FAILED_REFUND` (서로 다른 placeholder URL/치수).
2. `popups_image_code.sql` — 순서:
   1. `popups`에 `image_code varchar(100) null` 추가.
   2. 기존 `image_url` 있는 popup → `image_templates`에 `code = CONCAT('POPUP_LEGACY_', id)`로 행 삽입, 해당 popup의 `image_code` 세팅(기존 이미지 보존).
   3. `popups`에서 `image_url`/`image_width`/`image_height` 컬럼 drop.

placeholder URL 예: `https://placehold.co/320x400?text=match-failed-refund` (width 320, height 400), `https://placehold.co/320x400?text=meeting-failed-refund`. (운영에서 DB로 교체)

## 테스트

- `infra` testFixtures: `ImageTemplateEntityFixture` 추가(코드·URL·치수 지정 생성).
- popup 조회 E2E: `image_templates` seed + popup `image_code` 세팅 → 조회 결과에 join된 이미지(url/width/height)가 내려오는지, 코드 없으면 null인지 검증.
- `core/image` 유닛: `GetImageTemplateService.getByCode` (있음/없음) — 단, 단순 위임이라 E2E로 갈음 가능. dao 동작은 popup 조회 E2E의 join으로 함께 검증됨. `GetImageTemplateUseCase` 직접 검증 E2E 1건 추가.
- 환불 E2E(`ExpireMatchServiceIntegrationTest` 등): 팝업 생성/존재 검증 그대로 동작(코드만 저장, FK 없음). 필요 시 생성된 팝업의 `image_code` 검증 추가.
- `PopupViewsTest`(정렬 도메인): `PopupView` 불변 → 영향 없음.

## 영향 없는 것 / 호환성

- 클라이언트 API(`PopupResponse`) 응답 형태 불변.
- 전역/개인 팝업 조회 쿼리 형태 동일(LEFT JOIN 1개 추가).
- 기존 popup 이미지 데이터는 백필로 보존.
