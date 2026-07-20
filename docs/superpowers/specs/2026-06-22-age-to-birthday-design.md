# age → birthday 전환 설계

작성일: 2026-06-22

## 배경 / 문제

온보딩에서 사용자에게 **나이(`age: Int`)**를 입력받아 저장하고 응답으로 내려주고 있다.
원래 받았어야 하는 값은 **생년월일(`birthday`)**이다. 나이는 시간이 지나면 틀어지므로
잘못된 데이터 모델이다. 이를 일괄적으로 바로잡는다.

운영 데이터는 아직 없는 개발 단계다(기존 row 백필 불필요).

## 핵심 원칙

- **`birthday`(생년월일)를 단일 진실원천**으로 저장·전파한다.
- **나이는 표시 경계(API 응답 조립 시점)에서만** `TimeGenerator`의 `now` 기준으로 파생한다.
- 매칭 로직은 나이를 **비교하지 않는다**(확인됨 — 배치는 gender·regionCode로만 그룹핑,
  `validateMatchProfile`도 gender·regionCode만 검사). 따라서 나이는 순수 표시용이며,
  매칭 적격성은 **birthday 존재 여부**만 본다.

## 결정 사항 (확정)

1. **매칭 age 의존**: birthday를 진실원천으로, 매칭에 나이가 필요한 지점에서만 파생(접근 A).
2. **타입/포맷/검증**: 도메인·엔티티 `LocalDate`, DB `DATE`, JSON ISO-8601(`"1995-03-21"`).
   검증은 만 **19~100세**(기존 규칙 의미 보존). 미래 날짜는 19세 하한에 의해 자동 차단.
3. **DB 전환**: `age` 컬럼 제거 + `birthday DATE` 신규 추가. 운영 데이터 없음 →
   ddl-auto에 위임, 마이그레이션 SQL 스크립트는 남기지 않는다(레포 관례에 맞춤).
4. **응답 계약**: 모든 표시 응답은 **`age: Int` 그대로 유지**하고, 서버가 birthday에서 파생.
   타인 카드에 정확한 생년월일을 노출하지 않는다(프라이버시). → 프론트 표시 화면 변경 0.
5. **MatchBatchTarget**: age 필드를 **제거**(배치에서 미사용).

## 변경 범위 (레이어별)

### 1. 입력 (oneulsogae-api)
- `UpdateUserDetailRequest`: `age: Int?` → `birthday: LocalDate?` (`@field:NotNull`,
  메시지 "생년월일은 필수입니다."). 기존 `@Min(19)/@Max(100)`(나이 범위) 검증은 제거하고
  도메인 검증으로 이전.
- `toCommand()`: `age` → `birthday` 매핑.
- `UpdateUserDetailCommand`: `age: Int` → `birthday: LocalDate`.

### 2. 도메인 (oneulsogae-core)
- `UserDetail`: `age: Int?` → `birthday: LocalDate?`.
  - `initProfile(...)`: age 파라미터 → birthday.
  - `validateBirthday(birthday, today)`로 만 19~100세 검증 캡슐화(`if…throw` 나열 금지).
  - 표시용 파생: `fun age(today: LocalDate): Int?`.
  - 보존 로직("나이/성별/키…는 보존")의 age를 birthday로 교체.
  - `matchProfileSnapshotOrNull()`: age 비-null 요구 → birthday 비-null 요구.
- `MatchProfileSnapshot`(이벤트 페이로드): `age: Int` → `birthday: LocalDate`.
- `MatchUser`(매칭 도메인): `age: Int` → `birthday: LocalDate`.
- 공용 파생 함수: `fun LocalDate.ageAt(today: LocalDate): Int`(중복 제거). 배치 위치는
  core common(예: `common/time` 인접)에 두고 api 응답·도메인이 공유.
- `UserErrorCode`: 나이 검증용 에러코드를 birthday 의미로 추가/교체(예: `BIRTHDAY_REQUIRED`,
  `INVALID_BIRTHDAY_AGE_RANGE`). 기존 age 관련 코드가 있으면 정리.

### 3. 저장 (oneulsogae-infra)
- `UserDetailEntity`: `@Column(name="age") age: Int?` → `@Column(name="birthday") birthday: LocalDate?`.
- `MatchUserEntity`: `@Column(name="age", nullable=false) age: Int` →
  `@Column(name="birthday", nullable=false) birthday: LocalDate`.
- `UserDetailMapper` / `MatchUserMapper`: `toDomain`/`toEntity`의 age ↔ birthday 교체.

### 4. 조회 / 읽기 모델 (core query + infra dao)
읽기 모델 필드 `age` → `birthday`로 교체:
- `InvitableUser`, `ReceivedInvitation`(내부 `ReceivedInvitationInviter`),
  `SentInvitation`(내부 `SentInvitationMember`), `MatchWithPartner`, `UserDetailView`.
- `MatchBatchTarget`: age 필드 **제거**(미사용).

DAO(QueryDSL projection) 컬럼 스왑(`match_user.age`/`user_details.age` → `.birthday`):
- `SearchInvitableUsersDaoImpl`, `GetReceivedInvitationsDaoImpl`, `GetSentInvitationDaoImpl`,
  `GetMatchWithPartnerDaoImpl`, `GetUserDetailDaoImpl`, `GetUserWithDetailDaoImpl`.
- `GetMatchBatchTargetDaoImpl`: age projection 제거.

### 5. 출력 (oneulsogae-api) — 응답 JSON은 `age: Int` 불변
각 응답 팩토리가 읽기모델/도메인의 `birthday` + 주입된 `now`(TimeGenerator)로 `age`를 파생:
- `UserProfileResponse`(자기 프로필; query `UserDetailView`·command `UserDetail` 양 경로),
  `InvitableUserResponse`, `MatchResponse.PartnerResponse`, `ReceivedInvitationResponse.Inviter`,
  `SentInvitationResponse.Member`.
- 컨트롤러가 `TimeGenerator`를 주입받아 `today`를 응답 팩토리에 전달. `ageAt(today)` 사용.
- birthday가 null일 수 있는 경로(`UserProfileResponse.age: Int?` 등)는 nullable 파생 유지.

### 6. 테스트
- 도메인 Kotest(`UserDetailTest`, `MatchUserTest`): `validateBirthday` 경계(만 19/100,
  미래 날짜), `age(today)` 파생을 **고정 today**로 검증. 스냅샷이 birthday를 담는지.
- 픽스처: `UserDetailFixture`, `UserDetailEntityFixture`, `MatchUserEntityFixture`를
  birthday로 교체(고정 생년월일).
- E2E: 온보딩 요청 바디 `"birthday": "..."`로 변경. 응답 `age` 단언은 **고정 clock 기준
  기대 나이**로 갱신(`RequestCompanyEmailVerificationE2ETest`, `GetReceivedInvitationsE2ETest`,
  `SearchInvitableUsersE2ETest`, `GetSentInvitationE2ETest`, `GetMatchesE2ETest`,
  `MatchUserSyncE2ETest`).

## 프론트엔드 영향 (이 작업 범위 밖, 참고)
- 표시 화면: 응답이 `age: number` 그대로라 **변경 없음**.
- 온보딩 폼만: 숫자 나이 입력 → 날짜(생년월일) 입력으로 바꾸고 `birthday`(ISO-8601) 전송.
  (별도 작업.)

## 성공 기준
- 온보딩이 `birthday`(ISO-8601)를 받아 `user_details.birthday`에 저장한다.
- 만 19세 미만/100세 초과/미래 날짜 birthday는 검증 실패(테스트로 확인).
- 모든 표시 응답이 birthday에서 파생된 `age`를 고정 clock 기준으로 정확히 내려준다(E2E 확인).
- 코드 전반에 `age` 저장/입력이 사라지고 birthday가 단일 진실원천이 된다.
- `./gradlew build`(유닛+E2E) 통과.
