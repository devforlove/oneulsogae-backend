# 어드민 승인 시 match_user.company_name 동기화 설계

**작성일:** 2026-07-05
**대상 브랜치:** feat/meeple-admin-module

## 목표

어드민이 회사 이미지 인증을 **승인**하면서 유저 회사명을 확정할 때, `user_details.company_name`뿐 아니라 매칭 읽기 모델 `match_user.company_name`도 함께 갱신한다. 이로써 같은-회사 소개 차단(`SameCompanyIntroPredicates`가 `match_user.company_name`을 읽음)이 스테일해지는 [[admin-company-image-approve-matchuser-stale-deferred]] 이슈를 해소한다.

이전에 "최소 범위"로 보류했던 항목을 사용자가 명시적으로 구현 요청.

## 배경

- companyName을 바꾸는 다른 경로(`VerifyCompanyEmailService`, `ResolveCompanyNameService`)는 match_user를 동기화하지만, 어드민 승인(`ReviewCompanyImageVerificationService.approve`)은 `user_details`만 직접 갱신했다.
- `MatchUserJpaRepository`에는 이미 단일 컬럼만 갱신하고 영향 행 수를 반환하는(행 없으면 0 = no-op) `@Modifying @Query` 관례가 있다(`updateRefuseSameCompanyIntro`, `updateLastLoginAt`).
- `match_user`는 매칭 가능한(대개 ACTIVE) 유저만 행을 갖는다(user_id 유니크). 온보딩 유저는 행이 없다.

## 아키텍처

admin은 core 비의존이므로 자체 out-port를 두고 infra가 match_user를 갱신한다(기존 `UpdateUserCompanyNamePort`와 동일 구조).

### meeple-admin

- `companyverification/command/application/port/out/UpdateMatchUserCompanyNamePort` (신규)
  - `fun updateCompanyName(userId: Long, companyName: String)`
- `ReviewCompanyImageVerificationService`
  - 생성자에 `UpdateMatchUserCompanyNamePort` 주입.
  - `approve(id, companyName)`: 인증 상태 저장 → `updateUserCompanyNamePort.updateCompanyName(userId, companyName)` → **`updateMatchUserCompanyNamePort.updateCompanyName(userId, companyName)`** 추가. 같은 `@Transactional`이라 원자적.
  - KDoc의 "[알려진 제약 — 보류] ... match_user 미동기화" 문구를 제거하고 "user_details·match_user를 함께 갱신한다"로 갱신.

### meeple-infra

- `MatchUserJpaRepository`에 메서드 추가 (기존 `updateRefuseSameCompanyIntro`와 동일 형태):
  ```kotlin
  @Modifying
  @Query("update MatchUserEntity m set m.companyName = :companyName where m.userId = :userId")
  fun updateCompanyName(userId: Long, companyName: String): Int
  ```
  (행이 없으면 0 = no-op, 예외 없음)
- `MatchUserAdapter`가 admin `UpdateMatchUserCompanyNamePort`를 **추가 구현**(엔티티당 어댑터 하나):
  - `override fun updateCompanyName(userId: Long, companyName: String) { matchUserJpaRepository.updateCompanyName(userId, companyName) }`

### 문서/메모리

- 메모리 `admin-company-image-approve-matchuser-stale-deferred.md`를 삭제하고 `MEMORY.md`에서 해당 줄 제거(이슈 해소). (부수 관찰이던 reject-미복원·trim은 별도 사안이라 유지 필요 없음 — 문서에서 함께 사라짐)

## 테스트

- `AdminCompanyVerificationReviewE2ETest`에 케이스 추가:
  - 유저 + `user_details`(companyName=null) + `match_user`(companyName="이전회사") + `company_image_verifications`(PENDING) persist.
  - `POST .../{id}/approve {"companyName":"미플"}` → 200.
  - DB 재조회: `user_details.companyName == "미플"` **및** `match_user.companyName == "미플"` 검증.
  - (match_user 행이 없는 경우 no-op으로 승인이 실패하지 않음은 기존 승인 테스트가 이미 커버 — 그 케이스엔 match_user 행이 없다.)
- match_user 픽스처(`MatchUserEntityFixture`)로 persist. DB 재조회는 `IntegrationUtil.getQuery()`. afterTest에 `QMatchUserEntity` deleteAll 추가.

## 결정 사항 (근거)

- **타겟 단일 컬럼 갱신 + no-op**: 사용자 요청("company_name 변경")과 기존 match_user 부분갱신 관례에 부합. 온보딩 유저(match_user 행 없음)는 자연히 no-op이라 승인 실패 없음.
- **전체 스냅샷 sync 미사용**: companyName만 필요하므로 무거운 전체 동기화는 과함(YAGNI).
- **엔티티당 어댑터 하나**: 새 어댑터 없이 `MatchUserAdapter`에 admin 포트를 추가 구현.

## 검증 기준

1. 승인 시 `match_user.company_name`이 입력값으로 바뀐다(행이 있을 때). (E2E)
2. match_user 행이 없어도 승인이 성공한다(no-op). (기존 승인 E2E 유지)
3. admin 모듈 core import 0건 유지. 전체 빌드·기존 테스트 통과.
4. 관련 보류 메모리 제거.
