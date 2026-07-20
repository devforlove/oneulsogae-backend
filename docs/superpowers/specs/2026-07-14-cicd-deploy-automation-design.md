# CI/CD 배포 자동화 설계

**작성일**: 2026-07-14
**대상**: `oneulsogae-backend` — GitHub Actions 기반 EC2 자동 배포

## 배경

현재 배포는 전부 수동이다. 로컬에서 `docker buildx`로 arm64 이미지를 빌드해 GHCR에 push하고, EC2에 SSM으로 접속해 `docker compose pull && up -d`를 실행한다. 이 과정을 GitHub Actions로 자동화한다. 기존 인프라(EC2 t4g.small + `oneulsogae-ec2-role`, GHCR public 패키지, EC2의 `/opt/meeple/docker-compose.prod.yml`)는 그대로 재사용한다.

## 결정 사항

| 항목 | 결정 | 근거 |
|---|---|---|
| AWS 인증 | **OIDC (키 없음)** | 장기 자격증명을 GitHub에 두지 않음. "시크릿을 리포에 안 두는" 기존 방향과 일치 |
| 빌드 방식 | **러너에서 jar 빌드 → arm64 런타임 이미지에 COPY** | jar은 아키텍처 독립적. QEMU로 gradle을 에뮬레이션하는 느림/OOM 회피 |
| 테스트 | **테스트 게이트 실행(`./gradlew build`, E2E 포함)** | 깨진 코드의 운영 배포 차단 |
| 트리거 | **수동 실행(`workflow_dispatch`)만** | 배포 시점을 사람이 통제. 수동 전용이라 문서-only 스킵 옵션 불필요 |

## 전체 흐름

```
[Actions 탭에서 수동 실행]
  → checkout (x86 ubuntu 러너)
  → JDK 21 + ./gradlew build          # 테스트 게이트(E2E 포함). 실패 시 중단
  → GHCR 로그인 (GITHUB_TOKEN)
  → jar을 arm64 런타임 이미지에 COPY → GHCR push (:latest + :<git-sha>)
  → AWS OIDC로 단기 자격증명 획득
  → SSM SendCommand → EC2에서 compose pull && up -d
  → 명령 완료 폴링 → 실패면 job 실패 처리
```

## 컴포넌트

### 1. Dockerfile (런타임 전용으로 교체)

빌드 스테이지를 제거하고 미리 만든 jar만 받는다.

```dockerfile
FROM amazoncorretto:21
WORKDIR /app
COPY oneulsogae-api/build/libs/*.jar app.jar
EXPOSE 8080
ENV JAVA_OPTS="-Xms512m -Xmx1024m"
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar app.jar"]
```

- CI는 `./gradlew build`로 jar을 먼저 만든 뒤 이 이미지를 빌드 → arm64라도 COPY만 하므로 빠름.
- **영향**: 로컬 수동 빌드도 `./gradlew :oneulsogae-api:bootJar` 선행 필요. `docs/deployment/aws-setup-guide.md`의 로컬 빌드 절차를 이에 맞게 갱신한다.
- **`.dockerignore` 수정 필요**: 현재 `**/build`가 산출물을 제외해 러너에서 만든 jar이 빌드 컨텍스트에서 빠진다. `!oneulsogae-api/build/libs/*.jar` 예외를 추가하거나 제외 규칙을 조정해 jar이 컨텍스트에 포함되게 한다. (다른 모듈의 `build/`는 계속 제외)

### 2. AWS OIDC 설정 (콘솔에서 1회, 워크플로 실행 전 선행)

- **IAM → 자격 증명 공급자**: OpenID Connect, `token.actions.githubusercontent.com`
- **IAM 역할 `oneulsogae-github-deploy-role`**
  - 신뢰 정책: 위 OIDC + 리포 제한 `repo:devforlove/meeple-backend:*`
  - 권한(최소):
    - `ssm:SendCommand` — 리소스를 대상 EC2 인스턴스 ARN + `AWS-RunShellScript` 문서 ARN으로 제한
    - `ssm:GetCommandInvocation` — 명령 완료/성공 확인용
- 산출물: 역할 ARN

### 3. GitHub 리포 변수 (Variables, 비시크릿)

`Settings → Secrets and variables → Actions → Variables`:

| 변수 | 값 |
|---|---|
| `AWS_ROLE_ARN` | `arn:aws:iam::<계정ID>:role/oneulsogae-github-deploy-role` |
| `AWS_REGION` | `ap-northeast-2` |
| `EC2_INSTANCE_ID` | `i-xxxxxxxx` (oneulsogae-api 인스턴스) |

- GHCR는 Public → EC2 pull에 인증 불필요.
- GHCR push는 `GITHUB_TOKEN`(`packages: write`)으로 처리 → 추가 시크릿 없음.

### 4. 워크플로 `.github/workflows/deploy.yml`

- `on: workflow_dispatch`
- `permissions: { contents: read, packages: write, id-token: write }`
- 단일 job(`ubuntu-latest`):
  1. `actions/checkout@v4`
  2. `actions/setup-java@v4` (corretto 21)
  3. `./gradlew build` (테스트 게이트, Testcontainers E2E — ubuntu-latest에 Docker 존재)
  4. `docker/login-action@v3` (registry `ghcr.io`, `GITHUB_TOKEN`)
  5. `docker/setup-buildx-action` + `docker/build-push-action@v6`: `platforms: linux/arm64`, 태그 `:latest`와 `:<git-sha>` 동시 push
  6. `aws-actions/configure-aws-credentials@v4` (OIDC, `role-to-assume: ${{ vars.AWS_ROLE_ARN }}`)
  7. `aws ssm send-command` → `cd /opt/meeple && docker compose -f docker-compose.prod.yml pull && docker compose -f docker-compose.prod.yml up -d`
  8. `aws ssm get-command-invocation` 폴링 → 상태가 `Success`가 아니면 `exit 1`로 job 실패

### 5. 이미지 태그·롤백

- push 태그: `:latest`(compose가 참조) + `:<git-sha>`(추적/롤백용)
- 롤백 자동화는 범위 밖. 현재는 `:latest` 기준. (후속으로 `workflow_dispatch` 입력으로 특정 sha 배포 옵션 추가 가능)

## 데이터/설정 흐름

- 시크릿(운영 값)은 기존대로 EC2 `/opt/meeple/.env`에 있으며 워크플로는 이를 건드리지 않는다.
- 워크플로가 다루는 것은 **이미지 빌드·push·배포 트리거**뿐. `.env`/`docker-compose.prod.yml`/`Caddyfile`은 EC2에 그대로 유지.

## 에러 처리

- 테스트 실패 → 3단계에서 중단(이미지 빌드·배포 안 함).
- 이미지 push 실패 → job 실패.
- SSM 명령 실패 또는 컨테이너 미기동 → 8단계 폴링에서 감지해 job 실패(빨간불).

## 테스트/검증

- 워크플로 자체는 `workflow_dispatch`로 실제 실행해 end-to-end 검증(빌드 → push → 배포 → 컨테이너 기동)한다.
- 성공 기준: Actions job이 초록불이고, 배포 후 `https://api.meeple.life/`가 정상 응답하며 새 이미지(`:<git-sha>`)가 EC2에서 구동됨.

## 범위 밖 (YAGNI)

- 자동(push) 트리거, 문서-only 스킵, 스테이징 환경, 블루/그린·무중단 배포, 다중 인스턴스, 자동 롤백.

## 선행 조건 (구현 전 사용자 준비)

- AWS 계정 ID, EC2 인스턴스 ID 확보.
- 2번 OIDC 공급자·역할 생성(구현 계획에 절차 포함).
