# 배포 가이드

로컬 개발은 `./gradlew bootRun` (H2, 무설정). 아래는 **서버 배포**용이다.

## 구성

```
[인터넷] ──443──► Caddy ──► app:8080 (Spring Boot, prod 프로필)
                              │
                              └──► db:3306 (MariaDB 11)
```

- **Caddy** (`caddy` 프로필) — `${DOMAIN}` 에 대해 Let's Encrypt 인증서를 자동 발급/갱신. HTTP→HTTPS 리다이렉트.
- **app** — 외부에 직접 노출 안 함(`expose` 만). Flyway 가 기동 시 스키마 마이그레이션.
- **db** — 볼륨(`db-data`)에 데이터 영속. compose 밖에서 접근 불가.

## 무료로 배포하기: Oracle Cloud Free + Cloudflare Tunnel

서버는 Oracle Cloud Always Free(무료·상시), 외부 공개는 Cloudflare Tunnel(무료)로 한다.
포트를 열 필요도, 인증서를 관리할 필요도 없다 (Caddy 불필요).

```
[사용자] ─HTTPS─► Cloudflare ◄══Tunnel══ cloudflared ──► app:8080 ──► db:3306
                                          (Oracle 서버 안, 아웃바운드 연결만)
```

### 1. Oracle 서버 만들기
1. https://www.oracle.com/cloud/free/ 가입 (카드 인증 필요, 과금 없음). 홈 리전은 **Chuncheon(춘천)** 또는 Seoul.
2. Compute > Instances > Create instance
   - Image: **Ubuntu 22.04** · Shape: **VM.Standard.A1.Flex (ARM)** 2 OCPU / 12GB (Always Free 한도 내)
   - 용량 부족(Out of host capacity) 이면 OCPU/메모리를 줄이거나 시간을 두고 재시도. 안 되면 `VM.Standard.E2.1.Micro`(1GB)는 메모리가 빠듯하다.
   - SSH 키: 새로 생성 -> private key(.key) 다운로드 (분실하면 접속 불가)
3. 인스턴스 Public IP 확인 후 접속: `ssh -i <키파일> ubuntu@<IP>`
4. Tunnel 은 아웃바운드 연결이라 **인바운드 80/443 을 열 필요 없다.** SSH(22)만 있으면 된다.

### 2. 서버에 Docker 설치
```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER && newgrp docker
docker compose version
```
ARM 서버에서도 `Dockerfile`(temurin)·mariadb·cloudflared 이미지 모두 arm64 를 지원한다.

### 3. Cloudflare Tunnel 만들기 (고정 주소가 필요할 때)
도메인이 없으면 이 단계는 건너뛰고 아래 "임시 주소" 를 쓴다.
1. Cloudflare 가입 -> 도메인을 Cloudflare 에 등록(네임서버 변경)
2. Zero Trust 대시보드 > Networks > **Tunnels** > Create a tunnel > Cloudflared
3. 터널 이름 `chungyak` -> 화면에 나오는 설치 명령 중 **토큰**(`eyJ...`)만 복사 -> `.env` 의 `TUNNEL_TOKEN`
4. **Public Hostname** 추가: 서브도메인/도메인 선택, Service 는 `HTTP` / `app:8080`
   (compose 네트워크 안에서 `app` 으로 접근한다)

### 4. 배포
```bash
git clone <repo> chungyak && cd chungyak
cp .env.example .env
vi .env      # COMPOSE_PROFILES=tunnel, TUNNEL_TOKEN, DB_PASSWORD, DB_ROOT_PASSWORD,
             # PUBLICDATA_SERVICE_KEY, ADMIN_PASSWORD
docker compose up -d --build
docker compose logs -f app cloudflared   # app: Started ChungyakApplication / cloudflared: Registered tunnel connection
curl https://<공개 호스트명>/actuator/health
```

### (대안) 임시 주소 — 계정·도메인 없이
`.env` 에서 `COMPOSE_PROFILES=quick-tunnel` 로 두고 `docker compose up -d --build` 후:
```bash
docker compose logs cloudflared-quick | grep trycloudflare.com
```
`https://xxxx.trycloudflare.com` 주소가 나온다. **컨테이너를 재시작하면 주소가 바뀌므로** 시연·확인용이다.
(sitemap·알림 메일 링크도 이 주소를 따라간다.)

### 무료 서버 운영 메모
- Oracle 은 장기간 유휴(CPU/메모리/네트워크 모두 낮음) 인스턴스를 회수할 수 있다. 앱이 상시 돌면서
  매일 수집하므로 보통 해당 없지만, 헬스체크 모니터(UptimeRobot)를 걸어두면 알 수 있다.
- 백업은 아래 "백업" 절 명령을 cron 으로. 서버 디스크가 유일한 사본이다.

## 사전 준비 (Caddy 로 직접 공개할 때)

1. 서버 (Ubuntu 22.04+ 권장, 최소 1GB RAM — 2GB 권장) + Docker / Docker Compose v2
2. 도메인 등록 후 **A 레코드**를 서버 IP로
3. 서버 방화벽에서 **80, 443** 오픈 (22 는 SSH)
4. 공공데이터포털에서 청약홈 API 활용신청 → `PUBLICDATA_SERVICE_KEY` 발급

## 배포 (Caddy)

```bash
git clone <repo> chungyak && cd chungyak
cp .env.example .env
vi .env          # COMPOSE_PROFILES=caddy, DOMAIN, DB_PASSWORD, DB_ROOT_PASSWORD, PUBLICDATA_SERVICE_KEY,
                 # ADMIN_PASSWORD 최소 5개는 반드시 채운다. (ANTHROPIC_API_KEY 는 선택)

docker compose up -d --build
docker compose logs -f app        # "Started ChungyakApplication" 확인
curl https://$DOMAIN/actuator/health   # {"status":"UP"}  (수집 전이면 DOWN 일 수 있음)
```

### 첫 데이터 수집

```bash
curl -u admin:$ADMIN_PASSWORD -X POST https://$DOMAIN/api/admin/sync
# → {"pagesFetched":29,"received":2861,...}  (약 5분)
```

이후 매일 04시(Asia/Seoul) 자동 수집. `AnnouncementSyncScheduler`.

## 업데이트

```bash
git pull
docker compose up -d --build        # 무중단 아님 (수십 초 다운). Flyway 가 새 마이그레이션 적용
```

## 스키마 마이그레이션 (Flyway)

- 마이그레이션 파일: `src/main/resources/db/migration/V{n}__설명.sql`
- **이미 적용된 파일은 절대 수정하지 않는다** (체크섬 불일치로 기동 실패). 항상 새 `V{n+1}` 추가.
- 로컬/테스트는 H2(MODE=MySQL), 운영은 MariaDB — 양쪽에서 도는 SQL 로 작성.
- 엔티티를 바꾸면: 마이그레이션 추가 → `./gradlew test`(H2 에 적용 + JPA validate) → 커밋.
- 기존 운영 DB(마이그레이션 이력 없음)에 처음 붙일 때는 `docker compose run --rm app \
  java -jar app.jar` 대신 Flyway `baseline` 필요 — 지금은 신규 배포 전제라 해당 없음.

## CI (GitHub Actions)

- `push`/`PR` 마다 `./gradlew build` (LLM 통합테스트는 키 없으면 skip).
- `master` push 시 Docker 이미지 빌드 검증.
- **레지스트리 푸시/자동 배포는 아직 미설정.** 붙이려면:
  1. `.github/workflows/ci.yml` 의 `docker` 잡에서 `push: true` + `docker/login-action`(GHCR)
  2. `tags: ghcr.io/<owner>/chungyak:latest,ghcr.io/<owner>/chungyak:${{ github.sha }}`
  3. 서버에서 `docker compose pull && docker compose up -d` 하는 배포 잡(SSH action 또는
     watchtower) 추가

## 백업

```bash
docker compose exec db sh -c 'mariadb-dump -u root -p"$MARIADB_ROOT_PASSWORD" chungyak' > backup-$(date +%F).sql
```

공고 데이터는 공공 API 에서 다시 수집 가능하므로 치명적이지 않지만, 회원 기능 추가 후에는 정기 백업 필수.

## 모니터링

- `GET /actuator/health/liveness` — 앱 프로세스 생존. Caddy·compose 헬스체크가 이걸 본다.
- `GET /actuator/health` — 전체 상태. **수집이 실패·저조·30시간 이상 오래되면 DOWN(503)** 이 되므로
  uptime 모니터(UptimeRobot 등)를 여기에 걸어두면 데이터가 낡을 때 알림이 온다.
  (상세 `outcome`/`lastSuccessAt` 는 admin 인증 시 노출)
- 임계값은 `application.yml` `publicdata.sync` (min-expected-records, stale-after-hours).
- 그 외 actuator 는 차단. 로그는 `docker compose logs` — `공고 수집 실패` / `공고 수집 이상` ERROR 를
  로그 수집기(Loki/CloudWatch 등)에서 알림 규칙으로 잡을 수 있다.
