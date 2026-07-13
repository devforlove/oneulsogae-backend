# AWS 배포 셋업 순서서 (프리티어 · 콘솔)

meeple 서비스 초기 배포용 AWS 콘솔 셋업 가이드. **프리티어 전제**의 최소 비용 구성이며, Terraform/IaC 없이 콘솔에서 순서대로 따라 하도록 작성했다.

- **리전**: 서울 `ap-northeast-2` (전 과정 이 리전 고정)
- **구성**: EC2(API) + RDS MySQL + ElastiCache Redis + S3 + Amplify(프론트) + Route 53
- **CI/CD**: 프론트=Amplify 자동 빌드, 백엔드=GitHub Actions → GHCR → SSM 배포

```
사용자
 ├─ app.<도메인>  → Amplify Hosting (Next.js SSR)
 └─ api.<도메인>  → EC2 t4g.small (퍼블릭 서브넷)
                     ├─ Caddy (HTTPS)
                     └─ meeple-api 컨테이너 (WS·스케줄러 포함)
                    RDS MySQL db.t4g.micro      (프라이빗 서브넷)
                    ElastiCache Redis t4g.micro (프라이빗 서브넷)
                    S3 (이미지 업로드)
```

> ⚠️ **NAT 게이트웨이는 절대 만들지 않는다** (월 ~$32, 이 구성 전체보다 비쌈). RDS·Redis는 인터넷 아웃바운드가 필요 없으므로 프라이빗 서브넷 + 보안 그룹만으로 충분하다.

---

## 0. 사전 준비 (배포 전 필수)

- [ ] **리포에 커밋된 시크릿 로테이션**: `meeple-api/src/main/resources/application.yml`에 Google OAuth client-secret(`GOCSPX-...`)과 KCP enc-key가 기본값으로 하드코딩돼 있다. **각 콘솔에서 재발급**하고, 새 값은 리포에 넣지 않는다(9단계에서 SSM으로 주입).
- [ ] 도메인 준비 (가비아·Route 53 등 어디서 사든 무방. 예시는 `meeple.example` 사용)
- [ ] GitHub 리포 접근 권한 (백엔드·프론트 각각)
- [ ] 카카오·구글 OAuth 콘솔 접근 권한 (Redirect URI 등록 변경용)

---

## 1. 네트워크 (VPC)

기본 VPC를 그대로 써도 되지만, RDS/Redis를 프라이빗으로 분리하려면 서브넷 구성만 확인한다.

1. **VPC 콘솔 → 기본 VPC 사용** (별도 생성 불필요)
2. 서브넷 확인: 기본 VPC에는 AZ별 퍼블릭 서브넷이 이미 있다. RDS·ElastiCache는 서브넷 그룹을 만들 때 **서로 다른 AZ 2개**가 필요하므로, 서울 리전 서브넷이 최소 2개(예: `ap-northeast-2a`, `ap-northeast-2c`)인지 확인.

> 기본 VPC 서브넷은 모두 "퍼블릭"이지만, RDS·Redis에 **퍼블릭 액세스=아니오**로 두고 보안 그룹으로 잠그면 외부에서 접근 불가하다. 프리티어 단계에서는 이 방식이 가장 단순하고 비용 0이다.

### 보안 그룹 3개 미리 생성 (EC2 → RDS/Redis 순으로 참조)

**VPC 콘솔 → 보안 그룹 → 보안 그룹 생성**

1. `meeple-ec2-sg` (EC2용)
   - 인바운드: `80`(HTTP) `0.0.0.0/0`, `443`(HTTPS) `0.0.0.0/0`
   - SSH(22)는 **열지 않는다** (접속은 SSM Session Manager 사용, 10단계)
   - 아웃바운드: 전체 허용(기본값)
2. `meeple-rds-sg` (RDS용)
   - 인바운드: `3306` — 소스에 **`meeple-ec2-sg` 선택** (CIDR 아님, 보안 그룹 참조)
3. `meeple-redis-sg` (Redis용)
   - 인바운드: `6379` — 소스에 **`meeple-ec2-sg` 선택**

---

## 2. S3 버킷 (이미지 업로드)

**S3 콘솔 → 버킷 만들기**

1. 이름: `meeple-prod-uploads` (전역 유일해야 함)
2. 리전: 서울 `ap-northeast-2`
3. **퍼블릭 액세스 차단: 모두 유지(ON)** — 앱은 presigned URL로 접근하므로 공개 불필요
4. 나머지 기본값으로 생성

> 버킷 이름을 9단계 `S3_BUCKET` 값으로 쓴다. 리전은 `ap-northeast-2` (앱 기본값과 일치).

---

## 3. RDS MySQL (db.t4g.micro)

**RDS 콘솔 → 데이터베이스 생성**

1. 생성 방식: **표준 생성**
2. 엔진: **MySQL 8.4** (로컬 docker와 동일 계열)
3. 템플릿: **프리 티어**
4. 설정:
   - DB 인스턴스 식별자: `meeple-prod-db`
   - 마스터 사용자 이름: `meeple`
   - 마스터 암호: 강한 암호 생성 후 **안전한 곳에 기록** (9단계 `DB_PASSWORD`)
5. 인스턴스: `db.t4g.micro` (프리티어 자동 선택)
6. 스토리지: gp3 20GB, **스토리지 자동 조정 비활성화**(비용 폭증 방지)
7. 연결:
   - 컴퓨팅 리소스: EC2에 연결 안 함
   - VPC: 기본 VPC
   - **퍼블릭 액세스: 아니오**
   - VPC 보안 그룹: **기존 항목 선택 → `meeple-rds-sg`** (기본 sg 제거)
   - 가용 영역: 아무거나
8. 추가 구성:
   - 초기 데이터베이스 이름: **`meeple`** (반드시 입력. 안 넣으면 DB가 안 만들어짐)
   - 자동 백업: **활성화, 보존 7일**
   - 파라미터 그룹은 나중에 collation 이슈 시 조정 (아래 주의 참고)
9. 생성 → 엔드포인트(`meeple-prod-db.xxxx.ap-northeast-2.rds.amazonaws.com`) 기록

> **Multi-AZ 끄기**(프리티어·비용 2배 방지). **collation 주의**: 앱은 `connectionCollation=utf8mb4_unicode_ci`를 JDBC URL에 명시하므로(9단계) 서버 기본 collation과 무관하게 동작한다. 별도 파라미터 그룹 수정 없이 시작해도 된다.

---

## 4. ElastiCache Redis (cache.t4g.micro)

**ElastiCache 콘솔 → Redis OSS 캐시 생성**

1. 배포: **자체 설계 (Design your own cache)**
2. 클러스터 모드: **비활성화**
3. 설정:
   - 이름: `meeple-prod-redis`
   - 노드 유형: `cache.t4g.micro` (프리티어)
   - 복제본 수: **0** (프리티어·초기)
4. 서브넷 그룹: 새로 생성, 기본 VPC의 서브넷 2개 선택
5. 보안: VPC 보안 그룹 **`meeple-redis-sg`** 선택
6. 백업: 초기엔 비활성화 (세션 캐시 용도라 유실 허용)
7. 생성 → 기본 엔드포인트(`meeple-prod-redis.xxxx.cache.amazonaws.com:6379`) 기록

> 앱에서 Redis는 단일 활성 세션 검사(fail-open) 용도라 가용성 요구가 낮다. 프리티어 종료 후 비용($12/월)이 아까우면 EC2 내 도커 Redis로 되돌리고 `REDIS_HOST`만 바꾸면 된다.

---

## 5. IAM 역할 (EC2용)

EC2가 S3·SSM·GHCR 로그인 시크릿에 키 없이 접근하도록 인스턴스 역할을 만든다.

**IAM 콘솔 → 역할 → 역할 생성**

1. 신뢰 엔터티: **AWS 서비스 → EC2**
2. 권한 정책 연결:
   - `AmazonSSMManagedInstanceCore` (SSM Session Manager·Run Command 배포용)
   - `AmazonS3FullAccess` — 또는 아래 최소 권한 커스텀 정책(권장)
   - `AmazonSSMReadOnlyAccess` (Parameter Store 시크릿 읽기용)
3. 역할 이름: `meeple-ec2-role` → 생성

<details>
<summary>S3 최소 권한 커스텀 정책 (권장)</summary>

```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Action": ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"],
    "Resource": "arn:aws:s3:::meeple-prod-uploads/*"
  }, {
    "Effect": "Allow",
    "Action": ["s3:ListBucket"],
    "Resource": "arn:aws:s3:::meeple-prod-uploads"
  }]
}
```
</details>

---

## 6. SSM Parameter Store (운영 시크릿)

리포에 시크릿을 넣지 않기 위해 운영 값을 여기에 저장한다. **표준 파라미터는 무료.**

**Systems Manager 콘솔 → 파라미터 스토어 → 파라미터 생성**

아래를 각각 **SecureString** 타입으로 생성 (이름 규칙 `/meeple/prod/...`):

| 파라미터 이름 | 값 |
|---|---|
| `/meeple/prod/DB_PASSWORD` | 3단계에서 만든 RDS 마스터 암호 |
| `/meeple/prod/JWT_SECRET` | 64바이트 이상 랜덤 문자열 (`openssl rand -base64 64`) |
| `/meeple/prod/KAKAO_CLIENT_ID` | 카카오 REST API 키 |
| `/meeple/prod/KAKAO_CLIENT_SECRET` | 카카오 client secret(사용 시) |
| `/meeple/prod/GOOGLE_CLIENT_ID` | **재발급한** 구글 OAuth client id |
| `/meeple/prod/GOOGLE_CLIENT_SECRET` | **재발급한** 구글 client secret |
| `/meeple/prod/KCP_ENC_KEY` | **재발급한** KCP enc-key |
| `/meeple/prod/KCP_SITE_CD` | KCP 사이트 코드(운영) |

> 나머지 비-시크릿 값(도메인·쿠키·CORS)은 시크릿이 아니므로 9단계 `.env` 파일에 평문으로 둔다.

---

## 7. EC2 인스턴스 (t4g.small · ARM)

**EC2 콘솔 → 인스턴스 시작**

1. 이름: `meeple-api`
2. AMI: **Amazon Linux 2023 (ARM 64-bit)** — 반드시 arm64
3. 인스턴스 유형: **t4g.small** (프리티어 대상 확인)
4. 키 페어: **키 페어 없이 계속** (SSM으로 접속하므로 SSH 키 불필요)
5. 네트워크 설정 → 편집:
   - VPC: 기본 VPC / 퍼블릭 서브넷
   - 퍼블릭 IP 자동 할당: **활성화**
   - 방화벽: **기존 보안 그룹 선택 → `meeple-ec2-sg`**
6. 스토리지: gp3 30GB
7. 고급 세부 정보 → **IAM 인스턴스 프로파일: `meeple-ec2-role`**
8. 시작

### 인스턴스 초기 세팅 (SSM Session Manager로 접속)

EC2 콘솔 → 인스턴스 선택 → **연결 → 세션 매니저 → 연결** (SSH 불필요)

```bash
# 도커 설치
sudo dnf install -y docker
sudo systemctl enable --now docker
sudo usermod -aG docker ssm-user

# docker compose v2 플러그인
sudo mkdir -p /usr/local/lib/docker/cli-plugins
sudo curl -SL https://github.com/docker/compose/releases/latest/download/docker-compose-linux-aarch64 \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-compose

# 스왑 2GB (2GB 램 보완)
sudo dd if=/dev/zero of=/swapfile bs=1M count=2048
sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab

# 배포 디렉터리
sudo mkdir -p /opt/meeple && sudo chown ssm-user /opt/meeple
```

---

## 8. 백엔드 컨테이너화 (리포에 추가할 파일)

아래 파일들을 **백엔드 리포에 커밋**한다. (Dockerfile이 아직 없으므로 신규 작성)

### `Dockerfile` (리포 루트)

```dockerfile
# 빌드 스테이지
FROM amazoncorretto:21 AS build
WORKDIR /app
COPY . .
RUN ./gradlew :meeple-api:bootJar --no-daemon

# 런타임 스테이지
FROM amazoncorretto:21
WORKDIR /app
COPY --from=build /app/meeple-api/build/libs/*.jar app.jar
EXPOSE 8080
ENV JAVA_OPTS="-Xms512m -Xmx1024m"
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar app.jar"]
```

### `/opt/meeple/docker-compose.prod.yml` (EC2에 배치)

```yaml
services:
  api:
    image: ghcr.io/<github-계정>/meeple-backend:latest
    restart: always
    env_file: /opt/meeple/.env
    ports:
      - "8080:8080"

  caddy:
    image: caddy:2
    restart: always
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - /opt/meeple/Caddyfile:/etc/caddy/Caddyfile
      - caddy_data:/data
    depends_on:
      - api

volumes:
  caddy_data:
```

### `/opt/meeple/Caddyfile` (EC2에 배치 — HTTPS 자동)

```
api.meeple.example {
    reverse_proxy api:8080
}
```

> Caddy가 Let's Encrypt 인증서를 자동 발급·갱신한다(무료). WebSocket(SockJS/STOMP)도 `reverse_proxy`가 자동 통과시킨다.

---

## 9. EC2에 운영 환경변수 배치

SSM 세션에서 `/opt/meeple/.env`를 생성한다. **시크릿은 SSM Parameter Store에서 읽어 채운다.**

```bash
# SSM에서 시크릿 읽기 (예시)
DB_PASSWORD=$(aws ssm get-parameter --name /meeple/prod/DB_PASSWORD --with-decryption --query Parameter.Value --output text --region ap-northeast-2)
# ... 나머지 시크릿도 동일하게 읽어서 아래 파일에 반영
```

`.env` 내용 (도메인·엔드포인트는 실제 값으로 치환):

```bash
# --- 프로파일 ---
SPRING_PROFILES_ACTIVE=local          # ※ 아래 주의 참고

# --- DB (RDS) ---
DB_USERNAME=meeple
DB_PASSWORD=<SSM에서 읽은 값>
# 앱은 application-local.yml의 localhost URL을 쓰므로, 운영 URL을 환경변수로 오버라이드해야 한다(주의 참고)

# --- Redis (ElastiCache) ---
REDIS_HOST=meeple-prod-redis.xxxx.cache.amazonaws.com
REDIS_PORT=6379

# --- JWT ---
JWT_SECRET=<SSM에서 읽은 값>

# --- OAuth ---
KAKAO_CLIENT_ID=<SSM>
KAKAO_CLIENT_SECRET=<SSM>
GOOGLE_CLIENT_ID=<SSM·재발급값>
GOOGLE_CLIENT_SECRET=<SSM·재발급값>

# --- S3 (IAM 역할 사용 → 키 불필요) ---
S3_BUCKET=meeple-prod-uploads
S3_REGION=ap-northeast-2

# --- 쿠키·CORS·리다이렉트 (운영값) ---
AUTH_COOKIE_DOMAIN=.meeple.example
AUTH_COOKIE_SECURE=true
AUTH_COOKIE_SAME_SITE=Lax
CORS_ALLOWED_ORIGINS=https://app.meeple.example
OAUTH2_REDIRECT_URI=https://app.meeple.example/oauth/redirect
OAUTH2_FAILURE_REDIRECT_URI=https://app.meeple.example

# --- KCP (운영·재발급값) ---
KCP_SITE_CD=<SSM>
KCP_ENC_KEY=<SSM·재발급값>
KCP_BASE_URL=https://cert.kcp.co.kr
KCP_RET_URL=https://app.meeple.example/api/onboarding/identity/callback
```

> ### ⚠️ 프로파일·DB URL 관련 중요 주의
>
> 현재 리포에는 **운영(prod) 프로파일이 없다.** DB 접속 정보(`spring.datasource.url`)는 `meeple-infra`의 **`application-local.yml`에만** 있고 `localhost:3306`으로 하드코딩돼 있다. 그대로 배포하면 RDS에 붙지 못한다. 배포 전 **둘 중 하나**를 선택해야 한다:
>
> **(A) 운영 프로파일 추가 (권장)** — `application-prod.yml`을 만들어 `SPRING_DATASOURCE_URL` 등을 환경변수로 받게 하고 `SPRING_PROFILES_ACTIVE=prod`로 구동. `ddl-auto`도 운영에선 `validate`나 `none`으로 두는 것을 검토(현재 local은 `update`).
>
> **(B) 환경변수 오버라이드** — local 프로파일을 유지하되 `.env`에 `SPRING_DATASOURCE_URL=jdbc:mysql://<RDS엔드포인트>:3306/meeple?...` 를 추가해 URL만 덮어쓴다. (Spring은 `SPRING_DATASOURCE_URL` 환경변수로 `spring.datasource.url`을 오버라이드한다)
>
> 이 작업은 코드 변경이므로 배포 착수 시 별도로 진행한다. **(A)를 권장**한다 — local 프로파일은 SQL 디버그 로깅·`ddl-auto: update`가 켜져 있어 운영 부적합.

---

## 10. 백엔드 CI/CD (GitHub Actions → GHCR → SSM 배포)

### GHCR 준비
- GitHub → Packages는 Actions의 `GITHUB_TOKEN`으로 자동 푸시 가능(추가 시크릿 불필요).
- EC2가 GHCR에서 pull하려면 **GHCR read 권한 PAT**가 필요(프라이빗 패키지인 경우). Public 패키지로 두면 EC2에서 로그인 없이 pull 가능 → 초기엔 이 방식이 간단.

### GitHub → AWS OIDC 연동 (키 없는 배포)
1. IAM → 자격 증명 공급자 → **OpenID Connect 추가**: `token.actions.githubusercontent.com`
2. IAM 역할 `meeple-github-deploy-role` 생성 (신뢰: 위 OIDC, 특정 리포로 제한)
3. 권한: `ssm:SendCommand` (대상 EC2 인스턴스로 제한)

### `.github/workflows/deploy.yml` (백엔드 리포)

```yaml
name: deploy-backend
on:
  push:
    branches: [main]

permissions:
  contents: read
  packages: write
  id-token: write   # OIDC

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

      - name: Build & test
        run: ./gradlew build   # Testcontainers E2E 포함

      - name: Log in to GHCR
        uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: Build & push (arm64)
        uses: docker/build-push-action@v6
        with:
          context: .
          platforms: linux/arm64
          push: true
          tags: ghcr.io/${{ github.repository_owner }}/meeple-backend:latest

      - name: Configure AWS credentials (OIDC)
        uses: aws-actions/configure-aws-credentials@v4
        with:
          role-to-assume: arn:aws:iam::<계정ID>:role/meeple-github-deploy-role
          aws-region: ap-northeast-2

      - name: Deploy via SSM
        run: |
          aws ssm send-command \
            --document-name "AWS-RunShellScript" \
            --targets "Key=InstanceIds,Values=<EC2 인스턴스 ID>" \
            --parameters 'commands=["cd /opt/meeple && docker compose -f docker-compose.prod.yml pull && docker compose -f docker-compose.prod.yml up -d"]' \
            --region ap-northeast-2
```

> `docker/build-push-action`의 arm64 크로스 빌드는 QEMU 에뮬레이션이라 다소 느리다. 빌드가 답답하면 `runs-on`을 ARM 러너로 바꾸거나 EC2에서 직접 빌드하는 방식으로 전환할 수 있다. 초기엔 이대로 충분.

---

## 11. 프론트엔드 배포 (Amplify Hosting)

**Amplify 콘솤 → 새 앱 → GitHub 리포 연결**

1. 프론트 리포·`main` 브랜치 선택
2. 빌드 설정: Next.js 자동 감지 (SSR 지원). 별도 `amplify.yml` 불필요(필요 시 자동 생성).
3. **환경변수** (Amplify 콘솔 → 호스팅 → 환경변수):
   | 변수 | 값 |
   |---|---|
   | `NEXT_PUBLIC_API_BASE_URL` | `https://api.meeple.example` |
   | `NEXT_PUBLIC_KAKAO_JS_KEY` | 카카오 JS 키(운영 도메인 등록 필요) |
4. 배포 → `main` 푸시 시 자동 빌드·배포된다.

> 이후 `main`에 푸시하면 프론트는 Amplify가, 백엔드는 GitHub Actions가 각각 자동 배포한다.

---

## 12. 도메인 · DNS (Route 53)

**Route 53 콘솔 → 호스팅 영역 생성** (`meeple.example`)

1. 도메인 등록기관(가비아 등)에서 네임서버를 Route 53 NS 레코드로 변경
2. 레코드 생성:
   - `api.meeple.example` → **A 레코드** → EC2 퍼블릭 IP (또는 탄력적 IP 권장)
   - `app.meeple.example` → Amplify 도메인 연결 (Amplify 콘솔 → 도메인 관리에서 자동으로 CNAME/ALIAS 생성)
3. **탄력적 IP 할당 권장**: EC2 재시작 시 퍼블릭 IP가 바뀌므로, 탄력적 IP를 인스턴스에 연결해 고정. (연결된 상태면 무료, 미연결 시 과금)

> 프리티어 종료 후 퍼블릭 IPv4는 시간당 과금(~$3.6/월)된다. 탄력적 IP도 인스턴스에 **연결돼 있을 때만** 무료다.

---

## 13. OAuth 콜백 URL 등록 (잊기 쉬움)

로그인이 동작하려면 각 콘솔에 운영 Redirect URI를 등록해야 한다.

- **카카오 개발자 콘솔**: Redirect URI에 `https://api.meeple.example/login/oauth2/code/kakao` 추가, 플랫폼 도메인에 `https://app.meeple.example` 추가
- **구글 클라우드 콘솔**: 승인된 리디렉션 URI에 `https://api.meeple.example/login/oauth2/code/google` 추가
- **카카오 JS 키**: 웹 플랫폼 도메인에 `https://app.meeple.example` 등록(카톡 공유용)

---

## 14. 배포 후 점검 체크리스트

- [ ] `https://api.meeple.example/swagger-ui` 가 **안 열리는지**(운영 프로파일은 springdoc off) 확인
- [ ] 헬스체크: API가 8080에서 뜨고 Caddy가 443으로 프록시하는지
- [ ] 프론트에서 로그인 → 쿠키가 `.meeple.example` 도메인·Secure로 세팅되는지
- [ ] 이미지 업로드 → S3 버킷에 객체 생성되는지 (IAM 역할 동작 확인)
- [ ] Redis 연결 → 로그인 세션 검사 정상
- [ ] 스케줄러 배치 로그 확인 (매칭·만료 cron)
- [ ] **Billing 콘솔 → 크레딧 잔액**: 하루 소진 추이 확인 (프리티어 미포함 항목 조기 발견)

---

## 비용 요약

| 항목 | 프리티어 중 | 종료 후(약) |
|---|---|---|
| EC2 t4g.small | 무료 | $15 |
| RDS db.t4g.micro Single-AZ | 무료 | $22 |
| ElastiCache t4g.micro | 무료 | $12 |
| 퍼블릭 IPv4 (탄력적 IP) | 무료 | $3.6 |
| Amplify Hosting | 무료 | $0~5 |
| S3 / EBS / Route 53 | 무료~$0.5 | $3~5 |
| **합계** | **~$1/월** | **~$55~60/월** |

**프리티어 종료 후 절감 경로**: ① ElastiCache → EC2 도커 Redis(-$12) ② 트래픽 적으면 RDS → EC2 도커 MySQL(-$22, 단 백업 자동화 필수) → ~$25/월 구성으로 착지.
