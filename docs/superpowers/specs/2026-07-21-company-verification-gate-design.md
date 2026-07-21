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
`UserDetail.companyName`을 본다. 이메일 인증이든 서류 이미지 심사든 **최종 승인 시 `companyName`이 채워진다**는 것은
이제 전제가 아니라 강제다: `VerifyCompanyEmailService.verify`가 `getUserCompanyUseCase.findCompanyNameByEmail(...)`로
회사명 매핑을 찾지 못하면 프로필에 반영하지 않고 그 자리에서 `BusinessException(UserErrorCode.COMPANY_NOT_FOUND)`
(`USER-034`, 400)를 던져 인증 자체를 실패시킨다. 인증했는데 `companyName`이 null이라 영원히 403인 사용자를 만들지
않기 위함이다. 덕분에 단일 조회로 끝나고 인증 경로가 늘어나도 판정 로직이 바뀌지 않는다.

## 설계

### 공통 — user 도메인에 판정 캡슐화

`companyName != null` 판정을 11곳(조회 3 + 명령 8)에 인라인하지 않도록 user query에 in-port를 하나 둔다.

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

### 4) 명령 API 검증

각 서비스의 메서드 최상단(분산 락 획득 직후, 매칭/글/팀 조회보다도 먼저)에서
`checkCompanyVerifiedUseCase.validateCompanyVerified(userId)`를 호출한다.
여덟 서비스가 일관된 위치를 갖고, 코인 차감보다 앞이라 미인증 요청은 과금 없이 fail-fast 한다.
(팀 초대/수락·셀소 작성은 코인을 다루지 않지만 배치 원칙은 동일하게 적용한다)

| API | 서비스 | 삽입 위치 |
|---|---|---|
| `POST /matches/v1/{matchId}/interest` | `SendInterestService` | 메서드 최상단 |
| `POST /matches/v1/extra` | `IntroduceExtraMatchService` | 메서드 최상단 |
| `POST /team-matches/v1/{teamMatchId}/interest` | `SendTeamInterestService` | 메서드 최상단 |
| `POST /lounge/v1/self-intro-posts/{postId}/chat-requests` | `RequestLoungeChatService` | 메서드 최상단 |
| `POST /lounge/v1/chat-requests/{requestId}/accept` | `AcceptLoungeChatService` | 메서드 최상단 |
| `POST /lounge/v1/self-intro-posts` | `RegisterSelfIntroPostService` | 메서드 최상단 |
| `POST /teams/v1/invitation` | `InviteTeamService` | 메서드 최상단 (초대자 `ownerId`만 검증) |
| `POST /teams/v1/{teamId}/acceptance` | `AcceptTeamInvitationService` | 메서드 최상단 (수락자 `userId` 검증) |

**팀 초대는 초대자만 검증하고 초대 대상은 검증하지 않는다.** `InviteTeamService.invite`는 `ownerId`로만
`validateCompanyVerified`를 호출하고 `command.invitedUserId`는 검증하지 않는다. 팀이 `ACTIVE`(결성 완료)로
전이하는 유일한 경로는 `AcceptTeamInvitationService.accept`뿐이므로, 초대 대상이 미인증이면 수락 시점의
게이트가 막아 결국 팀 결성을 차단한다. 초대 자체(상태는 `INVITING`)까지 막을 필요는 없다는 판단이다.

이 배치 때문에 존재하지 않는 매칭·글·팀에 대한 요청이라도 사용자가 미인증이면 404가 아니라 403을 받는다.
(소개·미팅·라운지 관심요청/대화신청뿐 아니라 팀 초대·수락, 셀소 작성도 동일)

## 테스트

도메인 모델 변경이 없으므로 E2E 중심이다. (`AbstractIntegrationSupport` + 엔티티 픽스처 + `RestAssuredDsl`)

- 리스트 조회 3건 × 2케이스: 프로필 `companyName`이 있는 사용자 → `companyVerified=true`, 없는 사용자 → `false`
- 명령 API 8건: 미인증 사용자 → 403, 코드 `USER-035`
- 기존 소개 리스트 E2E 테스트는 응답 구조 변경에 맞춰 갱신한다.

## 프론트엔드 대응 (백엔드에서 수정하지 않음 — 안내만)

- **소개 리스트 `GET /matches/v1`**: 응답이 배열 → `{ companyVerified, matches }` 객체로 변경. 응답 DTO와 파싱부 수정 필요.
- **미팅 `GET /team-matches/v1/meeting-tab`**, **라운지 `GET /lounge/v1/self-intro-posts`**: 필드 추가라 기존 파싱은 그대로 동작. `companyVerified`를 읽어 분기 처리만 추가.
- 관심요청 API(소개·미팅·라운지 대화신청)가 403 `USER-035`를 반환할 수 있으므로 인증 유도 화면으로 연결한다.
- 아래 5개 엔드포인트도 새로 403 `USER-035`를 반환할 수 있다: 추가 소개(`POST /matches/v1/extra`),
  라운지 대화신청 수락(`POST /lounge/v1/chat-requests/{requestId}/accept`), 셀소 작성
  (`POST /lounge/v1/self-intro-posts`), 팀 초대(`POST /teams/v1/invitation`), 팀 초대 수락
  (`POST /teams/v1/{teamId}/acceptance`). 이 중 **팀 초대/수락과 셀소 작성은 코인을 쓰지 않는 동작인데도 403이
  뜨므로**, "코인이 부족합니다" 류의 잔액 부족 안내와는 다른 문구(회사 인증 유도)로 분기해야 한다.
- **회사 이메일 인증 확정 `POST /users/v1/onboarding/company-email/verifications/confirm`**이 이제
  400 `USER-034`를 반환할 수 있다. 기존에는 회사명 매핑을 못 찾아도 200 + `isCompanyResolved: false`로 응답했지만,
  지금은 그 상황(요청 시점엔 존재하던 도메인 매핑이 확정 시점에 삭제된 엣지 케이스 등)에서 `VerifyCompanyEmailService`가
  인증 자체를 실패시켜 400을 던진다. 프론트가 `isCompanyResolved: false`를 성공 처리로 분기하던 부분을
  에러(400 `USER-034`) 처리로 옮겨야 한다.
- 같은 이유로 응답 필드 `isCompanyResolved`는 이제 인증이 성공하는 한 항상 `true`다(`companyName`이 null인 채
  200을 받는 경우가 없어져 `false` 분기가 도달 불가 코드가 됨). 필드 자체를 제거할지는 후속 협의로 남긴다.

## 범위 밖

- `companyName`이 비워지는(인증 만료/철회) 시나리오의 처리
- 셀소 상세·대화신청 목록 등 이번에 지정되지 않은 화면
- 리스트 조회 자체를 서버에서 차단하는 것 (프론트 분기 처리로 둔다)
- **미인증 사용자가 매칭 풀에 남는 것.** `MatchProfileSnapshot.companyName`은 선택 필드(`String?`, 회사 미인증이면
  null이어도 "매칭 가능"에는 영향 없음)이고, `CompleteOnboardingService.complete`는 온보딩 직후(`justOnboarded`)
  `syncMatchUser` → `recommendMatchUseCase.recommend` / `recommendTeamUseCase.recommend`를 곧바로 돌린다. 즉 회사
  인증 여부와 무관하게 온보딩만 마치면 매칭 읽기 모델(`match_user`)에 들어가고 상대의 소개·미팅 리스트에도 노출된다.
  그 결과 인증된 사용자가 미인증 상대에게 관심요청을 보내면 코인은 정상 차감되지만, 상대가 응답(수락 등)하는
  경로는 명령 게이트에서 403을 받으므로 매칭이 성사될 수 없다. 이 비일관성(코인 소모 vs 성사 불가)의 해소는
  이번 범위 밖이다.
- **"이탈/정리" 경로에는 의도적으로 게이트를 걸지 않았다.** 팀 해체(`DELETE /teams/v1/{teamId}`), 팀 초대 철회
  (`DELETE /teams/v1/{teamId}/invitation`), 팀 정보 수정(`PUT /teams/v1/{teamId}`), 매칭 종료
  (`DELETE /matches/v1/{matchId}`, `DELETE /team-matches/v1/{teamMatchId}`) 등은 이미 관계에 들어와 있는
  사용자가 빠져나가거나 정리하는 동작이라, 새로 관계를 만드는 것과 달리 미인증을 이유로 막을 이유가 없다.

## 운영 대응

- **배포 전 백필 필요**: `user_details`에서 `company_email is not null and company_name is null`인 레거시 행
  (구 인증 경로 등으로 이메일은 확인됐지만 회사명이 채워지지 않은 사용자)을 `user_companies`(email_domain → company_name)
  매핑으로 채워야 한다. 같은 사용자의 `match_user.company_name`도 함께 갱신해야 한다 — 같은 회사 소개 차단 판정
  (`SameCompanyIntroPredicates.notBlockedBySameCompanyIntro`)이 `QMatchUserEntity.companyName`(즉 `match_user` 테이블)을
  직접 보기 때문에, `user_details`만 갱신하면 차단 판정이 여전히 옛 상태로 계산된다. `user_companies`에 매핑이 없어
  백필이 불가능한 행은 재인증(회사 이메일 재인증)을 유도할 대상으로 남긴다.
- **`user_companies` 매핑 삭제/수정은 이제 "진행 중인 인증을 실패시키는 작업"이 됐다.** `RequestCompanyEmailVerificationService`
  단계(인증번호 요청)에서는 매핑이 있어 통과했더라도, `VerifyCompanyEmailService.verify`(확정) 시점에 그 사이 매핑이
  삭제·변경돼 있으면 `COMPANY_NOT_FOUND`(`USER-034`)로 실패한다. `user_companies`를 다루는 운영/관리자 작업은 이 시차
  리스크를 인지하고 진행해야 한다.
