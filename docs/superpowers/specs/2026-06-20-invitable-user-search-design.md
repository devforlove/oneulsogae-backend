# 초대 가능한 유저 닉네임 검색 — 설계

> 작성일: 2026-06-20 · 브랜치: `feat/team-formation` (team-formation 작업에 이어서)

## 배경

2:2 미팅 팀 초대 UX에서, 초대자가 **닉네임으로 초대 대상을 찾는** 검색 기능. 결과 리스트에 `id·닉네임·직업·회사명`을 포함한다.

검색 필드(닉네임·직업·회사명)는 모두 `user_details` 테이블에 있다(`nickname`, `job`, `company_name`). "초대 가능"은 팀 개념(같은 성별·활성 팀 없음)이라, 검색은 **match 도메인의 query 슬라이스**에 둔다. 표시용 `user_details` 조인은 기존 match query(`GetMatchWithPartnerDao`)가 상대 프로필을 조인하는 패턴과 동일하다.

`team_members` 테이블은 team-formation 작업(같은 브랜치)에만 존재하므로, 이 기능은 `feat/team-formation`에 이어서 작업한다(현재 미푸시 PR에 함께 포함).

## 결정 사항 (브레인스토밍 합의)

| 항목 | 결정 |
|---|---|
| 검색 대상 | **초대 가능한 유저만** — 같은 성별 + 활성 팀 없음 + 자기 제외 |
| 닉네임 매칭 | **정확히 일치(exact)**. 닉네임 유니크 제약이 없어 동명이 가능하므로 결과는 **리스트** |
| 후보 베이스 | **`match_user`** — 매칭 가능(초대 시 invite가 PROFILE_INCOMPLETE로 실패하지 않음)을 보장 |
| 페이지네이션 | **없음**(YAGNI). 정확히 일치라 결과가 작다. 정렬 userId 오름차순 |
| 요청자 비매칭 | `match_user`/성별 없으면 결과 **빈 리스트**(예외 대신 graceful) |
| 배치 | match 도메인 query 슬라이스, 엔드포인트는 `TeamController` GET |

## 1. 아키텍처 (match 도메인 query 슬라이스)

- **in-port** `SearchInvitableUsersUseCase` (`core/match/query/service/port/in`)
  - `fun search(requesterId: Long, nickname: String): List<InvitableUser>`
- **service** `SearchInvitableUsersService` (`core/match/query/service`, `@Transactional(readOnly = true)`)
  - 요청자 성별을 user 도메인 in-port `GetUserWithDetailUseCase`로 읽는다(`GetMatchesService`와 동일 방식). **`view.detail.gender`(nullable)를 직접 보고**, null이면 빈 리스트를 반환한다. (`UserWithDetailView.getGender()`는 `gender!!`라 null에서 NPE이므로 호출하지 않는다)
  - 자기 dao `SearchInvitableUsersDao`에 (requesterGender, requesterId, nickname)을 넘겨 위임.
- **dao** `SearchInvitableUsersDao` (`core/match/query/dao`) + `SearchInvitableUsersDaoImpl` (`infra/match/query`, QueryDSL `JPAQueryFactory`)
- **read model** `InvitableUser(userId: Long, nickname: String, job: String?, companyName: String?)` (`core/match/query/dto`)

> CQRS: query 서비스는 `@Transactional(readOnly = true)`, command 도메인/포트를 참조하지 않고 자기 dao + 타 도메인 in-port만 의존한다(`GetMatchesService` 선례).

## 2. 쿼리 (QueryDSL 단일 조회)

후보 베이스를 `match_user`로 두어 매칭 가능 유저만 후보로 삼고, 표시 필드는 `user_details` 조인으로 가져온다.

```sql
SELECT d.user_id, d.nickname, d.job, d.company_name
FROM match_user candidate
JOIN user_details d ON d.user_id = candidate.user_id
WHERE d.nickname = :nickname                       -- 정확히 일치
  AND candidate.gender = :requesterGender          -- 같은 성별
  AND candidate.user_id <> :requesterId            -- 자기 제외
  AND NOT EXISTS (                                  -- 활성 팀 없음
        SELECT 1 FROM team_members tm
        WHERE tm.user_id = candidate.user_id AND tm.deleted_at IS NULL)
ORDER BY candidate.user_id ASC
```

- QueryDSL 구현: `candidate = QMatchUserEntity`, `d = QUserDetailEntity`, `tm = QTeamMemberEntity`. `NOT EXISTS`는 `JPAExpressions.selectOne().from(tm).where(...)`로 표현.
- `match_user`/`user_details`/`team_members` 모두 `@SQLRestriction("deleted_at is null")`이라 활성 행만 스캔된다. (team_members의 deleted_at 조건은 NOT EXISTS 서브쿼리에도 명시 — @SQLRestriction이 서브쿼리 엔티티에도 적용되지만 의도를 드러내기 위해 함께 둔다)
- `requesterGender`는 서비스가 user in-port로 읽어 파라미터로 주입(상관 서브쿼리 불필요).
- `job`/`companyName`은 nullable(프로필 미완성 가능). 응답에 그대로 노출.

## 3. 엔드포인트 & 응답

`MatchController`가 query 엔드포인트를 들고 있는 관례대로, teams 리소스 응집을 위해 `TeamController`에 GET을 추가한다(컨트롤러가 command·query use case를 함께 주입하는 것은 HTTP 경계라 허용).

```
GET /teams/v1/invitable-users?nickname={nickname}   (인증 필요)
→ ApiResponse<List<InvitableUserResponse>>
```

- 요청: `@RequestParam nickname: String` 필수. 누락 시 Spring이 400. 공백만이면 `@NotBlank`(또는 컨트롤러 가드)로 400.
- `InvitableUserResponse(userId: Long, nickname: String, job: String?, companyName: String?)` (`api/match/response`) — `InvitableUser.of(...)`로 매핑.
- 요청자(인증 사용자) id는 `@LoginUser AuthUser`에서 얻는다.
- 매칭 없음 → 200 + 빈 배열.

## 4. 에러 처리

- 인증 토큰 없음 → 401 (Security).
- `nickname` 누락/공백 → 400 (요청 검증).
- 요청자 비매칭(성별 없음) → 200 + 빈 배열 (예외 없음).

## 5. 테스트 (E2E)

dao는 QueryDSL이라 관례대로 E2E로 검증한다(도메인 모델 로직이 없어 Kotest 유닛 불필요). `AbstractIntegrationSupport` + `IntegrationUtil`/`MatchUserEntityFixture`·`UserDetailEntityFixture`·`TeamFixture`(또는 엔티티 픽스처) 사용.

`SearchInvitableUsersE2ETest`:
- 같은 닉네임·같은 성별·매칭가능·팀없음 후보가 `userId/nickname/job/companyName`과 함께 반환된다.
- **자기 자신** 제외.
- **반대 성별** 제외.
- 이미 **활성 팀 소속**(team_members 행 존재)인 후보 제외.
- **match_user 없는**(매칭불가) 유저 제외.
- 동명이인 2명이면 둘 다(리스트) 반환.
- 공백 `nickname` → 400.
- 매칭 없음 → 200 + 빈 배열.

## 범위 밖

- 접두사/부분일치 검색, 페이지네이션, 닉네임 외 검색(직업·회사명 검색).
- 검색 결과에 추가 프로필 필드(나이·지역 등) 노출.
- 실제 초대 수행(기존 `POST /teams/v1` 담당).
