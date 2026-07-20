# 유저용 타입별 그룹핑 모임 리스트 조회 API 설계

작성일: 2026-07-06
브랜치: feat/oneulsogae-admin-module

## 배경

현재 `gathering` 도메인은 `oneulsogae-admin` 모듈에만 존재한다(어드민 전용 `AdminGathering`, 운영자 생성/수정/상태변경). 일반 유저용 gathering 도메인은 없다. 유저가 앱에서 모집중인 모임을 **모임 타입별로 그룹핑된 리스트**로 조회할 수 있는 API를 신설한다.

유저는 모임을 생성하지 않는다(생성은 어드민만, `GatheringEntity.userId`는 nullable이며 운영 생성 시 null). 따라서 command 측은 만들지 않고 **조회 전용(query-only) 경로**만 추가한다. 같은 `gatherings` 테이블을 읽는 독립 read model이다.

## 요구사항 (확정)

- **소비 대상**: 일반 유저(앱), 신규 유저용 query 경로.
- **상태 필터**: `RECRUITING`(모집중)만 노출. DRAFT/CLOSED/FINISHED/CANCELED 제외.
- **그룹핑**: `GatheringType` 3개 타입(ONE_ON_ONE_ROTATION, COOKING, PARTY)을 **항상 모두 포함**. 해당 타입에 모임이 없으면 빈 배열.
- **그룹 순서**: `GatheringType` enum 선언 순서 고정(ONE_ON_ONE_ROTATION → COOKING → PARTY).
- **그룹 내 정렬**: `gatheringAt` 오름차순(임박순). 개수 제한 없이 전체.
- **단건 응답 필드**: `id`, `imageUrl`(presigned), `region`(장소), `title`(제목), `gatheringAt`(시간).
- **엔드포인트**: `GET /v1/gatherings`.
- **인증**: 로그인한 일반 유저 전용(기존 `/v1/**` 유저 토큰 정책).

## 아키텍처 (헥사고날 + CQRS query-only)

### oneulsogae-core — `com.org.oneulsogae.gathering.query`

- `dto/`
  - `GatheringView` — 단건 read model. 기존 `AdminGatheringView`와 동일하게 **`imageKey`(내부용)와 `imageUrl`을 모두 보유**하고, DAO 투영용 보조 생성자(`imageUrl` 인자 제외)로 `imageUrl = null`을 채운다.
    - 필드: `id: Long`, `type: GatheringType`(그룹핑용), `title: String`, `imageKey: String?`, `imageUrl: String? = null`, `region: String`, `gatheringAt: LocalDateTime`.
    - 보조 생성자: `(id, type, title, imageKey, region, gatheringAt)` → `imageUrl = null`.
  - `GatheringViews` — 일급 컬렉션(`values: List<GatheringView>`). 도메인 메서드 `groupByType(types: List<GatheringType>): GroupedGatherings` 보유(타입 순서대로 그룹 생성, 없는 타입은 빈 배열, 그룹핑 로직 캡슐화).
  - `GatheringTypeGroup` — `type: GatheringType`, `typeDescription: String`(= `type.description`), `gatherings: List<GatheringView>`.
  - `GroupedGatherings` — 일급 컬렉션(`values: List<GatheringTypeGroup>`).
- `dao/GetGatheringDao` (query out-port)
  - `fun findRecruitingOrderByGatheringAt(): GatheringViews` — status=RECRUITING, order by gatheringAt asc. imageKey 포함 투영, imageUrl은 null.
- `service/GetGatheringsService` (`@Service`, `@Transactional(readOnly = true)`, `GetGatheringsUseCase` 구현)
  - DAO로 read model 조회 → 각 행을 `view.copy(imageUrl = imageKey?.let { port.presignedGetUrl(it) })`로 치환(admin `GetAdminGatheringsService`와 동일) → `groupByType(GatheringType.entries)`로 그룹핑 → `GroupedGatherings` 반환.
  - imageKey가 null인 행은 imageUrl도 null.
- `service/port/in/GetGatheringsUseCase` — `fun getGatherings(): GroupedGatherings`.
- `service/port/out/GatheringImageUrlPort` — `fun presignedGetUrl(imageKey: String): String` (fun interface). 기존 admin `GatheringImageUrlPort`와 동일 시그니처지만 core 소속의 별도 인터페이스(core는 admin에 의존 불가).

### oneulsogae-api — `com.org.oneulsogae.api.gathering`

- `GatheringController` — `@RestController`, `GET /v1/gatherings`. `GetGatheringsUseCase` 주입. 반환 `ApiResponse<GatheringGroupListResponse>`.
- `response/GatheringGroupListResponse`
  - `groups: List<Group>`
  - `Group`: `type: GatheringType`, `typeDescription: String`, `gatherings: List<Item>`
  - `Item`: `id: Long`, `imageUrl: String?`, `region: String`, `title: String`, `gatheringAt: LocalDateTime`
  - `from(GroupedGatherings)` 팩토리로 read model → 응답 DTO 변환.

### oneulsogae-infra — `com.org.oneulsogae.infra.gathering.query`

- `GetGatheringDaoImpl` (`GetGatheringDao` 구현) — QueryDSL `JPAQueryFactory`로 `QGatheringEntity`에서 `GatheringView`로 `Projections.constructor` 직접 투영(imageKey 포함, imageUrl 제외 → 보조 생성자). `where(gatheringEntity.status.eq(RECRUITING))`, `orderBy(gatheringEntity.gatheringAt.asc())`. 엔티티 로드 없음.
- presigned URL: 기존 `S3GatheringImageUrlAdapter`가 core `GatheringImageUrlPort`도 **함께 구현**(admin/core 두 인터페이스 모두 `presignedGetUrl(String): String`로 동일 시그니처 → 오버라이드 하나로 양쪽 충족, import alias 사용). 새 S3 어댑터를 만들지 않고 재사용한다.

## 인덱스 검토

기존 `GatheringEntity`의 복합 인덱스 `idx_status_type_gathering_at (status, type, gathering_at)`가 `where status = 'RECRUITING' order by gathering_at asc` 쿼리를 커버한다. 단, 인덱스는 `(status, type, gathering_at)` 순서라 type 조건이 없는 이 쿼리에서는 `status` 동등 조건까지만 seek하고 그 다음 `type`을 건너뛴 `gathering_at` 정렬에는 인덱스 정렬을 100% 활용하지 못할 수 있다(status 고정 후 type별로 gathering_at이 나뉘므로 정렬 보장 안 됨 → filesort 가능). 데이터량이 적으면 무시 가능하나, 필요 시 `(status, gathering_at)` 인덱스 추가를 검토한다. **초기에는 기존 인덱스로 진행하고, 정렬 성능이 문제되면 인덱스 추가를 별도 판단**한다.

## 테스트

- **유닛(Kotest)** — `GatheringViews.groupByType`
  - 빈 타입은 빈 배열로 포함된다.
  - 그룹 순서가 전달한 타입 순서를 따른다.
  - 그룹 내 정렬(입력 순서) 유지.
- **E2E(oneulsogae-api, AbstractIntegrationSupport)** — `GET /v1/gatherings`
  - RECRUITING만 노출(DRAFT/CANCELED 등 제외).
  - 3개 타입 모두 응답에 존재, 모임 없는 타입은 빈 배열.
  - `gatheringAt` 임박순 정렬.
  - imageKey 있는 모임은 imageUrl presigned로 채워지고, 없으면 null.
  - GatheringEntityFixture로 픽스처 구성.

## 범위 밖 (YAGNI)

- 유저용 모임 상세 조회, 참가/신청 기능.
- 페이지네이션, 타입당 개수 제한(추후 홈 미리보기 필요 시 별도).
- 지역/키워드 필터.
