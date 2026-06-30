# 대상 직접 지정 신고 통합 API 설계

작성일: 2026-06-30

## 배경

프론트(meeple-frontend) 소개탭(`ProfileCard`)·미팅탭(`TeamCard`) 카드에 신고하기 UI가 추가됐다.
기존 신고 API(`POST /reports/v1`)는 `chatRoomId`를 받아 chat 도메인에서 매칭 정보를 조회해
신고 대상(상대 유저/팀)을 정한다. 그러나 추천 카드 맥락에는 채팅방이 없으므로,
신고 대상 식별자(상대 유저 id / 팀 id)를 요청에서 직접 받는 새 API가 필요하다.

## 목표

`type`(신고 사유)과 `targetType`(USER/TEAM 구분) + `targetId`로 1:1·팀 신고를 모두 처리하는
통합 신고 API를 추가한다. 기존 채팅방 기반 신고 API는 그대로 유지한다.

## 비목표 (이번 범위 밖)

- 자기 자신/자기 팀 신고 차단
- `ETC` 사유 시 `description` 필수화
- 신고 조회(query) 기능

## 엔드포인트

`POST /reports/v1/targets`

요청 본문:
```json
{
  "type": "ABUSE_DEFAMATION",   // ReportType (신고 사유) — 기존 엔드포인트와 동일 필드명
  "targetType": "USER",          // ReportTargetType: USER(1:1 소개) | TEAM(팀 미팅)
  "targetId": 12345,             // USER면 상대 유저 id, TEAM이면 팀 id
  "description": "..."           // 선택, 최대 1000자
}
```
응답: `{ "data": { "reportId": N }, "success": true }`

검증:
- `type`, `targetType`, `targetId` 필수(null 불가) — 누락 시 400
- `description` 최대 1000자

## 변경 상세

### meeple-common
- `com.org.meeple.common.report.ReportTargetType { USER, TEAM }` 추가.

### meeple-core (report 도메인)
- `Report.create(...)`의 매칭 종류 파라미터를 `ChatRoomMatchType` → `ReportTargetType`로 변경
  (report 도메인이 chat enum에 의존하지 않도록 커플링 정리). `USER`→`to_user_id`, `TEAM`→`to_team_id`.
- 기존 `CreateReportService`는 `ChatRoomMatchType`(SOLO/TEAM) → `ReportTargetType`(USER/TEAM)로 매핑해 호출.
- 신규 command 슬라이스:
  - in-port `CreateTargetReportUseCase.create(reporterId: Long, command: CreateTargetReportCommand): Report`
  - command `CreateTargetReportCommand(type, targetType, targetId, description)`
  - service `CreateTargetReportService` — 주입: `SaveReportPort`, `GetUserByIdUseCase`, `GetTeamByIdUseCase`.
    - `USER` → `getUserByIdUseCase.getById(targetId)` (없으면 USER-001)
    - `TEAM` → `getTeamByIdUseCase.getById(targetId)` (없으면 TEAM-005)
    - 검증 후 `Report.create(...)` → `saveReportPort.save(...)`. `chatRoomId`는 null.

### meeple-core (teammatch 도메인)
- 신규 in-port `GetTeamByIdUseCase.getById(teamId: Long): Team` — 없으면 `TEAM_NOT_FOUND` throw.
- 구현 `GetTeamByIdService`가 기존 out-port `GetTeamPort.findById`로 조회.
- report는 반환 `Team`을 존재 검증 용도로만 사용.

### meeple-api
- `ReportController`에 `@PostMapping("/targets")` 추가, `CreateTargetReportUseCase` 주입.
- 요청 DTO `CreateTargetReportRequest(type, targetType, targetId, description)` + `toCommand()`.
- 응답은 기존 `ReportResponse` 재사용.

### meeple-infra / DB
- **변경 없음.** `reports` 테이블에 `to_user_id`/`to_team_id`/`chat_room_id` 컬럼이 이미 존재.
- `ReportEntity`/`ReportAdapter`/`ReportMapper` 재사용.

## 테스트

- 단위(Kotest): `Report.create`가 `ReportTargetType`별로 `to_user_id`/`to_team_id`를 올바르게 채우는지.
- E2E(`POST /reports/v1/targets`):
  - USER 신고 → `to_user_id` 채움, `to_team_id`·`chat_room_id` null
  - TEAM 신고 → `to_team_id` 채움, `to_user_id` null
  - 없는 유저 → 404 USER-001, 미저장
  - 없는 팀 → 404 TEAM-005, 미저장
  - 필수 필드 누락 → 400
- 기존 채팅방 신고 E2E는 매핑 리팩터 후에도 통과 유지.

## 프론트엔드 후속 (백엔드 외 — 안내 대상)

새 DTO(`type`+`targetType`+`targetId`+`description`)로 `POST /reports/v1/targets` 호출.
소개탭 → `targetType:"USER"`, `targetId`=상대 유저 id / 미팅탭 → `targetType:"TEAM"`, `targetId`=팀 id.
