# CI/CD 배포 자동화 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** GitHub Actions 수동 트리거로 oneulsogae-backend를 빌드·테스트·GHCR push 후 EC2에 SSM으로 자동 배포한다.

**Architecture:** x86 러너에서 `./gradlew build`(테스트 게이트)로 jar을 만든 뒤, jar만 받는 arm64 런타임 이미지를 GHCR에 push한다. GitHub↔AWS OIDC로 단기 자격증명을 얻어 SSM SendCommand로 EC2에서 `docker compose pull && up -d`를 실행하고, 명령 완료를 폴링해 실패 시 job을 실패시킨다.

**Tech Stack:** GitHub Actions, Docker Buildx, GHCR, AWS IAM OIDC, AWS SSM, Gradle, amazoncorretto:21.

## Global Constraints

- 리전: `ap-northeast-2` (전 과정 고정).
- 이미지: `ghcr.io/devforlove/meeple-backend` (GHCR, Public).
- EC2 대상 경로: `/opt/meeple/docker-compose.prod.yml` (이미 배치됨). 워크플로는 `.env`/compose/Caddyfile을 수정하지 않는다.
- 인증: OIDC만 사용(액세스 키 저장 금지). GHCR push는 `GITHUB_TOKEN`.
- 리포: `devforlove/meeple-backend`. 브랜치: 작업은 `feat/cicd-deploy-automation`.
- 커밋 메시지 형식: `<type>: <설명>` (도메인 전역이면 괄호 생략), 끝에 `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`.

---

### Task 1: Dockerfile 런타임 전용 교체 + .dockerignore 예외

**Files:**
- Modify: `Dockerfile`
- Modify: `.dockerignore`

**Interfaces:**
- Produces: `ghcr.io/devforlove/meeple-backend` 이미지가 `oneulsogae-api/build/libs/*.jar`(부트 jar)을 `/app/app.jar`로 담아 `java -jar app.jar`로 기동. Task 4의 build-push 단계가 이 Dockerfile을 컨텍스트 `.`로 빌드한다.

- [ ] **Step 1: Dockerfile을 런타임 전용으로 교체**

`Dockerfile` 전체를 아래로 대체(빌드 스테이지 제거):

```dockerfile
# 런타임 전용 이미지. jar은 CI 러너(또는 로컬)에서 미리 빌드해 COPY한다.
# (기존의 이미지 내 gradle 빌드 제거 → arm64 빌드 시 QEMU로 gradle을 돌리지 않아 빠르다)
FROM amazoncorretto:21
WORKDIR /app
COPY oneulsogae-api/build/libs/*.jar app.jar
EXPOSE 8080
ENV JAVA_OPTS="-Xms512m -Xmx1024m"
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar app.jar"]
```

- [ ] **Step 2: .dockerignore에 jar 예외 추가**

`.dockerignore`의 `**/build` 줄 바로 아래에 예외 한 줄을 추가한다. 결과 파일:

```
# 빌드 컨텍스트를 가볍게 유지한다. 이미지 안에서 gradle로 새로 빌드하므로 호스트 산출물은 제외한다.
.git
.gradle
.idea
**/build
# CI/로컬이 미리 만든 oneulsogae-api 부트 jar만 예외로 빌드 컨텍스트에 포함한다(런타임 Dockerfile이 COPY).
!oneulsogae-api/build/libs/*.jar
*.iml
# 로컬 개발용 파일 (운영 이미지에 불필요)
docker-compose.yml
docker/
```

- [ ] **Step 3: jar 빌드**

Run: `./gradlew :oneulsogae-api:bootJar`
Expected: `BUILD SUCCESSFUL`, `oneulsogae-api/build/libs/`에 `*.jar` 1개 생성.

확인: `ls oneulsogae-api/build/libs/*.jar` → jar 파일 경로 출력.

- [ ] **Step 4: 로컬 Docker 빌드로 Dockerfile+dockerignore 검증**

Run: `docker build -t oneulsogae-verify .`
Expected: 빌드 성공(`COPY oneulsogae-api/build/libs/*.jar app.jar` 통과). 실패 시 `.dockerignore`가 jar을 제외한 것이므로 Step 2 재확인.

확인: `docker run --rm oneulsogae-verify sh -c 'ls -l /app/app.jar'` → `/app/app.jar` 존재 출력.

- [ ] **Step 5: 커밋**

```bash
git add Dockerfile .dockerignore
git commit -m "build: Dockerfile을 런타임 전용(jar COPY)으로 교체

- 이미지 내 gradle 빌드 제거, 미리 만든 부트 jar만 COPY
- .dockerignore에 oneulsogae-api 부트 jar 예외 추가

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: AWS OIDC 공급자 + IAM 배포 역할 생성 (콘솔, 수동)

> 코드 커밋이 없는 인프라 준비 작업이다. 아래 값은 사용자가 콘솔에서 생성하고, 결과 ARN을 Task 3에서 GitHub 변수로 등록한다. `<ACCOUNT_ID>`, `<EC2_INSTANCE_ID>`는 실제 값으로 치환한다.

**Files:** 없음 (AWS 콘솔)

**Interfaces:**
- Produces: IAM 역할 `oneulsogae-github-deploy-role`의 ARN(`arn:aws:iam::<ACCOUNT_ID>:role/oneulsogae-github-deploy-role`). Task 3의 `AWS_ROLE_ARN` 변수, Task 4의 `role-to-assume`가 소비.

- [ ] **Step 1: 계정 ID·인스턴스 ID 확보**

```bash
aws sts get-caller-identity --query Account --output text            # <ACCOUNT_ID>
aws ec2 describe-instances --region ap-northeast-2 \
  --filters "Name=tag:Name,Values=oneulsogae-api" \
  --query "Reservations[].Instances[].InstanceId" --output text        # <EC2_INSTANCE_ID>
```
(로컬 맥에 aws CLI가 설정돼 있어야 함. 없으면 콘솔 EC2/우상단 계정에서 확인)

- [ ] **Step 2: OIDC 자격 증명 공급자 추가**

IAM 콘솔 → 자격 증명 공급자 → 공급자 추가 → OpenID Connect:
- 공급자 URL: `https://token.actions.githubusercontent.com`
- 대상(Audience): `sts.amazonaws.com`
- 추가

- [ ] **Step 3: IAM 역할 생성 (신뢰 정책)**

IAM → 역할 → 역할 생성 → 사용자 지정 신뢰 정책. 아래 JSON 사용(`<ACCOUNT_ID>` 치환):

```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Principal": { "Federated": "arn:aws:iam::<ACCOUNT_ID>:oidc-provider/token.actions.githubusercontent.com" },
    "Action": "sts:AssumeRoleWithWebIdentity",
    "Condition": {
      "StringEquals": { "token.actions.githubusercontent.com:aud": "sts.amazonaws.com" },
      "StringLike": { "token.actions.githubusercontent.com:sub": "repo:devforlove/meeple-backend:*" }
    }
  }]
}
```

역할 이름: `oneulsogae-github-deploy-role`.

- [ ] **Step 4: 권한 정책 연결 (최소 권한)**

위 역할에 인라인 정책 추가(`<ACCOUNT_ID>`, `<EC2_INSTANCE_ID>` 치환):

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "SendCommand",
      "Effect": "Allow",
      "Action": "ssm:SendCommand",
      "Resource": [
        "arn:aws:ec2:ap-northeast-2:<ACCOUNT_ID>:instance/<EC2_INSTANCE_ID>",
        "arn:aws:ssm:ap-northeast-2::document/AWS-RunShellScript"
      ]
    },
    {
      "Sid": "ReadInvocation",
      "Effect": "Allow",
      "Action": "ssm:GetCommandInvocation",
      "Resource": "*"
    }
  ]
}
```

- [ ] **Step 5: 역할 생성 확인**

Run: `aws iam get-role --role-name oneulsogae-github-deploy-role --query "Role.Arn" --output text`
Expected: `arn:aws:iam::<ACCOUNT_ID>:role/oneulsogae-github-deploy-role` 출력. 이 ARN을 Task 3에서 사용.

---

### Task 3: GitHub 리포 변수 등록 (수동)

**Files:** 없음 (GitHub 설정)

**Interfaces:**
- Consumes: Task 2의 역할 ARN, EC2 인스턴스 ID, 리전.
- Produces: 리포 변수 `AWS_ROLE_ARN`, `AWS_REGION`, `EC2_INSTANCE_ID`. Task 4 워크플로가 `${{ vars.* }}`로 소비.

- [ ] **Step 1: 리포 변수 3개 등록**

GitHub → 리포 `devforlove/meeple-backend` → Settings → Secrets and variables → Actions → **Variables 탭** → New repository variable:

| Name | Value |
|---|---|
| `AWS_ROLE_ARN` | `arn:aws:iam::<ACCOUNT_ID>:role/oneulsogae-github-deploy-role` |
| `AWS_REGION` | `ap-northeast-2` |
| `EC2_INSTANCE_ID` | `<EC2_INSTANCE_ID>` |

> Secrets가 아니라 Variables에 넣는다(비시크릿). GHCR는 Public이라 별도 시크릿 불필요.

- [ ] **Step 2: 등록 확인**

Run: `gh variable list --repo devforlove/meeple-backend`
Expected: `AWS_ROLE_ARN`, `AWS_REGION`, `EC2_INSTANCE_ID` 3개 표시. (gh CLI 없으면 웹 UI에서 3개 존재 확인)

---

### Task 4: deploy.yml 워크플로 작성

**Files:**
- Create: `.github/workflows/deploy.yml`

**Interfaces:**
- Consumes: Task 1의 Dockerfile, Task 3의 리포 변수(`AWS_ROLE_ARN`/`AWS_REGION`/`EC2_INSTANCE_ID`).
- Produces: `workflow_dispatch`로 실행되는 `deploy-backend` 워크플로.

- [ ] **Step 1: 워크플로 파일 작성**

`.github/workflows/deploy.yml` 생성:

```yaml
name: deploy-backend

on:
  workflow_dispatch:

permissions:
  contents: read
  packages: write
  id-token: write

jobs:
  build-and-deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          distribution: corretto
          java-version: '21'

      - name: Grant execute permission for gradlew
        run: chmod +x ./gradlew

      - name: Build & test (Testcontainers E2E 포함)
        run: ./gradlew build

      - name: Log in to GHCR
        uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: Set up QEMU
        uses: docker/setup-qemu-action@v3

      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v3

      - name: Build & push arm64 image
        uses: docker/build-push-action@v6
        with:
          context: .
          platforms: linux/arm64
          push: true
          tags: |
            ghcr.io/devforlove/meeple-backend:latest
            ghcr.io/devforlove/meeple-backend:${{ github.sha }}

      - name: Configure AWS credentials (OIDC)
        uses: aws-actions/configure-aws-credentials@v4
        with:
          role-to-assume: ${{ vars.AWS_ROLE_ARN }}
          aws-region: ${{ vars.AWS_REGION }}

      - name: Deploy via SSM
        run: |
          set -euo pipefail
          CMD_ID=$(aws ssm send-command \
            --document-name "AWS-RunShellScript" \
            --targets "Key=InstanceIds,Values=${{ vars.EC2_INSTANCE_ID }}" \
            --comment "deploy oneulsogae-backend ${{ github.sha }}" \
            --parameters 'commands=["cd /opt/meeple && docker compose -f docker-compose.prod.yml pull && docker compose -f docker-compose.prod.yml up -d"]' \
            --region "${{ vars.AWS_REGION }}" \
            --query "Command.CommandId" --output text)
          echo "SSM Command ID: $CMD_ID"

          # 명령이 등록될 시간을 잠깐 준 뒤 완료까지 대기
          sleep 5
          aws ssm wait command-executed \
            --command-id "$CMD_ID" \
            --instance-id "${{ vars.EC2_INSTANCE_ID }}" \
            --region "${{ vars.AWS_REGION }}" || true

          STATUS=$(aws ssm get-command-invocation \
            --command-id "$CMD_ID" \
            --instance-id "${{ vars.EC2_INSTANCE_ID }}" \
            --region "${{ vars.AWS_REGION }}" \
            --query "Status" --output text)
          echo "== STDOUT =="
          aws ssm get-command-invocation \
            --command-id "$CMD_ID" \
            --instance-id "${{ vars.EC2_INSTANCE_ID }}" \
            --region "${{ vars.AWS_REGION }}" \
            --query "StandardOutputContent" --output text
          echo "== STATUS: $STATUS =="
          if [ "$STATUS" != "Success" ]; then
            echo "== STDERR =="
            aws ssm get-command-invocation \
              --command-id "$CMD_ID" \
              --instance-id "${{ vars.EC2_INSTANCE_ID }}" \
              --region "${{ vars.AWS_REGION }}" \
              --query "StandardErrorContent" --output text
            exit 1
          fi
```

- [ ] **Step 2: 워크플로 YAML 문법 검증**

Run: `python3 -c "import yaml,sys; yaml.safe_load(open('.github/workflows/deploy.yml')); print('YAML OK')"`
Expected: `YAML OK`. (actionlint이 설치돼 있으면 `actionlint .github/workflows/deploy.yml`도 실행)

- [ ] **Step 3: 커밋**

```bash
git add .github/workflows/deploy.yml
git commit -m "ci: GitHub Actions 배포 워크플로 추가(수동 트리거)

- ./gradlew build 테스트 게이트 → arm64 이미지 GHCR push
- OIDC 인증 후 SSM SendCommand로 EC2 배포·완료 폴링

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: 배포 문서 로컬 빌드 절차 갱신

**Files:**
- Modify: `docs/deployment/aws-setup-guide.md`

**Interfaces:** 없음 (문서).

- [ ] **Step 1: 8단계 Dockerfile 블록·로컬 빌드 절차 갱신**

`docs/deployment/aws-setup-guide.md` 8단계의 Dockerfile 예시를 Task 1의 런타임 전용 Dockerfile로 교체하고, 로컬 수동 빌드가 **jar 선행 빌드**를 요구함을 명시한다. 8단계 Dockerfile 코드블록 아래에 다음 문장을 추가:

```markdown
> **런타임 전용 Dockerfile**: 이미지 안에서 gradle을 돌리지 않고 미리 만든 부트 jar을 COPY한다.
> 로컬에서 수동 빌드할 때는 먼저 `./gradlew :oneulsogae-api:bootJar`로 jar을 만든 뒤 `docker build`(또는 `docker buildx build --platform linux/arm64 ... --push .`)를 실행한다.
> 자동 배포는 `.github/workflows/deploy.yml`(수동 트리거)이 담당한다 — 자세한 내용은 10단계 참고.
```

- [ ] **Step 2: 4단계(ElastiCache)에 전송 중 암호화 주의 추가**

4단계 ElastiCache 설정 목록의 "보안" 항목 아래에 다음을 추가(이번 배포에서 겪은 이슈):

```markdown
   - **전송 중 암호화(Encryption in-transit): 사용 안 함** — 앱(Redisson/Lettuce)이 평문(`redis://`)으로만 접속하므로 켜면 PING 타임아웃으로 기동 실패한다.
```

- [ ] **Step 3: 커밋**

```bash
git add docs/deployment/aws-setup-guide.md
git commit -m "docs: 런타임 Dockerfile·로컬 빌드 절차·Redis 암호화 주의 반영

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 6: 워크플로 수동 실행으로 end-to-end 검증

> Task 1~5 완료 + Task 2·3의 AWS/GitHub 설정이 끝난 뒤 수행. 실제 배포를 돌려 파이프라인 전체를 검증한다.

**Files:** 없음 (실행·검증)

**Interfaces:**
- Consumes: Task 1·4의 산출물, Task 2·3의 인프라 설정.

- [ ] **Step 1: 브랜치 push**

```bash
git push -u origin feat/cicd-deploy-automation
```

> `workflow_dispatch`는 워크플로 파일이 있는 브랜치를 지정해 실행할 수 있다. main 병합 전에 이 브랜치로 먼저 검증한다.

- [ ] **Step 2: 워크플로 수동 실행**

```bash
gh workflow run deploy-backend --ref feat/cicd-deploy-automation --repo devforlove/meeple-backend
gh run watch --repo devforlove/meeple-backend
```
(gh CLI 없으면 GitHub Actions 탭 → deploy-backend → Run workflow → 브랜치 `feat/cicd-deploy-automation` 선택)

Expected: 모든 스텝 초록불, 특히 `Build & test` 통과, `Deploy via SSM`의 `STATUS: Success`.

- [ ] **Step 3: 실제 배포 반영 확인**

로컬 맥에서:
```bash
curl -I https://api.meeple.life/
```
Expected: 502가 아닌 정상 응답.

EC2에서 새 이미지가 도는지(선택, SSM 세션):
```bash
docker inspect --format '{{.Config.Image}}' oneulsogae-api-1
docker compose -f /opt/meeple/docker-compose.prod.yml ps
```
Expected: api 컨테이너 `Up`, `ghcr.io/devforlove/meeple-backend:latest` 사용.

- [ ] **Step 4: main 병합 (검증 성공 시)**

```bash
git checkout main
git merge --no-ff feat/cicd-deploy-automation
git push origin main
```
(또는 GitHub에서 PR 생성 후 병합 — 사용자 선호에 따름)

---

## Self-Review

**Spec coverage:**
- OIDC 인증 → Task 2, 3, 4(configure-aws-credentials). ✓
- 러너 jar 빌드 → arm64 런타임 이미지 → Task 1(Dockerfile/.dockerignore), Task 4(build-push). ✓
- 테스트 게이트 → Task 4 `./gradlew build`. ✓
- 수동 트리거 → Task 4 `on: workflow_dispatch`. ✓
- `.dockerignore` 예외 → Task 1 Step 2(검증된 최소형). ✓
- 리포 변수 → Task 3. ✓
- 배포 검증(폴링) → Task 4 Deploy 스텝. ✓
- 이미지 태그 latest+sha → Task 4 build-push tags. ✓
- 로컬 빌드 절차·Redis 암호화 문서화 → Task 5. ✓
- end-to-end 검증 → Task 6. ✓

**Placeholder scan:** `<ACCOUNT_ID>`/`<EC2_INSTANCE_ID>`는 사용자가 자신의 값으로 치환하는 의도된 변수(수동 인프라 태스크). 코드/설정 태스크(1,4,5)에는 미완성 표현 없음.

**Type consistency:** 이미지 이름 `ghcr.io/devforlove/meeple-backend`, 역할명 `oneulsogae-github-deploy-role`, 변수명 `AWS_ROLE_ARN`/`AWS_REGION`/`EC2_INSTANCE_ID`, compose 경로 `/opt/meeple/docker-compose.prod.yml`가 전 태스크에서 일관됨. ✓
