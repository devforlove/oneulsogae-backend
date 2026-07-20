# 솔로 유저 대상 팀 추천 (RecommendedTeam) 설계

작성일: 2026-06-22

## 1. 목적

아직 미팅 팀을 구성하지 않은(팀 미소속) 솔로 유저에게, 결성된(ACTIVE) 상대 팀을 추천해 **미팅탭에 표시**한다. 솔로 유저는 팀이 없어 미팅 신청을 할 수 없으므로 이 기능은 **표시 전용(teaser)** 이다 — "팀을 만들면 이런 팀들과 미팅할 수 있다"는 동기 부여가 목적이다.

## 2. 요구사항 (확정)

- **추천 수신 대상**: 팀이 없는 솔로 유저. `match_user`에 적재돼 있고(= 정식 가입 + 필수 필드 완성), 활성(ACTIVE) 팀에 소속되지 않은 유저.
- **추천 대상 팀**: **반대 성별 + 같은 활동 권역**의 **ACTIVE(결성) 팀**.
  - "같은 권역" 규칙: **팀원 중 한 명이라도 요청자와 같은 `regionCode`** 이면 같은 권역으로 본다. (팀원 2명의 권역이 다를 수 있고, 둘 다 잠재적 미팅 상대이므로 풀을 넓게 잡는다)
- **노출 개수**: 솔로 유저당 **1개**.
- **생성 시점**: **일일 배치**. (솔로 `RunDailyMatchBatch`와 같은 인프라/패턴)
- **갱신 규칙**: **주기마다 교체**. 재추천 방지(이력 누적)는 하지 않는다 — 이전에 노출한 팀이 다음 주기에 다시 나와도 무방.

## 3. 비범위 (YAGNI)

- 솔로 유저의 미팅 신청/수락 (팀이 없으므로 불가).
- 추천 이력/재추천 방지(no-repeat), 추천 만료(expiresAt), 추천 상태(status: 수락/거절).
- 추천 점수/랭킹. (1개만 고르므로 선택 정책은 단순)

## 4. 엔티티 설계

### 4.1 접근 비교

| 접근 | 내용 | 채택 |
|---|---|---|
| **A. 최소 포인터 행** | `(userId, teamId, recommendedDate)`만 저장. 표시 데이터는 읽기 시점에 조인 | ✅ |
| B. 이력 누적 + status/expiresAt | `solo_matches`처럼 라이프사이클 보유 | ❌ "교체" 결정·신청 불가와 불일치, 과설계 |
| C. 표시 스냅샷 복제 | 팀명·팀원 프로필까지 행에 복사해 조인 제거 | ❌ `match_user`/`teams` 통째 복제 + 동기화·staleness 비용. 프로젝트가 피하는 안티패턴 |

→ **A 채택.** 추천 행은 `(userId → teamId)` 포인터일 뿐이고, 표시 데이터(팀명·소개·팀원 프로필)는 읽기 시점에 `teams + team_members + match_user`를 조인해 채운다. 이는 기존 표시 조회 패턴(`GetMatchWithPartnerDao`)과 일치하며, 매치별 staleness를 읽기 조인이 자동 흡수한다.

### 4.2 엔티티 정의

위치: `oneulsogae-infra/.../match/command/entity/RecommendedTeamEntity.kt`

```kotlin
@Entity
@Table(
    name = "recommended_teams",
    uniqueConstraints = [
        // 솔로 유저당 추천 1개. 일일 배치가 이 키로 교체(upsert)한다.
        UniqueConstraint(name = "ux_user_id", columnNames = ["user_id"]),
    ],
)
class RecommendedTeamEntity(
    /** 추천을 받는, 팀 없는 솔로 유저. */
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    /** 추천된 ACTIVE(결성) 팀. */
    @Column(name = "team_id", nullable = false)
    val teamId: Long,

    /** 추천이 생성된 배치 일자. (관측·신선도 확인용) */
    @Column(name = "recommended_date", nullable = false)
    val recommendedDate: LocalDate,
) : BaseEntity()
```

### 4.3 설계 결정과 근거

- **소프트 삭제 없음(`@SQLRestriction` 미적용)**: 매 배치가 행을 교체(upsert by `userId`)하므로 라이프사이클 상태가 불필요하다. `match_user`처럼 하드 교체한다.
- **`status`/`expiresAt` 없음**: 수락/거절·만료 개념이 없는 표시 전용 데이터다.
- **유니크 `user_id`**: 유저당 1행 보장 + 배치 교체 키 + 표시 단건 조회 키를 겸한다. 단건 lookup이라 별도 조회 인덱스가 불필요하다.
- **`team_id` 인덱스 없음**: 현 범위에선 팀 기준 역방향 조회가 없다. (팀 해체 시 추천 행 정리는 읽기 조인이 흡수하므로 불필요)
- **staleness는 읽기 조인이 흡수**: 추천된 팀이 장중에 해체/상태 변경되면 표시 쿼리의 `teams`(`@SQLRestriction deleted_at is null` + `status = ACTIVE`) 조인에서 자연히 빠진다. 다음 배치까지 그 유저의 추천 칸이 비게 되며, 이는 허용한다.

## 5. 주변 슬라이스 (엔티티가 놓이는 자리)

이번 작업의 1차 산출물은 엔티티이지만, 슬라이스 전체의 자리를 명시한다. (헥사고날/CQRS 규칙 준수)

### 5.1 쓰기 경로 — 일일 배치

- **모듈**: `oneulsogae-scheduler` (core 비의존, 솔로 배치와 동일).
- **out-port**: `SaveRecommendedTeamPort` (upsert). 후보 조회용 dao(대상 솔로 유저, ACTIVE 팀 풀).
- **infra adapter**: `RecommendedTeamAdapter`가 `SaveRecommendedTeamPort`를 구현(엔티티당 어댑터 하나). `userId` 유니크로 교체.
- **배치 로직**:
  1. 대상 = `match_user`에 있으나 활성 `team_members`가 없는 솔로 유저.
  2. 각 대상의 `gender`/`regionCode`로 **반대 성별 ACTIVE 팀** 중 **팀원 한 명이라도 같은 권역**인 팀 1개를 선택.
  3. `RecommendedTeamEntity`를 `userId` 기준 교체 저장.
- 솔로 배치처럼 유저 단위 격리(한 유저 실패가 전체에 전파되지 않음).

### 5.2 읽기 경로 — 표시

- **모듈/패키지**: match `query` 슬라이스 (자기 dao만 의존).
- **read model**: `RecommendedTeam` (팀명·소개 + 팀원 2명의 표시 프로필: 닉네임·성별·생일/나이·권역·프로필 이미지).
- **dao**: `GetRecommendedTeamDao` — QueryDSL로 `recommended_teams ⋈ teams(ACTIVE) ⋈ team_members ⋈ match_user`.
- **service/port**: `GetRecommendedTeamUseCase` / `GetRecommendedTeamService`(`@Transactional(readOnly = true)`).
- **endpoint**: `TeamController` 또는 미팅탭 컨트롤러에 `GET` 추가. 팀 있는 유저에겐 노출하지 않는다(미팅탭이 솔로 유저 섹션에서만 호출).

## 6. 테스트 전략

- **엔티티/매퍼 왕복**: infra 매퍼 단위 검증 (도메인 ↔ 엔티티).
- **읽기 dao**: E2E (`AbstractIntegrationSupport` + 엔티티 픽스처) — 반대 성별·같은 권역(팀원 한 명 일치)·ACTIVE 팀만 조회되는지, 해체 팀이 빠지는지.
- **배치**: 대상 선정(팀 없는 솔로만)·교체(주기마다 1행 유지) E2E.

## 7. 마이그레이션

`recommended_teams` 테이블 신규 DDL. 컬럼: `id`(PK)·`user_id`·`team_id`·`recommended_date` + BaseEntity 감사 컬럼. 유니크 `ux_user_id(user_id)`.
