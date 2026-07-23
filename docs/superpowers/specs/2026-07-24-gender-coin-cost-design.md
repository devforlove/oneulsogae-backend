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

## 3. 환불·금액 스냅샷

- `Match.propose(requesterGender)`: 이미 성별을 받음 → `datingInitAmount` = 신청자 성별 금액, `datingAcceptAmount` = 상대(반대 성별, `Gender.opposite()`) 금액으로 스냅샷. 실패 환불(`스냅샷/2`)은 자동 일관.
- `TeamMatch.propose()`: 신청팀 성별 파라미터 추가 → 같은 방식 스냅샷(`dateInitAmount`=신청팀, `dateAcceptAmount`=상대팀). 미팅은 남팀↔여팀 매칭 전제.
- `LoungeChatRequest`: 만기 환불이 `LOUNGE_CHAT_INIT.coinAmount / 2` 하드코딩 → **신청 시 실제 차감액 스냅샷 컬럼 추가**(엔티티 `initCoinAmount`, DB `init_coin_amount INT NOT NULL DEFAULT 32`). 기존 행은 전부 32 차감이었으므로 DEFAULT로 backfill 완결. 환불 = `initCoinAmount / 2`.

## 4. 표시(조회) 4곳 — 뷰어 성별 기준, 미상이면 null

- `ExtraIntroCandidatesResponse.coinCost: Int?` — 로그인 유저 성별로 계산해 전달.
- `LoungeChatRequestPage.acceptCoinAmount` — 뷰어(셀소 글 작성자=수락 주체) 성별.
- `SelfIntroPostDetailView.chatRequestCoinAmount: Int?` — 뷰어(신청 후보) 성별, 비로그인(userId null)이면 null.
- `GetRecommendedTeamDaoImpl.datingInitAmount/datingAcceptAmount` — DAO 시그니처에 뷰어 성별 파라미터 추가.

## 5. 웹·모바일

- 서버 응답 기반 표시는 자동 반영. 비용 필드 null이면 코인 뱃지/문구 숨김.
- 하드코딩 금액 전수 탐색 후 교체: 웹 `src/domains/wallet/domain/entities/Coin.ts`의 `LIKE_COST` 등 상수와 그 사용처, 모바일의 대응 상수. 서버값 사용이 가능하면 서버값, 아니면 성별 분기(로그인 유저 성별은 me/프로필 조회로 보유). 구체 목록은 구현 계획에서 확정.
- 약관(`TermsPage`)에 코인 비용 문구가 있으면 남/녀 병기로 갱신.

## 6. 테스트

- 유닛(Kotest): `coinAmount(gender)` 금액표 전수, `Match`/`TeamMatch` 스냅샷 성별 반영, `LoungeChatRequest` 스냅샷 환불.
- E2E: 여성 소개팅 신청 16 차감 vs 남성 32 차감(대표 1쌍), 표시 API 성별별 값, 비로그인 상세 조회 시 비용 null.

## 운영 DDL (수동 반영)

```sql
ALTER TABLE lounge_chat_requests ADD COLUMN init_coin_amount INT NOT NULL DEFAULT 32;
```
(실제 테이블명은 구현 시 엔티티 `@Table`로 확인)

## 검토한 대안

- **남/녀 enum 2개 분리**: DB 저장 이름·사용처 이중화, 조회 분기 복잡. 탈락.
- **enum 밖 정책 객체**(`CoinPolicy.costOf(type, gender)`): 금액 정보가 두 곳으로 갈라짐. enum이 금액 소유 구조라 탈락.
