# 남녀별 코인 비용 분리 설계

2026-07-24 승인. `CoinUsageType`의 고정 코인 비용을 남녀별로 분리한다. 남자는 기존 금액 유지, 여자는 전부 절반.

## 정책 결정 사항

- 금액표 (남 / 여): DATING_INIT 32/16 · DATING_ACCEPT 32/16 · MEETING_INIT 40/20 · MEETING_ACCEPT 40/20 · EXTRA_INTRO 30/15 · LOUNGE_CHAT_INIT 32/16 · LOUNGE_CHAT_ACCEPT 32/16.
- 비로그인·성별 미상 사용자에게는 비용을 **표시하지 않음**(응답 필드 null, 프론트 숨김).
- 차감 경로에서 성별 null(이론상)이면 남자 금액 fallback. (차감 경로는 ACTIVE 유저라 실제 발생 없음)
- 웹·모바일 하드코딩 비용 표시도 이번에 함께 수정.
- enum 이름은 불변 — DB `coin_histories.coin_usage_type` 호환.

## 1. enum (oneulsogae-common)

```kotlin
enum class CoinUsageType(val description: String, private val maleAmount: Int, private val femaleAmount: Int) {
	DATING_INIT("소개팅 신청", 32, 16),
	MEETING_INIT("미팅 신청", 40, 20),
	DATING_ACCEPT("소개팅 수락", 32, 16),
	MEETING_ACCEPT("미팅 수락", 40, 20),
	EXTRA_INTRO("추가 소개", 30, 15),
	LOUNGE_CHAT_INIT("셀소 대화 신청", 32, 16),
	LOUNGE_CHAT_ACCEPT("셀소 대화 수락", 32, 16);

	fun coinAmount(gender: Gender): Int = if (gender == Gender.FEMALE) femaleAmount else maleAmount
}
```

기존 `coinAmount` 프로퍼티는 **제거** — 전 사용처가 컴파일 에러로 드러나 수정 누락이 불가능하다. `Gender`는 common 소속이라 의존 문제 없음.

## 2. 차감 지점 5곳

| 서비스 | 성별 출처 | 변경 |
|---|---|---|
| `SendInterestService` (DATING_*) | `match.members` 내 actor의 `MatchMember.gender` | 호출 교체 |
| `IntroduceExtraMatchService` (EXTRA_INTRO) | `requester.gender` (MatchUser) | 호출 교체 |
| `SendTeamInterestService` (MEETING_*) | `actorTeam.gender` (completeMatch 경로는 `teams.findByActiveMember(userId).gender`로 재도출) | 호출 교체 |
| `RequestLoungeChatService` (LOUNGE_CHAT_INIT) | `requesterDetail?.gender` (이미 로드) | 호출 교체 |
| `AcceptLoungeChatService` (LOUNGE_CHAT_ACCEPT) | **미보유** → `GetUserDetailUseCase` 신규 주입해 조회 | 주입 추가 + 호출 교체 |

## 3. 환불·금액 스냅샷 (2026-07-24 수정 — 설계 결함 교정)

**교정 사유**: 소개팅·미팅은 남녀(양팀) 어느 쪽이든 먼저 신청할 수 있어, 매칭 헤더 단일 스냅샷(`datingInitAmount`)으로는 남녀 금액을 표현할 수 없다(생성 시점엔 누가 신청할지 미정). 승인된 원안(propose 시 성별 스냅샷)은 폐기.

**교정 설계 (사용자 승인: 실차감액 스냅샷)**
- **차감**: 행위 시점에 행위자 성별로 `coinAmount(gender)` 계산. 매칭 헤더 스냅샷은 차감에 사용하지 않는다.
- **환불 = 실제 차감액의 절반**: 신청(차감) 시 지불액을 신청 레코드에 스냅샷.
  - `solo_match_members.paid_init_amount INT NULL` — 관심 신청 차감 시 기록. `Match.failureRefunds()` = `paidInitAmount/2`, null(구행)이면 기존 헤더 `datingInitAmount/2` fallback.
  - 팀 매칭 참가팀 테이블(`MatchedTeamEntity`)에 `paid_init_amount INT NULL` — 동일 규칙, fallback은 헤더 `dateInitAmount/2`.
  - `lounge_chat_requests.init_coin_amount INT NULL` — 신청 차감 시 기록. `expiryRefundAmount()` = `initCoinAmount/2`, null(구행)이면 기존 정책값 32/2 fallback.
  - 과도기(배포 전 차감 건) 포함 항상 "낸 돈의 절반"이 보장되고, 향후 금액 개정에도 안전.
- **헤더 `datingInitAmount`/`dateAcceptAmount` 필드**: 차감·표시에서 미사용으로 전환하되 구행 환불 fallback 용도로 유지(제거 안 함). propose 기본값은 기존값(남성 금액) 그대로.
- 수락은 즉시 성사라 환불 없음 — 수락액 스냅샷 불필요.

## 4. 표시(조회) — 뷰어 성별 기준, 미상이면 null

- `MatchResponse.datingInitAmount/datingAcceptAmount` (소개팅 목록/상세) — 헤더 값 대신 뷰어(로그인 유저=참가자) 성별로 계산.
- `RecommendedTeamResponse.datingInitAmount/datingAcceptAmount` (`GetRecommendedTeamDaoImpl`) — 뷰어 성별 파라미터 전달.
- `ExtraIntroCandidatesResponse.coinCost` — 로그인 유저 성별로 계산.
- `LoungeChatRequestPage.acceptCoinAmount` — 뷰어(셀소 글 작성자=수락 주체) 성별.
- `SelfIntroPostDetailView.chatRequestCoinAmount: Int?` — 뷰어(신청 후보) 성별, 비로그인(userId null)이면 null.

## 5. 웹·모바일 (탐색 완료 — 2026-07-24)

- 소개팅·미팅·추가 소개·셀소 비용 표시는 웹·모바일 모두 서버 응답 필드 사용 — **자동 반영, 코드 수정 불필요**.
- 수동 수정은 **웹 약관 1곳뿐**: `TermsPage.tsx:198`의 `LIKE_COST=32` 문구 → 남/녀 금액 병기(예: "남성 32코인·여성 16코인"). `Coin.ts`의 `LIKE_COST` 상수 처리 포함.
- `SelfIntroPostDetailView.chatRequestCoinAmount`가 null(비로그인)일 때 웹 셀소 상세 토스트 문구가 "undefined코인"이 되지 않는지 확인·방어.

## 6. 테스트

- 유닛(Kotest): `coinAmount(gender)` 금액표 전수, `Match`/`TeamMatch` 스냅샷 성별 반영, `LoungeChatRequest` 스냅샷 환불.
- E2E: 여성 소개팅 신청 16 차감 vs 남성 32 차감(대표 1쌍), 표시 API 성별별 값, 비로그인 상세 조회 시 비용 null.

## 운영 DDL (수동 반영)

```sql
ALTER TABLE solo_match_members ADD COLUMN paid_init_amount INT NULL;
ALTER TABLE matched_teams ADD COLUMN paid_init_amount INT NULL;
ALTER TABLE lounge_chat_requests ADD COLUMN init_coin_amount INT NULL;
```
(실제 테이블명은 구현 시 각 엔티티 `@Table`로 확인해 확정)

## 검토한 대안

- **남/녀 enum 2개 분리**: DB 저장 이름·사용처 이중화, 조회 분기 복잡. 탈락.
- **enum 밖 정책 객체**(`CoinPolicy.costOf(type, gender)`): 금액 정보가 두 곳으로 갈라짐. enum이 금액 소유 구조라 탈락.
