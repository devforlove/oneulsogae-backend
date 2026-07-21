# 회사 인증 게이트 설계 (소개·미팅·라운지)

- 작성일: 2026-07-21
- 상태: 승인됨

## 배경

회사 인증을 완료한 사용자만 서비스를 이용할 수 있도록 한다. 두 가지가 필요하다.

1. **표시용 플래그**: 소개 리스트·미팅 리스트·라운지 셀소 리스트 응답에 **로그인 사용자 본인**의 회사 인증 여부를 내려, 프론트엔드가 미인증 사용자에게 블러/CTA 등으로 분기 처리한다.
2. **서버 검증**: 각 탭의 관심요청 API에서 미인증 사용자를 차단한다. (플래그만으로는 우회 가능)

## 결정 사항

| 항목 | 결정 |
|---|---|
| 플래그 주체 | 조회하는 본인(로그인 사용자). 리스트에 노출되는 상대방이 아니다. |
| 인증 판정 기준 | `UserDetail.companyName != null` |
| 에러코드 | `UserErrorCode.COMPANY_NOT_VERIFIED` 1개를 세 도메인이 공유 |
| 소개 리스트 응답 | 배열 → 객체로 래핑 (프론트엔드 수정 필요) |

### 판정 기준을 `companyName != null`로 둔 이유

`CompanyEmailVerificationEntity.verifiedAt` / `CompanyImageVerificationEntity.status`를 직접 보는 대신 결과값인
`UserDetail.companyName`을 본다. 이메일 인증이든 서류 이미지 심사든 **최종 승인 시 `companyName`이 채워진다**는 전제이며,
단일 조회로 끝나고 인증 경로가 늘어나도 판정 로직이 바뀌지 않는다.

## 설계

### 공통 — user 도메인에 판정 캡슐화

`companyName != null` 판정을 6곳(조회 3 + 명령 3)에 인라인하지 않도록 user query에 in-port를 하나 둔다.

```kotlin
// core/user/query/service/port/in/CheckCompanyVerifiedUseCase.kt
interface CheckCompanyVerifiedUseCase {
    /** 회사 인증 여부. 리스트 응답 플래그용. */
    fun isCompanyVerified(userId: Long): Boolean

    /** 미인증이면 BusinessException(COMPANY_NOT_VERIFIED)를 던진다. 관심요청 검증용. */
    fun validateCompanyVerified(userId: Long)
}
```

- 구현체 `CheckCompanyVerifiedService` — `@Service` + `@Transactional(readOnly = true)`, 기존 `GetUserDetailDao` 재사용. 새 dao/out-port를 만들지 않는다.
- `UserErrorCode`에 추가: `COMPANY_NOT_VERIFIED("USER-035", "회사 인증이 완료된 사용자만 이용할 수 있습니다.", HttpStatus.FORBIDDEN)`
- command 서비스가 user query in-port를 주입하는 것은 `RequestLoungeChatService`가 `GetUserDetailUseCase`를 주입하는 기존 선례와 동일하다.

**CLAUDE.md 검증 원칙에서의 예외**: "서비스에 `if…throw`를 나열하지 말고 도메인 모델의 `validate…`로 캡슐화한다"에서
이 건은 예외로 둔다. 회사 인증 여부는 match/teammatch/lounge 도메인 모델의 상태가 아니라 **다른 도메인의 사용자 속성**이며,
도메인 모델에 넣으려면 세 도메인 모두에 파라미터를 흘려야 한다. 대신 판정 규칙 자체는 위 in-port 한 곳에 캡슐화한다.

### 1) 소개 탭 — `GET /matches/v1`

현재 `List<MatchResponse>`를 그대로 반환해 최상위 플래그를 담을 자리가 없다. 객체로 래핑한다.

- **core**: read model `MyMatches(companyVerified: Boolean, matches: List<MatchWithPartner>)` 신설
  (`core/solomatch/query/dto/`). `GetMatchesUseCase.getMatches(userId)` 반환 타입을 `List<MatchWithPartner>` → `MyMatches`로 변경.
  `GetMatchesService`가 `CheckCompanyVerifiedUseCase`를 주입해 채운다. 기존 정렬(`MatchesWithPartner.sortedForDisplay()`)은 그대로 둔다.
- **api**: `MatchListResponse(companyVerified: Boolean, matches: List<MatchResponse>)` 신설. `SoloMatchController.myMatches` 반환 타입 변경.
- **응답 변화**: `[...]` → `{ "companyVerified": true, "matches": [...] }`

### 2) 미팅 탭 — `GET /team-matches/v1/meeting-tab`

필드 추가만 하면 된다.

- `MeetingTab`(core read model)에 `companyVerified: Boolean` 추가
- `GetMeetingTabService`가 `CheckCompanyVerifiedUseCase`를 주입해 채움
- `MeetingTabResponse`에 `companyVerified` 추가 (`of` 매핑 포함)

### 3) 라운지 셀소 리스트 — `GET /lounge/v1/self-intro-posts`

- `SelfIntroPostPage`(일급 컬렉션)에 `companyVerified: Boolean = false` 추가.
  기존 `withImageUrls` / `withAuthorAges` / `withPendingChatRequestCounts`가 private 생성자를 재호출하므로 **모두 이 값을 전달**하도록 수정하고,
  같은 패턴의 `withCompanyVerified(verified: Boolean)`을 추가한다.
- `GetSelfIntroPostsService`가 `CheckCompanyVerifiedUseCase`를 주입해 `withCompanyVerified(...)` 호출
- `SelfIntroPostPageResponse`에 `companyVerified` 추가
- 셀소 **상세**(`SelfIntroPostDetailView`)는 이번 범위에 포함하지 않는다.

### 4) 관심요청 API 검증

각 서비스의 메서드 최상단(분산 락 획득 직후, 매칭/글 조회보다도 먼저)에서
`checkCompanyVerifiedUseCase.validateCompanyVerified(userId)`를 호출한다.
세 서비스가 일관된 위치를 갖고, 코인 차감보다 앞이라 미인증 요청은 과금 없이 fail-fast 한다.

| API | 서비스 | 삽입 위치 |
|---|---|---|
| `POST /matches/v1/{matchId}/interest` | `SendInterestService` | 메서드 최상단 |
| `POST /team-matches/v1/{teamMatchId}/interest` | `SendTeamInterestService` | 메서드 최상단 |
| `POST /lounge/v1/self-intro-posts/{postId}/chat-requests` | `RequestLoungeChatService` | 메서드 최상단 |

이 배치 때문에 존재하지 않는 매칭·글에 대한 요청이라도 사용자가 미인증이면 404가 아니라 403을 받는다.

## 테스트

도메인 모델 변경이 없으므로 E2E 중심이다. (`AbstractIntegrationSupport` + 엔티티 픽스처 + `RestAssuredDsl`)

- 리스트 조회 3건 × 2케이스: 프로필 `companyName`이 있는 사용자 → `companyVerified=true`, 없는 사용자 → `false`
- 관심요청 3건: 미인증 사용자 → 403, 코드 `USER-035`
- 기존 소개 리스트 E2E 테스트는 응답 구조 변경에 맞춰 갱신한다.

## 프론트엔드 대응 (백엔드에서 수정하지 않음 — 안내만)

- **소개 리스트 `GET /matches/v1`**: 응답이 배열 → `{ companyVerified, matches }` 객체로 변경. 응답 DTO와 파싱부 수정 필요.
- **미팅 `GET /team-matches/v1/meeting-tab`**, **라운지 `GET /lounge/v1/self-intro-posts`**: 필드 추가라 기존 파싱은 그대로 동작. `companyVerified`를 읽어 분기 처리만 추가.
- 관심요청 API가 403 `USER-035`를 반환할 수 있으므로 인증 유도 화면으로 연결한다.

## 범위 밖

- `companyName`이 비워지는(인증 만료/철회) 시나리오의 처리
- 셀소 상세·대화신청 목록 등 이번에 지정되지 않은 화면
- 리스트 조회 자체를 서버에서 차단하는 것 (프론트 분기 처리로 둔다)
